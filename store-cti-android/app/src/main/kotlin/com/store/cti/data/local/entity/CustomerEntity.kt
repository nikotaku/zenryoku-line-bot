package com.store.cti.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.store.cti.core.model.CustomerCategory
import kotlinx.serialization.Serializable

/**
 * 顧客。日付は ISO-8601 文字列(yyyy-MM-dd)、created/updated/deletedAt は epoch millis。
 * バックアップ(JSON)へそのまま書き出せるよう @Serializable を付与している。
 */
@Serializable
@Entity(
    tableName = "customers",
    indices = [
        Index("normalizedPhoneNumber"),
        Index("normalizedPhoneNumber2"),
        Index("kana"),
        Index("customerNumber"),
        Index("deletedAt"),
    ],
)
data class CustomerEntity(
    @PrimaryKey val id: String,
    val customerNumber: String = "",
    val name: String = "",
    val kana: String = "",
    val displayPhoneNumber: String = "",
    val normalizedPhoneNumber: String = "",
    val displayPhoneNumber2: String = "",
    val normalizedPhoneNumber2: String = "",
    val lineName: String = "",
    val firstVisitDate: String? = null,      // ISO LocalDate
    val lastVisitDate: String? = null,       // ISO LocalDate
    val visitCount: Int = 0,
    val lastStaffName: String = "",
    val preferredStaffName: String = "",
    val category: String = CustomerCategory.NEW.name,
    val cautionNotes: String = "",
    val servicePreferences: String = "",
    val prohibitedActions: String = "",
    val memo: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val syncStatus: String = "LOCAL_ONLY",
    val version: Int = 1,
) {
    val categoryEnum: CustomerCategory get() = CustomerCategory.fromName(category)
    val isDeleted: Boolean get() = deletedAt != null
}
