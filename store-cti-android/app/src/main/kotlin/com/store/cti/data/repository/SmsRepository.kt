package com.store.cti.data.repository

import com.store.cti.core.model.SmsDraftStatus
import com.store.cti.core.phone.PhoneNumberUtils
import com.store.cti.data.local.dao.SmsDraftDao
import com.store.cti.data.local.dao.SmsTemplateDao
import com.store.cti.data.local.entity.SmsDraftEntity
import com.store.cti.data.local.entity.SmsTemplateEntity
import com.store.cti.util.newUuid
import com.store.cti.util.nowEpochMillis
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsRepository @Inject constructor(
    private val templateDao: SmsTemplateDao,
    private val draftDao: SmsDraftDao,
    private val auditLogger: AuditLogger,
) {
    // --- テンプレート ---

    fun observeTemplates(): Flow<List<SmsTemplateEntity>> = templateDao.observeActive()
    fun observeEnabledTemplates(): Flow<List<SmsTemplateEntity>> = templateDao.observeEnabled()
    suspend fun getTemplate(id: String): SmsTemplateEntity? = templateDao.getById(id)

    suspend fun saveTemplate(entity: SmsTemplateEntity, isNew: Boolean) {
        val now = nowEpochMillis()
        templateDao.upsert(
            entity.copy(
                createdAt = if (isNew) now else entity.createdAt,
                updatedAt = now,
                version = if (isNew) 1 else entity.version + 1,
            ),
        )
        auditLogger.log(
            if (isNew) AuditLogger.ACTION_CREATE else AuditLogger.ACTION_UPDATE,
            AuditLogger.TARGET_TEMPLATE,
            entity.id,
        )
    }

    suspend fun softDeleteTemplate(id: String) {
        val entity = templateDao.getById(id) ?: return
        val now = nowEpochMillis()
        templateDao.upsert(entity.copy(deletedAt = now, updatedAt = now, version = entity.version + 1))
        auditLogger.log(AuditLogger.ACTION_SOFT_DELETE, AuditLogger.TARGET_TEMPLATE, id)
    }

    /** 並び順を入れ替える(sortOrder を交換) */
    suspend fun swapOrder(a: SmsTemplateEntity, b: SmsTemplateEntity) {
        val now = nowEpochMillis()
        templateDao.upsert(a.copy(sortOrder = b.sortOrder, updatedAt = now, version = a.version + 1))
        templateDao.upsert(b.copy(sortOrder = a.sortOrder, updatedAt = now, version = b.version + 1))
    }

    /** 初回起動時の初期テンプレート投入(仕様書 6-9) */
    suspend fun seedInitialTemplatesIfEmpty(): Boolean {
        if (templateDao.count() > 0) return false
        val now = nowEpochMillis()
        val templates = listOf(
            "予約確定" to "ご予約ありがとうございます。{customerName}様のご予約は、{reservationDate} {startTime}から承りました。変更やキャンセルがある場合は、お早めにご連絡ください。",
            "折り返し" to "先ほどはお電話に出られず申し訳ございません。こちらのSMSへ返信、またはお電話にてご連絡ください。",
            "来店後のお礼" to "本日はご来店ありがとうございました。またのご利用をお待ちしております。",
        )
        templateDao.upsertAll(
            templates.mapIndexed { index, (name, body) ->
                SmsTemplateEntity(
                    id = newUuid(),
                    name = name,
                    body = body,
                    sortOrder = index,
                    enabled = true,
                    createdAt = now,
                    updatedAt = now,
                )
            },
        )
        return true
    }

    // --- SMS作成履歴 ---

    fun observeDrafts(): Flow<List<SmsDraftEntity>> = draftDao.observeActive()
    fun observeDraftsByCustomer(customerId: String): Flow<List<SmsDraftEntity>> =
        draftDao.observeByCustomer(customerId)

    suspend fun saveDraft(entity: SmsDraftEntity, isNew: Boolean): SmsDraftEntity {
        val now = nowEpochMillis()
        val stamped = entity.copy(
            displayPhoneNumber = entity.displayPhoneNumber.trim(),
            normalizedPhoneNumber = PhoneNumberUtils.normalize(entity.displayPhoneNumber),
            createdAt = if (isNew) now else entity.createdAt,
            updatedAt = now,
            version = if (isNew) 1 else entity.version + 1,
        )
        draftDao.upsert(stamped)
        if (isNew) {
            auditLogger.log(AuditLogger.ACTION_CREATE, AuditLogger.TARGET_SMS_DRAFT, stamped.id)
        }
        return stamped
    }

    suspend fun updateDraftStatus(id: String, status: SmsDraftStatus) {
        val entity = draftDao.getById(id) ?: return
        val now = nowEpochMillis()
        draftDao.upsert(entity.copy(status = status.name, updatedAt = now, version = entity.version + 1))
    }
}
