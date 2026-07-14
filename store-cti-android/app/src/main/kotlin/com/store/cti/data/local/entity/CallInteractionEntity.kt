package com.store.cti.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.store.cti.core.model.CallDirection
import com.store.cti.core.model.CallResult
import kotlinx.serialization.Serializable

/** 通話対応履歴。occurredAt / nextActionAt は ISO LocalDateTime 文字列。 */
@Serializable
@Entity(
    tableName = "call_interactions",
    indices = [
        Index("customerId"),
        Index("normalizedPhoneNumber"),
        Index("occurredAt"),
        Index("deletedAt"),
    ],
)
data class CallInteractionEntity(
    @PrimaryKey val id: String,
    val customerId: String? = null,
    val displayPhoneNumber: String = "",
    val normalizedPhoneNumber: String = "",
    val occurredAt: String = "",             // ISO LocalDateTime
    val direction: String = CallDirection.INCOMING.name,
    val answered: Boolean = true,
    val result: String = CallResult.INQUIRY_ONLY.name,
    val inquiry: String = "",
    val desiredDateTime: String = "",
    val desiredStaffName: String = "",
    val guidanceGiven: String = "",
    val reservationCreated: Boolean = false,
    val reservationId: String? = null,
    val callbackRequired: Boolean = false,
    val callbackDone: Boolean = false,
    val nextActionAt: String? = null,        // ISO LocalDateTime
    val staffName: String = "",
    val memo: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val syncStatus: String = "LOCAL_ONLY",
    val version: Int = 1,
) {
    val directionEnum: CallDirection get() = CallDirection.fromName(direction)
    val resultEnum: CallResult get() = CallResult.fromName(result)
    val callbackPending: Boolean get() = callbackRequired && !callbackDone
}
