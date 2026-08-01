package com.example.pfa8c07.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.pfa8c07.model.AppInfo

/**
 * 持久化的应用扫描结果缓存表。
 *
 * 不存图标（Drawable 没法直接存 SQLite，也没必要——图标由 IconCache 按需异步加载）。
 * 也不存完整权限列表，列表页/筛选只需要 grantedCount/totalCount/hasDangerousPermission
 * 这几个聚合字段；完整的权限明细在详情页通过 refreshAppDetail() 现查现算即可。
 */
@Entity(tableName = "apps")
data class AppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val versionName: String,
    val isSystemApp: Boolean,
    val targetSdk: Int,
    val grantedCount: Int,
    val totalCount: Int,
    val hasDangerousPermission: Boolean,
    val versionCode: Long,
    val lastUpdateTime: Long
)

fun AppEntity.toDomain(): AppInfo = AppInfo(
    packageName = packageName,
    appName = appName,
    versionName = versionName,
    icon = null,
    isSystemApp = isSystemApp,
    targetSdk = targetSdk,
    permissions = emptyList(),
    grantedCount = grantedCount,
    totalCount = totalCount,
    versionCode = versionCode,
    lastUpdateTime = lastUpdateTime,
    isRuntimeVerified = false,
    hasDangerousPermission = hasDangerousPermission
)

fun AppInfo.toEntity(): AppEntity = AppEntity(
    packageName = packageName,
    appName = appName,
    versionName = versionName,
    isSystemApp = isSystemApp,
    targetSdk = targetSdk,
    grantedCount = grantedCount,
    totalCount = totalCount,
    hasDangerousPermission = hasDangerousPermission,
    versionCode = versionCode,
    lastUpdateTime = lastUpdateTime
)
