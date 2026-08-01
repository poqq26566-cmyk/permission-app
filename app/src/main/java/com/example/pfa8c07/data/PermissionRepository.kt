package com.example.pfa8c07.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.example.pfa8c07.model.AppInfo
import com.example.pfa8c07.model.AppPermission
import com.example.pfa8c07.model.PermissionGroups

/**
 * 权限查询仓库 — 获取已安装应用及其权限状态
 */
class PermissionRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    /**
     * 获取所有已安装的非系统应用（可选包含系统应用）。
     *
     * 参照 Thor 的扫描策略：
     * 1. 不在扫描阶段加载图标（交给 UI 按需异步加载，见 IconCache）；
     * 2. 不对每条权限做 checkPermission() 这种跨进程调用，只读
     *    requestedPermissionsFlags 里已有的授权位（信息已经在 PackageInfo 里，零 IPC）；
     * 3. 传入上一次的扫描结果作为缓存，versionCode/lastUpdateTime 都没变的应用
     *    直接复用缓存对象，完全跳过重新解析。
     */
    @SuppressLint("QueryPermissionsNeeded")
    fun getInstalledApps(
        includeSystem: Boolean = false,
        cachedApps: Map<String, AppInfo> = emptyMap(),
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): List<AppInfo> {
        val packages: List<PackageInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val flags = PackageManager.PackageInfoFlags.of(
                PackageManager.GET_PERMISSIONS.toLong()
            )
            pm.getInstalledPackages(flags)
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }

        val filtered = packages.filter { info ->
            val isSystem = isSystemApp(info)
            if (includeSystem) true else !isSystem
        }
        val total = filtered.size
        val result = ArrayList<AppInfo>(total)

        filtered.forEachIndexed { index, info ->
            onProgress?.invoke(index + 1, total)

            val cached = cachedApps[info.packageName]
            if (cached != null &&
                cached.versionCode == info.longVersionCode &&
                cached.lastUpdateTime == info.lastUpdateTime
            ) {
                // 未变化，直接复用，跳过一切解析
                result.add(cached)
                return@forEachIndexed
            }

            val appName = info.applicationInfo?.let { pm.getApplicationLabel(it) }?.toString() ?: info.packageName
            val isSystem = isSystemApp(info)

            // 轻量解析：只用 flags 位判断是否授权，不发起 checkPermission IPC
            val permList = parsePermissions(info, lightweight = true)

            result.add(
                AppInfo(
                    packageName = info.packageName,
                    appName = appName,
                    versionName = info.versionName ?: "",
                    icon = null,
                    isSystemApp = isSystem,
                    targetSdk = info.applicationInfo?.targetSdkVersion ?: 0,
                    permissions = permList,
                    grantedCount = permList.count { it.isGranted },
                    totalCount = permList.size,
                    versionCode = info.longVersionCode,
                    lastUpdateTime = info.lastUpdateTime,
                    isRuntimeVerified = false,
                    hasDangerousPermission = permList.any { it.isDangerous }
                )
            )
        }

        return result.sortedBy { it.appName }
    }

    /**
     * 获取单个应用的权限详情
     */
    fun getAppPermissionDetail(packageName: String): AppInfo? {
        return try {
            val info: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val flags = PackageManager.PackageInfoFlags.of(
                    PackageManager.GET_PERMISSIONS.toLong()
                )
                pm.getPackageInfo(packageName, flags)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            }

            val appName = info.applicationInfo?.let { pm.getApplicationLabel(it) }?.toString() ?: packageName
            val isSystem = isSystemApp(info)

            // 详情页只针对单个应用，checkPermission 的开销可以接受，用它做精确校验
            val permList = parsePermissions(info, lightweight = false)

            AppInfo(
                packageName = info.packageName,
                appName = appName,
                versionName = info.versionName ?: "",
                icon = null,
                isSystemApp = isSystem,
                targetSdk = info.applicationInfo?.targetSdkVersion ?: 0,
                permissions = permList,
                grantedCount = permList.count { it.isGranted },
                totalCount = permList.size,
                versionCode = info.longVersionCode,
                lastUpdateTime = info.lastUpdateTime,
                isRuntimeVerified = true,
                hasDangerousPermission = permList.any { it.isDangerous }
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析权限列表并检查当前授予状态
     */
    private fun parsePermissions(info: PackageInfo, lightweight: Boolean): List<AppPermission> {
        val requestedPermissions = info.requestedPermissions ?: return emptyList()
        val requestedPermissionsFlags = info.requestedPermissionsFlags ?: return emptyList()

        return requestedPermissions.mapIndexedNotNull { index, permName ->
            if (permName.isBlank()) return@mapIndexedNotNull null

            val isGranted = if (index < requestedPermissionsFlags.size) {
                (requestedPermissionsFlags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            } else false

            // 运行时检查（更准确，但是跨进程调用，列表扫描阶段跳过，只在详情页做）
            val runtimeGranted = if (lightweight) {
                isGranted
            } else {
                try {
                    context.checkPermission(permName, Process.myPid(), Process.myUid()) ==
                            PackageManager.PERMISSION_GRANTED
                } catch (e: Exception) {
                    isGranted
                }
            }

            val label = getPermissionLabel(permName)
            val group = getPermissionGroup(permName)
            val isDangerous = isDangerousPermission(permName)

            AppPermission(
                name = permName,
                label = label,
                description = getPermissionDescription(permName),
                isGranted = runtimeGranted || isGranted,
                group = group,
                isDangerous = isDangerous
            )
        }
    }

    private fun isSystemApp(info: PackageInfo): Boolean {
        val ai = info.applicationInfo ?: return false
        return (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
    }

    companion object {
        /**
         * 获取权限的中文可读名称
         */
        fun getPermissionLabel(permName: String): String {
            return PERMISSION_LABEL_MAP[permName] ?: run {
                // 从权限名末尾提取简称
                val short = permName.substringAfterLast(".")
                short.replace("_", " ")
            }
        }

        /**
         * 获取权限所属分组
         */
        fun getPermissionGroup(permName: String): String {
            return PERMISSION_GROUP_MAP.entries.firstOrNull { (prefix, _) ->
                permName.startsWith(prefix)
            }?.value ?: PermissionGroups.OTHER
        }

        fun getPermissionDescription(permName: String): String {
            return PERMISSION_DESC_MAP[permName] ?: ""
        }

        fun isDangerousPermission(permName: String): Boolean {
            return DANGEROUS_PERMISSIONS.contains(permName)
        }

        /** 按前缀分组的映射 */
        private val PERMISSION_GROUP_MAP = mapOf(
            "android.permission.ACCESS_COARSE_LOCATION" to PermissionGroups.LOCATION,
            "android.permission.ACCESS_FINE_LOCATION" to PermissionGroups.LOCATION,
            "android.permission.ACCESS_BACKGROUND_LOCATION" to PermissionGroups.LOCATION,
            "android.permission.CAMERA" to PermissionGroups.CAMERA,
            "android.permission.RECORD_AUDIO" to PermissionGroups.MICROPHONE,
            "android.permission.READ_EXTERNAL_STORAGE" to PermissionGroups.STORAGE,
            "android.permission.WRITE_EXTERNAL_STORAGE" to PermissionGroups.STORAGE,
            "android.permission.READ_MEDIA_IMAGES" to PermissionGroups.STORAGE,
            "android.permission.READ_MEDIA_VIDEO" to PermissionGroups.STORAGE,
            "android.permission.READ_MEDIA_AUDIO" to PermissionGroups.STORAGE,
            "android.permission.READ_PHONE_STATE" to PermissionGroups.PHONE,
            "android.permission.CALL_PHONE" to PermissionGroups.PHONE,
            "android.permission.READ_CALL_LOG" to PermissionGroups.PHONE,
            "android.permission.WRITE_CALL_LOG" to PermissionGroups.PHONE,
            "android.permission.ADD_VOICEMAIL" to PermissionGroups.PHONE,
            "android.permission.USE_SIP" to PermissionGroups.PHONE,
            "android.permission.PROCESS_OUTGOING_CALLS" to PermissionGroups.PHONE,
            "android.permission.READ_CONTACTS" to PermissionGroups.CONTACTS,
            "android.permission.WRITE_CONTACTS" to PermissionGroups.CONTACTS,
            "android.permission.GET_ACCOUNTS" to PermissionGroups.CONTACTS,
            "android.permission.SEND_SMS" to PermissionGroups.SMS,
            "android.permission.RECEIVE_SMS" to PermissionGroups.SMS,
            "android.permission.READ_SMS" to PermissionGroups.SMS,
            "android.permission.RECEIVE_WAP_PUSH" to PermissionGroups.SMS,
            "android.permission.RECEIVE_MMS" to PermissionGroups.SMS,
            "android.permission.READ_CALENDAR" to PermissionGroups.CALENDAR,
            "android.permission.WRITE_CALENDAR" to PermissionGroups.CALENDAR,
            "android.permission.BODY_SENSORS" to PermissionGroups.SENSORS,
            "android.permission.ACTIVITY_RECOGNITION" to PermissionGroups.ACTIVITY_RECOGNITION,
            "android.permission.BLUETOOTH_SCAN" to PermissionGroups.NEARBY_DEVICES,
            "android.permission.BLUETOOTH_ADVERTISE" to PermissionGroups.NEARBY_DEVICES,
            "android.permission.BLUETOOTH_CONNECT" to PermissionGroups.NEARBY_DEVICES,
            "android.permission.POST_NOTIFICATIONS" to PermissionGroups.NOTIFICATIONS,
            "android.permission.ACCESS_MEDIA_LOCATION" to PermissionGroups.MEDIA_LOCATION,
        )

        /** 权限中文名映射 */
        private val PERMISSION_LABEL_MAP = mapOf(
            "android.permission.ACCESS_COARSE_LOCATION" to "粗略位置",
            "android.permission.ACCESS_FINE_LOCATION" to "精确位置",
            "android.permission.ACCESS_BACKGROUND_LOCATION" to "后台位置",
            "android.permission.CAMERA" to "相机",
            "android.permission.RECORD_AUDIO" to "麦克风",
            "android.permission.READ_EXTERNAL_STORAGE" to "读取存储",
            "android.permission.WRITE_EXTERNAL_STORAGE" to "写入存储",
            "android.permission.READ_MEDIA_IMAGES" to "读取图片",
            "android.permission.READ_MEDIA_VIDEO" to "读取视频",
            "android.permission.READ_MEDIA_AUDIO" to "读取音频",
            "android.permission.READ_PHONE_STATE" to "读取设备状态",
            "android.permission.CALL_PHONE" to "拨打电话",
            "android.permission.READ_CALL_LOG" to "读取通话记录",
            "android.permission.WRITE_CALL_LOG" to "写入通话记录",
            "android.permission.ADD_VOICEMAIL" to "添加语音邮件",
            "android.permission.USE_SIP" to "使用 SIP",
            "android.permission.PROCESS_OUTGOING_CALLS" to "处理拨出电话",
            "android.permission.READ_CONTACTS" to "读取通讯录",
            "android.permission.WRITE_CONTACTS" to "写入通讯录",
            "android.permission.GET_ACCOUNTS" to "获取账户",
            "android.permission.SEND_SMS" to "发送短信",
            "android.permission.RECEIVE_SMS" to "接收短信",
            "android.permission.READ_SMS" to "读取短信",
            "android.permission.RECEIVE_WAP_PUSH" to "接收 WAP 推送",
            "android.permission.RECEIVE_MMS" to "接收彩信",
            "android.permission.READ_CALENDAR" to "读取日历",
            "android.permission.WRITE_CALENDAR" to "写入日历",
            "android.permission.BODY_SENSORS" to "身体传感器",
            "android.permission.ACTIVITY_RECOGNITION" to "活动识别",
            "android.permission.BLUETOOTH_SCAN" to "蓝牙扫描",
            "android.permission.BLUETOOTH_ADVERTISE" to "蓝牙广播",
            "android.permission.BLUETOOTH_CONNECT" to "蓝牙连接",
            "android.permission.POST_NOTIFICATIONS" to "发送通知",
            "android.permission.ACCESS_MEDIA_LOCATION" to "访问媒体位置",
            "android.permission.INTERNET" to "互联网",
            "android.permission.ACCESS_NETWORK_STATE" to "网络状态",
            "android.permission.ACCESS_WIFI_STATE" to "Wi-Fi 状态",
            "android.permission.CHANGE_WIFI_STATE" to "修改 Wi-Fi 状态",
            "android.permission.VIBRATE" to "振动",
            "android.permission.WAKE_LOCK" to "唤醒锁定",
            "android.permission.FLASHLIGHT" to "闪光灯",
            "android.permission.BLUETOOTH" to "蓝牙",
            "android.permission.NFC" to "NFC",
            "android.permission.SYSTEM_ALERT_WINDOW" to "悬浮窗",
            "android.permission.REQUEST_INSTALL_PACKAGES" to "安装应用",
            "android.permission.MANAGE_EXTERNAL_STORAGE" to "管理所有文件",
            "android.permission.FOREGROUND_SERVICE" to "前台服务",
            "android.permission.USE_BIOMETRIC" to "使用生物识别",
            "android.permission.USE_FINGERPRINT" to "使用指纹",
            "android.permission.ACCESS_NOTIFICATION_POLICY" to "通知策略",
            "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" to "通知监听",
            "android.permission.EXPAND_STATUS_BAR" to "展开状态栏",
            "android.permission.SET_ALARM" to "设置闹钟",
            "android.permission.READ_SYNC_SETTINGS" to "读取同步设置",
            "android.permission.WRITE_SYNC_SETTINGS" to "写入同步设置",
            "android.permission.AUTHENTICATE_ACCOUNTS" to "账户认证",
            "android.permission.MANAGE_ACCOUNTS" to "管理账户",
            "android.permission.READ_PROFILE" to "读取个人资料",
            "android.permission.WRITE_PROFILE" to "写入个人资料",
            "android.permission.READ_SOCIAL_STREAM" to "读取社交动态",
            "android.permission.WRITE_SOCIAL_STREAM" to "写入社交动态",
            "android.permission.BATTERY_STATS" to "电池统计",
            "android.permission.REORDER_TASKS" to "重排任务",
            "android.permission.CHANGE_CONFIGURATION" to "修改配置",
            "android.permission.BIND_ACCESSIBILITY_SERVICE" to "无障碍服务",
            "android.permission.BIND_INPUT_METHOD" to "输入法",
            "android.permission.BIND_VPN_SERVICE" to "VPN",
            "android.permission.BIND_DEVICE_ADMIN" to "设备管理",
            "android.permission.READ_LOGS" to "读取日志",
            "android.permission.DUMP" to "导出信息",
            "android.permission.PERSISTENT_ACTIVITY" to "持久活动",
            "android.permission.INTERACT_ACROSS_USERS_FULL" to "跨用户交互",
            "android.permission.CAPTURE_VIDEO_OUTPUT" to "屏幕录制",
            "android.permission.CAPTURE_AUDIO_OUTPUT" to "音频录制",
            "com.android.voicemail.permission.ADD_VOICEMAIL" to "添加语音邮件",
            "com.android.launcher.permission.INSTALL_SHORTCUT" to "安装快捷方式",
            "com.android.launcher.permission.UNINSTALL_SHORTCUT" to "卸载快捷方式",
        )

        /** 权限描述 */
        private val PERMISSION_DESC_MAP = mapOf(
            "android.permission.ACCESS_COARSE_LOCATION" to "允许应用获取设备的大致位置",
            "android.permission.ACCESS_FINE_LOCATION" to "允许应用获取设备的精确位置",
            "android.permission.ACCESS_BACKGROUND_LOCATION" to "允许应用在后台时获取位置",
            "android.permission.CAMERA" to "允许应用使用相机拍照和录制视频",
            "android.permission.RECORD_AUDIO" to "允许应用使用麦克风录音",
            "android.permission.READ_EXTERNAL_STORAGE" to "允许应用读取外部存储中的内容",
            "android.permission.WRITE_EXTERNAL_STORAGE" to "允许应用写入外部存储",
            "android.permission.READ_CONTACTS" to "允许应用读取您的通讯录",
            "android.permission.WRITE_CONTACTS" to "允许应用修改您的通讯录",
            "android.permission.SEND_SMS" to "允许应用发送短信",
            "android.permission.READ_SMS" to "允许应用读取短信",
            "android.permission.RECEIVE_SMS" to "允许应用接收短信",
            "android.permission.READ_CALENDAR" to "允许应用读取日历事件",
            "android.permission.WRITE_CALENDAR" to "允许应用写入日历事件",
            "android.permission.READ_PHONE_STATE" to "允许应用读取设备标识和状态",
            "android.permission.CALL_PHONE" to "允许应用直接拨打电话",
            "android.permission.POST_NOTIFICATIONS" to "允许应用发送通知",
        )

        /** 危险权限列表 */
        private val DANGEROUS_PERMISSIONS = setOf(
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.GET_ACCOUNTS",
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_WAP_PUSH",
            "android.permission.RECEIVE_MMS",
            "android.permission.READ_CALENDAR",
            "android.permission.WRITE_CALENDAR",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.CALL_PHONE",
            "android.permission.READ_PHONE_STATE",
            "android.permission.BODY_SENSORS",
            "android.permission.ACTIVITY_RECOGNITION",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.ACCESS_MEDIA_LOCATION",
        )
    }
}
