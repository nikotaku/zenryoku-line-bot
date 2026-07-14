package com.store.cti.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.store.cti.data.local.entity.CallInteractionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallInteractionDao {

    @Query("SELECT * FROM call_interactions WHERE deletedAt IS NULL ORDER BY occurredAt DESC")
    fun observeActive(): Flow<List<CallInteractionEntity>>

    @Query(
        "SELECT * FROM call_interactions WHERE deletedAt IS NULL AND customerId = :customerId " +
            "ORDER BY occurredAt DESC",
    )
    fun observeByCustomer(customerId: String): Flow<List<CallInteractionEntity>>

    @Query("SELECT * FROM call_interactions WHERE id = :id")
    suspend fun getById(id: String): CallInteractionEntity?

    @Query(
        "SELECT * FROM call_interactions WHERE deletedAt IS NULL " +
            "AND (customerId = :customerId OR normalizedPhoneNumber = :normalizedPhone) " +
            "ORDER BY occurredAt DESC LIMIT :limit",
    )
    suspend fun recentFor(customerId: String?, normalizedPhone: String, limit: Int): List<CallInteractionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CallInteractionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CallInteractionEntity>)

    @Query("SELECT * FROM call_interactions")
    suspend fun getAllIncludingDeleted(): List<CallInteractionEntity>

    @Query("DELETE FROM call_interactions")
    suspend fun deleteAll()
}
