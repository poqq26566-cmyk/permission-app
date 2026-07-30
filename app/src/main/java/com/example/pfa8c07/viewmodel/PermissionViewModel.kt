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

class PermissionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PermissionRepository(application)
    private val _uiState = MutableStateFlow(AppListUiState())
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val apps = repository.getInstalledApps(includeSystem = true)
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
