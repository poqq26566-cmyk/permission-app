package com.example.pfa8c07.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppDao {

    @Query("SELECT * FROM apps")
    suspend fun getAll(): List<AppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(apps: List<AppEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: AppEntity)

    /** 清理已卸载的应用，避免缓存里越堆越多幽灵条目 */
    @Query("DELETE FROM apps WHERE packageName NOT IN (:keepPackageNames)")
    suspend fun deleteMissing(keepPackageNames: List<String>)
}
