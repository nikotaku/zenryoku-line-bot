package com.store.cti.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** スタッフ */
@Serializable
@Entity(tableName = "staff", indices = [Index("deletedAt")])
data class StaffEntity(
    @PrimaryKey val id: String,
    val name: String = "",
    val sortOrder: Int = 0,
    val active: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val syncStatus: String = "LOCAL_ONLY",
    val version: Int = 1,
)
