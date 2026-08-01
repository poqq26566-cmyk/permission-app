package com.example.pfa8c07.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pfa8c07.data.PermissionRepository
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
    val permissionFilter: PermissionFilter = PermissionFilter.ALL
)

enum class PermissionFilter {
    ALL, DANGEROUS_ONLY, HAS_GRANTED
}

/** 进程内静态缓存：ViewModel 重建（比如详情页返回）时先用它秒开，不用等重新扫描 */
private object AppListCache {
    @Volatile var apps: List<AppInfo> = emptyList()
}

class PermissionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PermissionRepository(application)
    private val _uiState = MutableStateFlow(
        AppListUiState(
            apps = AppListCache.apps,
            isLoading = AppListCache.apps.isEmpty()
        )
    )
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    init {
        applyFilters() // 先用缓存把上次的结果显示出来
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val hasCache = AppListCache.apps.isNotEmpty()
            // 有缓存时不显示加载圈，静默在后台增量刷新；没缓存才是真正的首次冷启动
            if (!hasCache) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }
            val cachedMap = AppListCache.apps.associateBy { it.packageName }
            val apps = repository.getInstalledApps(includeSystem = true, cachedApps = cachedMap)
            AppListCache.apps = apps
            _uiState.value = _uiState.value.copy(
                apps = apps,
                isLoading = false
            )
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

    /** 详情页打开时才做一次精确的 checkPermission 校验，不影响列表扫描速度 */
    fun refreshAppDetail(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val detail = repository.getAppPermissionDetail(packageName) ?: return@launch
            val state = _uiState.value
            val updatedApps = state.apps.map { if (it.packageName == packageName) detail else it }
            AppListCache.apps = updatedApps
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
                result = result.filter { app ->
                    app.permissions.any { it.isDangerous }
                }
            }
            PermissionFilter.HAS_GRANTED -> {
                result = result.filter { it.grantedCount > 0 }
            }
            PermissionFilter.ALL -> { /* no filter */ }
        }

        _uiState.value = state.copy(filteredApps = result)
    }
}
