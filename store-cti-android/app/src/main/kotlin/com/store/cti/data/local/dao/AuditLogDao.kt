package com.store.cti.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.store.cti.data.local.entity.AuditLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {

    @Query("SELECT * FROM audit_logs ORDER BY occurredAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditLogEntity>>

    @Insert
    suspend fun insert(entity: AuditLogEntity)

    @Insert
    suspend fun insertAll(entities: List<AuditLogEntity>)

    @Query("SELECT * FROM audit_logs")
    suspend fun getAll(): List<AuditLogEntity>

    @Query("DELETE FROM audit_logs")
    suspend fun deleteAll()
}
