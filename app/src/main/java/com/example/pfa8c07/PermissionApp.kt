package com.example.pfa8c07

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pfa8c07.ui.screens.AppDetailScreen
import com.example.pfa8c07.ui.screens.AppListScreen
import com.example.pfa8c07.viewmodel.PermissionViewModel

@Composable
fun PermissionApp() {
    val viewModel: PermissionViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    var selectedPackage by remember { mutableStateOf<String?>(null) }
    val selectedApp = selectedPackage?.let { viewModel.getAppDetail(it) }

    // 详情页打开时，系统返回键/手势应该先关掉详情页回到列表，
    // 而不是直接把整个 Activity 关掉退到桌面（因为页面切换是自己用一个状态
    // 变量做的，不是系统返回栈，不拦截的话系统压根不知道"现在在详情页"）
    BackHandler(enabled = selectedPackage != null) {
        selectedPackage = null
    }

    AnimatedContent(
        targetState = selectedPackage,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            if (targetState == null) {
                // 从详情返回列表
                (slideInHorizontally { -it / 4 } + fadeIn())
                    .togetherWith(slideOutHorizontally { it / 4 } + fadeOut())
            } else {
                // 从列表进入详情
                (slideInHorizontally { it / 4 } + fadeIn())
                    .togetherWith(slideOutHorizontally { -it / 4 } + fadeOut())
            }
        },
        label = "screen_transition"
    ) { targetPkg ->
        if (targetPkg == null) {
            // 应用列表页
            AppListScreen(
                uiState = uiState,
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                onToggleSystemApps = { viewModel.toggleSystemApps() },
                onSetPermissionFilter = { viewModel.setPermissionFilter(it) },
                onAppClick = { pkg ->
                    selectedPackage = pkg
                    viewModel.refreshAppDetail(pkg)
                },
                onRefresh = { viewModel.loadApps() }
            )
        } else {
            // 应用权限详情页
            if (selectedApp != null) {
                AppDetailScreen(
                    appInfo = selectedApp,
                    onBack = { selectedPackage = null }
                )
            } else {
                // 数据未加载完成时显示加载
                AppListScreen(
                    uiState = uiState,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onToggleSystemApps = { viewModel.toggleSystemApps() },
                    onSetPermissionFilter = { viewModel.setPermissionFilter(it) },
                    onAppClick = { pkg -> selectedPackage = pkg },
                    onRefresh = { viewModel.loadApps() }
                )
            }
        }
    }
}
