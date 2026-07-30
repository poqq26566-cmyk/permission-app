package com.example.pfa8c07

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
                onAppClick = { pkg -> selectedPackage = pkg },
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
