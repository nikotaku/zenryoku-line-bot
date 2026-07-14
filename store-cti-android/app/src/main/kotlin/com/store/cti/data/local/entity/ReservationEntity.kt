package com.store.cti.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.store.cti.core.model.ReservationChannel
import com.store.cti.core.model.ReservationStatus
import kotlinx.serialization.Serializable

/** 予約。date は ISO LocalDate、startTime/endTime は HH:mm。 */
@Serializable
@Entity(
    tableName = "reservations",
    indices = [
        Index("customerId"),
        Index("date"),
        Index("status"),
        Index("deletedAt"),
    ],
)
data class ReservationEntity(
    @PrimaryKey val id: String,
    val customerId: String? = null,
    val date: String = "",                   // ISO LocalDate
    val startTime: String = "",              // HH:mm
    val endTime: String = "",                // HH:mm
    val staffName: String = "",
    val courseName: String = "",
    val price: Long? = null,
    val channel: String = ReservationChannel.PHONE.name,
    val status: String = ReservationStatus.TENTATIVE.name,
    val room: String = "",
    val requests: String = "",
    val cautionNotes: String = "",
    val createdByStaffName: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val syncStatus: String = "LOCAL_ONLY",
    val version: Int = 1,
) {
    val statusEnum: ReservationStatus get() = ReservationStatus.fromName(status)
    val channelEnum: ReservationChannel get() = ReservationChannel.fromName(channel)
}
