package com.example.pfa8c07.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AppEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "permission_app_cache.db"
                )
                    // 缓存表纯粹是扫描结果的镜像，随时能从 PackageManager 重新生成，
                    // 版本升级时直接丢弃重建比写迁移脚本更划算
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
