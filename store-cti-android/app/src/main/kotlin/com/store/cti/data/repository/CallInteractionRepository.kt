package com.store.cti.data.repository

import com.store.cti.core.phone.PhoneNumberUtils
import com.store.cti.data.local.dao.CallInteractionDao
import com.store.cti.data.local.entity.CallInteractionEntity
import com.store.cti.util.nowEpochMillis
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallInteractionRepository @Inject constructor(
    private val dao: CallInteractionDao,
    private val auditLogger: AuditLogger,
) {
    fun observeActive(): Flow<List<CallInteractionEntity>> = dao.observeActive()
    fun observeByCustomer(customerId: String): Flow<List<CallInteractionEntity>> =
        dao.observeByCustomer(customerId)

    suspend fun getById(id: String): CallInteractionEntity? = dao.getById(id)

    /** 顧客IDまたは電話番号に紐づく直近の対応履歴(着信対応画面の「直近3件」) */
    suspend fun recentFor(customerId: String?, rawPhone: String, limit: Int = 3): List<CallInteractionEntity> =
        dao.recentFor(customerId, PhoneNumberUtils.normalize(rawPhone), limit)

    suspend fun save(entity: CallInteractionEntity, isNew: Boolean): CallInteractionEntity {
        val now = nowEpochMillis()
        val stamped = entity.copy(
            displayPhoneNumber = entity.displayPhoneNumber.trim(),
            normalizedPhoneNumber = PhoneNumberUtils.normalize(entity.displayPhoneNumber),
            createdAt = if (isNew) now else entity.createdAt,
            updatedAt = now,
            version = if (isNew) 1 else entity.version + 1,
        )
        dao.upsert(stamped)
        auditLogger.log(
            if (isNew) AuditLogger.ACTION_CREATE else AuditLogger.ACTION_UPDATE,
            AuditLogger.TARGET_INTERACTION,
            stamped.id,
        )
        return stamped
    }

    suspend fun softDelete(id: String) {
        val entity = dao.getById(id) ?: return
        val now = nowEpochMillis()
        dao.upsert(entity.copy(deletedAt = now, updatedAt = now, version = entity.version + 1))
        auditLogger.log(AuditLogger.ACTION_SOFT_DELETE, AuditLogger.TARGET_INTERACTION, id)
    }

    /** 折り返し完了にする */
    suspend fun markCallbackDone(id: String) {
        val entity = dao.getById(id) ?: return
        val now = nowEpochMillis()
        dao.upsert(entity.copy(callbackDone = true, updatedAt = now, version = entity.version + 1))
    }
}
