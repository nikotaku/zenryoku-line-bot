package com.store.cti.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.store.cti.data.local.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {

    @Query("SELECT * FROM customers WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeActive(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id")
    fun observeById(id: String): Flow<CustomerEntity?>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getById(id: String): CustomerEntity?

    @Query(
        "SELECT * FROM customers WHERE deletedAt IS NULL " +
            "AND (normalizedPhoneNumber = :normalized OR normalizedPhoneNumber2 = :normalized)",
    )
    suspend fun findByNormalizedPhone(normalized: String): List<CustomerEntity>

    @Query(
        "SELECT * FROM customers WHERE deletedAt IS NULL " +
            "AND (normalizedPhoneNumber LIKE '%' || :tail OR normalizedPhoneNumber2 LIKE '%' || :tail)",
    )
    suspend fun findByPhoneTail(tail: String): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE deletedAt IS NULL ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<CustomerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CustomerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CustomerEntity>)

    @Query("SELECT * FROM customers")
    suspend fun getAllIncludingDeleted(): List<CustomerEntity>

    @Query("DELETE FROM customers")
    suspend fun deleteAll()
}
