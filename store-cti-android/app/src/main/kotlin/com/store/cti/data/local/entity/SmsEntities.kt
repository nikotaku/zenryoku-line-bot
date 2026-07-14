package com.store.cti.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.store.cti.core.model.SmsDraftStatus
import kotlinx.serialization.Serializable

/** SMS作成履歴(実際に送信されたかは断定できないため「送信済み」状態は持たない) */
@Serializable
@Entity(
    tableName = "sms_drafts",
    indices = [Index("customerId"), Index("normalizedPhoneNumber"), Index("deletedAt")],
)
data class SmsDraftEntity(
    @PrimaryKey val id: String,
    val customerId: String? = null,
    val reservationId: String? = null,
    val displayPhoneNumber: String = "",
    val normalizedPhoneNumber: String = "",
    val templateId: String? = null,
    val templateName: String = "",
    val body: String = "",
    val status: String = SmsDraftStatus.DRAFT.name,
    val staffName: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val syncStatus: String = "LOCAL_ONLY",
    val version: Int = 1,
) {
    val statusEnum: SmsDraftStatus get() = SmsDraftStatus.fromName(status)
}

/** SMS定型文テンプレート */
@Serializable
@Entity(tableName = "sms_templates", indices = [Index("deletedAt")])
data class SmsTemplateEntity(
    @PrimaryKey val id: String,
    val name: String = "",
    val body: String = "",
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val syncStatus: String = "LOCAL_ONLY",
    val version: Int = 1,
)
