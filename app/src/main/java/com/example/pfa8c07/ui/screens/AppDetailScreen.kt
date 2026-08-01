package com.example.pfa8c07.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pfa8c07.model.AppInfo
import com.example.pfa8c07.model.AppPermission
import com.example.pfa8c07.model.PermissionGroups

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    appInfo: AppInfo,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val groupedPermissions = appInfo.permissions
        .groupBy { it.group }
        .toSortedMap(compareBy {
            val order = listOf(
                PermissionGroups.LOCATION,
                PermissionGroups.CAMERA,
                PermissionGroups.MICROPHONE,
                PermissionGroups.STORAGE,
                PermissionGroups.CONTACTS,
                PermissionGroups.PHONE,
                PermissionGroups.SMS,
                PermissionGroups.CALENDAR,
                PermissionGroups.SENSORS,
                PermissionGroups.ACTIVITY_RECOGNITION,
                PermissionGroups.NEARBY_DEVICES,
                PermissionGroups.NOTIFICATIONS,
                PermissionGroups.MEDIA_LOCATION,
            )
            order.indexOf(it).let { idx -> if (idx < 0) Int.MAX_VALUE else idx }
        })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appInfo.appName, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        @Suppress("DEPRECATION")
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${appInfo.packageName}")
                        }
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "系统设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // 应用信息头
            item {
                AppInfoHeader(appInfo = appInfo)
            }

            // 权限统计卡片
            item {
                PermissionStatsCard(appInfo = appInfo)
            }

            // 权限概览标题
            item {
                Text(
                    "权限列表",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 空权限
            if (appInfo.permissions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.Shield,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "该应用未声明任何权限",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 按分组显示权限
            groupedPermissions.forEach { (group, permissions) ->
                item {
                    PermissionGroupHeader(group = group, count = permissions.size)
                }
                items(permissions, key = { it.name }) { permission ->
                    PermissionItem(
                        permission = permission,
                        packageName = appInfo.packageName
                    )
                }
                // 分组间隔
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // 底部按钮：打开系统设置
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${appInfo.packageName}")
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Outlined.Security,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("在系统设置中管理权限", fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AppInfoHeader(appInfo: AppInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 大图标
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val context = LocalContext.current
            var icon by remember(appInfo.packageName) {
                mutableStateOf(com.example.pfa8c07.util.IconCache.getCached(appInfo.packageName))
            }
            LaunchedEffect(appInfo.packageName) {
                if (icon == null) {
                    icon = com.example.pfa8c07.util.IconCache.load(context, appInfo.packageName)
                }
            }
            val bitmap = com.example.pfa8c07.util.drawableToImageBitmap(icon)
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap,
                    contentDescription = appInfo.appName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                FallbackIcon()
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = appInfo.appName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = appInfo.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (appInfo.isSystemApp) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("系统应用", fontSize = 10.sp) },
                        modifier = Modifier.height(22.dp),
                        shape = RoundedCornerShape(6.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = "v${appInfo.versionName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun FallbackIcon() {
    Icon(
        Icons.Outlined.Android,
        contentDescription = null,
        modifier = Modifier.size(32.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PermissionStatsCard(appInfo: AppInfo) {
    val grantedPercent = if (appInfo.totalCount > 0) {
        (appInfo.grantedCount.toFloat() / appInfo.totalCount * 100).toInt()
    } else 0

    val totalDangerous = appInfo.permissions.count { it.isDangerous }
    val grantedDangerous = appInfo.permissions.count { it.isDangerous && it.isGranted }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "权限概览",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    value = "${appInfo.grantedCount}",
                    label = "已授权",
                    color = MaterialTheme.colorScheme.primary
                )
                StatItem(
                    value = "${appInfo.totalCount - appInfo.grantedCount}",
                    label = "未授权",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatItem(
                    value = "$grantedPercent%",
                    label = "授权率",
                    color = if (grantedPercent >= 50) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
                if (totalDangerous > 0) {
                    StatItem(
                        value = "$grantedDangerous/$totalDangerous",
                        label = "敏感权限",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // 进度条
            LinearProgressIndicator(
                progress = {
                    if (appInfo.totalCount > 0) appInfo.grantedCount.toFloat() / appInfo.totalCount
                    else 0f
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color,
            fontSize = 18.sp
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun PermissionGroupHeader(group: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val groupIcon = getGroupIcon(group)
        Icon(
            imageVector = groupIcon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = group,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun PermissionItem(
    permission: AppPermission,
    packageName: String
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 3.dp)
            .clickable {
                // 点击跳转到系统设置
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                context.startActivity(intent)
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态图标
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (permission.isGranted) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (permission.isGranted) Icons.Filled.Check
                    else Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (permission.isGranted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 权限信息
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = permission.label.ifEmpty { permission.name.substringAfterLast(".") },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (permission.isDangerous) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                "敏感",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                if (permission.description.isNotEmpty()) {
                    Text(
                        text = permission.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 状态标签
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (permission.isGranted)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = if (permission.isGranted) "已授权" else "关闭",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (permission.isGranted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun getGroupIcon(group: String) = when (group) {
    PermissionGroups.LOCATION -> Icons.Outlined.LocationOn
    PermissionGroups.CAMERA -> Icons.Outlined.CameraAlt
    PermissionGroups.MICROPHONE -> Icons.Outlined.Mic
    PermissionGroups.STORAGE -> Icons.Outlined.Folder
    PermissionGroups.PHONE -> Icons.Outlined.Phone
    PermissionGroups.CONTACTS -> Icons.Outlined.Contacts
    PermissionGroups.SMS -> Icons.Outlined.Message
    PermissionGroups.CALENDAR -> Icons.Outlined.CalendarMonth
    PermissionGroups.SENSORS -> Icons.Outlined.Sensors
    PermissionGroups.ACTIVITY_RECOGNITION -> Icons.Outlined.DirectionsWalk
    PermissionGroups.NEARBY_DEVICES -> Icons.Outlined.Bluetooth
    PermissionGroups.NOTIFICATIONS -> Icons.Outlined.Notifications
    PermissionGroups.MEDIA_LOCATION -> Icons.Outlined.MediaBluetoothOn // fallback
    else -> Icons.Outlined.Security
}
