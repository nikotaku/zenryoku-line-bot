package com.store.cti.data.repository

import com.store.cti.data.local.dao.AuditLogDao
import com.store.cti.data.local.entity.AuditLogEntity
import com.store.cti.data.settings.SettingsRepository
import com.store.cti.util.newUuid
import com.store.cti.util.nowEpochMillis
import javax.inject.Inject
import javax.inject.Singleton

/** 監査ログ記録。detail に個人情報を入れないこと。 */
@Singleton
class AuditLogger @Inject constructor(
    private val auditLogDao: AuditLogDao,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun log(action: String, targetType: String, targetId: String? = null, detail: String = "") {
        auditLogDao.insert(
            AuditLogEntity(
                id = newUuid(),
                occurredAt = nowEpochMillis(),
                actorStaffName = settingsRepository.current().defaultStaffName,
                action = action,
                targetType = targetType,
                targetId = targetId,
                detail = detail,
            ),
        )
    }

    companion object {
        const val ACTION_CREATE = "CREATE"
        const val ACTION_UPDATE = "UPDATE"
        const val ACTION_SOFT_DELETE = "SOFT_DELETE"
        const val ACTION_EXPORT_CSV = "EXPORT_CSV"
        const val ACTION_EXPORT_BACKUP = "EXPORT_BACKUP"
        const val ACTION_IMPORT_BACKUP = "IMPORT_BACKUP"
        const val ACTION_WIPE_ALL = "WIPE_ALL"
        const val ACTION_SEED_SAMPLE = "SEED_SAMPLE"

        const val TARGET_CUSTOMER = "CUSTOMER"
        const val TARGET_RESERVATION = "RESERVATION"
        const val TARGET_INTERACTION = "INTERACTION"
        const val TARGET_SMS_DRAFT = "SMS_DRAFT"
        const val TARGET_TEMPLATE = "TEMPLATE"
        const val TARGET_STAFF = "STAFF"
        const val TARGET_ALL = "ALL"
    }
}
