package com.example.pfa8c07.util

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用图标的内存缓存 + 按需异步加载。
 *
 * 参照 Thor 的做法：扫描应用列表时完全不碰图标（PackageManager#getApplicationIcon
 * 是同步 IPC，几百个应用一起加载非常慢），图标只在真正要显示的那一行、
 * 且尚未缓存时才去加载，加载一次后常驻内存，滚动/返回都不会重新触发 IPC。
 */
object IconCache {

    // 大多数手机装的应用不会超过几百个，图标按 48dp 量级缓存，内存占用可控
    private val cache = LruCache<String, Drawable>(300)

    fun getCached(packageName: String): Drawable? = cache.get(packageName)

    suspend fun load(context: Context, packageName: String): Drawable? {
        cache.get(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                cache.put(packageName, drawable)
                drawable
            } catch (e: Exception) {
                null
            }
        }
    }

    fun clear() = cache.evictAll()
}
