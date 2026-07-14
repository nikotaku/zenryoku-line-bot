package com.store.cti.data.repository

import com.store.cti.data.local.dao.StaffDao
import com.store.cti.data.local.entity.StaffEntity
import com.store.cti.util.newUuid
import com.store.cti.util.nowEpochMillis
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StaffRepository @Inject constructor(
    private val dao: StaffDao,
    private val auditLogger: AuditLogger,
) {
    fun observeActive(): Flow<List<StaffEntity>> = dao.observeActive()
    fun observeUsable(): Flow<List<StaffEntity>> = dao.observeUsable()

    suspend fun add(name: String, sortOrder: Int) {
        val now = nowEpochMillis()
        val entity = StaffEntity(
            id = newUuid(),
            name = name.trim(),
            sortOrder = sortOrder,
            active = true,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(entity)
        auditLogger.log(AuditLogger.ACTION_CREATE, AuditLogger.TARGET_STAFF, entity.id)
    }

    suspend fun setActive(entity: StaffEntity, active: Boolean) {
        val now = nowEpochMillis()
        dao.upsert(entity.copy(active = active, updatedAt = now, version = entity.version + 1))
    }

    suspend fun softDelete(entity: StaffEntity) {
        val now = nowEpochMillis()
        dao.upsert(entity.copy(deletedAt = now, updatedAt = now, version = entity.version + 1))
        auditLogger.log(AuditLogger.ACTION_SOFT_DELETE, AuditLogger.TARGET_STAFF, entity.id)
    }
}
