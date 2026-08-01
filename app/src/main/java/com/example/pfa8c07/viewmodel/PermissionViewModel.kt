package com.example.pfa8c07.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pfa8c07.data.PermissionRepository
import com.example.pfa8c07.data.local.AppDatabase
import com.example.pfa8c07.data.local.toDomain
import com.example.pfa8c07.data.local.toEntity
import com.example.pfa8c07.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppListUiState(
    val apps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val showSystemApps: Boolean = false,
    val permissionFilter: PermissionFilter = PermissionFilter.ALL,
    /** 只有真正首次启动（本地无缓存）才会有意义地滚动，用于展示"正在设置您的应用..."进度 */
    val syncCurrent: Int = 0,
    val syncTotal: Int = 0
)

enum class PermissionFilter {
    ALL, DANGEROUS_ONLY, HAS_GRANTED
}

class PermissionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PermissionRepository(application)
    private val db = AppDatabase.getInstance(application)

    private val _uiState = MutableStateFlow(AppListUiState())
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    init {
        loadApps()
    }

    /**
     * 冷启动（含进程被杀重启）先从 Room 缓存秒出上次的扫描结果，
     * 再在后台用 PackageManager 增量刷新、写回数据库。
     * 只有数据库里完全没有数据（真·首次启动）才会显示加载圈。
     */
    fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val cachedEntities = db.appDao().getAll()
            val cachedApps = cachedEntities.map { it.toDomain() }
            val hasCache = cachedApps.isNotEmpty()

            if (hasCache) {
                _uiState.value = _uiState.value.copy(apps = cachedApps, isLoading = false)
                applyFilters()
            } else {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }

            val cachedMap = cachedApps.associateBy { it.packageName }
            val apps = repository.getInstalledApps(
                includeSystem = true,
                cachedApps = cachedMap,
                onProgress = if (!hasCache) { current, total ->
                    _uiState.value = _uiState.value.copy(syncCurrent = current, syncTotal = total)
                } else null
            )

            // 后台扫描这段时间里，如果用户已经点开过某个应用、精确刷新过它的完整权限详情
            // （isRuntimeVerified=true），这里不能再用扫描重新生成的（可能只有汇总数字、
            // 权限列表是空的）旧缓存对象把它覆盖掉，否则详情页会突然"变回"只有数字没有列表。
            val verifiedNow = _uiState.value.apps
                .filter { it.isRuntimeVerified }
                .associateBy { it.packageName }
            val merged = apps.map { verifiedNow[it.packageName] ?: it }

            db.appDao().upsertAll(merged.map { it.toEntity() })
            db.appDao().deleteMissing(merged.map { it.packageName })

            _uiState.value = _uiState.value.copy(apps = merged, isLoading = false)
            applyFilters()
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyFilters()
    }

    fun toggleSystemApps() {
        _uiState.value = _uiState.value.copy(showSystemApps = !_uiState.value.showSystemApps)
        applyFilters()
    }

    fun setPermissionFilter(filter: PermissionFilter) {
        _uiState.value = _uiState.value.copy(permissionFilter = filter)
        applyFilters()
    }

    fun getAppDetail(packageName: String): AppInfo? {
        return _uiState.value.apps.find { it.packageName == packageName }
    }

    /** 详情页打开时才做一次精确的 checkPermission 校验，不影响列表扫描/缓存读取速度 */
    fun refreshAppDetail(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val detail = repository.getAppPermissionDetail(packageName) ?: return@launch
            val state = _uiState.value
            val updatedApps = state.apps.map { if (it.packageName == packageName) detail else it }
            db.appDao().upsert(detail.toEntity())
            _uiState.value = state.copy(apps = updatedApps)
            applyFilters()
        }
    }

    private fun applyFilters() {
        val state = _uiState.value
        var result = state.apps

        // 过滤系统应用
        if (!state.showSystemApps) {
            result = result.filter { !it.isSystemApp }
        }

        // 搜索过滤
        if (state.searchQuery.isNotBlank()) {
            val query = state.searchQuery.trim().lowercase()
            result = result.filter {
                it.appName.lowercase().contains(query) ||
                        it.packageName.lowercase().contains(query)
            }
        }

        // 权限过滤
        when (state.permissionFilter) {
            PermissionFilter.DANGEROUS_ONLY -> {
                result = result.filter { it.hasDangerousPermission }
            }
            PermissionFilter.HAS_GRANTED -> {
                result = result.filter { it.grantedCount > 0 }
            }
            PermissionFilter.ALL -> { /* no filter */ }
        }

        _uiState.value = state.copy(filteredApps = result)
    }
}
