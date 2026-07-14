package com.store.cti.data.repository

import com.store.cti.core.phone.PhoneNumberUtils
import com.store.cti.data.local.dao.CustomerDao
import com.store.cti.data.local.entity.CustomerEntity
import com.store.cti.util.nowEpochMillis
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomerRepository @Inject constructor(
    private val customerDao: CustomerDao,
    private val auditLogger: AuditLogger,
) {
    fun observeActive(): Flow<List<CustomerEntity>> = customerDao.observeActive()
    fun observeById(id: String): Flow<CustomerEntity?> = customerDao.observeById(id)
    fun observeRecent(limit: Int = 5): Flow<List<CustomerEntity>> = customerDao.observeRecent(limit)
    suspend fun getById(id: String): CustomerEntity? = customerDao.getById(id)

    /**
     * 電話番号で顧客を検索する(完全一致 → 見つからなければ末尾一致)。
     * 入力の表記ゆれは正規化で吸収する。
     */
    suspend fun findByPhone(raw: String): List<CustomerEntity> {
        val normalized = PhoneNumberUtils.normalize(raw)
        if (normalized.isEmpty()) return emptyList()
        val exact = customerDao.findByNormalizedPhone(normalized)
        if (exact.isNotEmpty()) return exact
        return if (normalized.length >= 4) customerDao.findByPhoneTail(normalized) else emptyList()
    }

    /** 重複電話番号を持つ他の有効顧客(警告表示用) */
    suspend fun findDuplicatesByPhone(raw: String, selfId: String?): List<CustomerEntity> {
        val normalized = PhoneNumberUtils.normalize(raw)
        if (normalized.isEmpty()) return emptyList()
        return customerDao.findByNormalizedPhone(normalized).filter { it.id != selfId }
    }

    /** 新規または更新を保存する。タイムスタンプ・version は本メソッドで付与する。 */
    suspend fun save(entity: CustomerEntity, isNew: Boolean): CustomerEntity {
        val now = nowEpochMillis()
        val stamped = entity.copy(
            displayPhoneNumber = entity.displayPhoneNumber.trim(),
            normalizedPhoneNumber = PhoneNumberUtils.normalize(entity.displayPhoneNumber),
            displayPhoneNumber2 = entity.displayPhoneNumber2.trim(),
            normalizedPhoneNumber2 = PhoneNumberUtils.normalize(entity.displayPhoneNumber2),
            createdAt = if (isNew) now else entity.createdAt,
            updatedAt = now,
            version = if (isNew) 1 else entity.version + 1,
        )
        customerDao.upsert(stamped)
        auditLogger.log(
            if (isNew) AuditLogger.ACTION_CREATE else AuditLogger.ACTION_UPDATE,
            AuditLogger.TARGET_CUSTOMER,
            stamped.id,
        )
        return stamped
    }

    /** 論理削除 */
    suspend fun softDelete(id: String) {
        val entity = customerDao.getById(id) ?: return
        val now = nowEpochMillis()
        customerDao.upsert(entity.copy(deletedAt = now, updatedAt = now, version = entity.version + 1))
        auditLogger.log(AuditLogger.ACTION_SOFT_DELETE, AuditLogger.TARGET_CUSTOMER, id)
    }

    /** 来店実績を反映する(予約の来店済み処理から呼ぶ) */
    suspend fun recordVisit(customerId: String, visitDateIso: String, staffName: String) {
        val entity = customerDao.getById(customerId) ?: return
        val now = nowEpochMillis()
        customerDao.upsert(
            entity.copy(
                visitCount = entity.visitCount + 1,
                lastVisitDate = visitDateIso,
                lastStaffName = staffName.ifBlank { entity.lastStaffName },
                updatedAt = now,
                version = entity.version + 1,
            ),
        )
    }
}
