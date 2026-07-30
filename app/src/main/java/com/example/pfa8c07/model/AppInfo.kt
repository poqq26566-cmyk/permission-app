package com.example.pfa8c07.model

import android.graphics.drawable.Drawable

/**
 * 已安装应用的信息
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean,
    val targetSdk: Int,
    /** 所有声明的权限列表 */
    val permissions: List<AppPermission> = emptyList(),
    /** 已授予的权限数 */
    val grantedCount: Int = 0,
    /** 权限总数 */
    val totalCount: Int = 0
)

/**
 * 单个权限的状态
 */
data class AppPermission(
    val name: String,
    val label: String = "",
    val description: String = "",
    val isGranted: Boolean = false,
    /** 权限分组：如 位置、相机、存储、其他 */
    val group: String = "其他",
    /** 是否为危险权限 */
    val isDangerous: Boolean = false
)

/**
 * 权限分组常量
 */
object PermissionGroups {
    const val LOCATION = "位置信息"
    const val CAMERA = "相机"
    const val MICROPHONE = "麦克风"
    const val STORAGE = "存储"
    const val PHONE = "电话"
    const val CONTACTS = "通讯录"
    const val SMS = "短信"
    const val CALENDAR = "日历"
    const val SENSORS = "传感器"
    const val ACTIVITY_RECOGNITION = "身体活动"
    const val NEARBY_DEVICES = "附近设备"
    const val NOTIFICATIONS = "通知"
    const val MEDIA_LOCATION = "媒体位置"
    const val OTHER = "其他"
}
