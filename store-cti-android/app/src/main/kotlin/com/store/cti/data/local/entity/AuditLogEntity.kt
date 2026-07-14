package com.store.cti.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 監査ログ(追記専用)。detail に個人情報を含めないこと(docs/SECURITY.md 8章)。
 */
@Serializable
@Entity(tableName = "audit_logs", indices = [Index("occurredAt")])
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val occurredAt: Long = 0L,
    val actorStaffName: String = "",
    val action: String = "",     // CREATE / UPDATE / SOFT_DELETE / EXPORT_CSV / ...
    val targetType: String = "", // CUSTOMER / RESERVATION / INTERACTION / ...
    val targetId: String? = null,
    val detail: String = "",
)
