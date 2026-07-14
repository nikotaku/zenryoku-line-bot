package com.store.cti.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.store.cti.data.local.entity.StaffEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StaffDao {

    @Query("SELECT * FROM staff WHERE deletedAt IS NULL ORDER BY sortOrder ASC, createdAt ASC")
    fun observeActive(): Flow<List<StaffEntity>>

    @Query(
        "SELECT * FROM staff WHERE deletedAt IS NULL AND active = 1 " +
            "ORDER BY sortOrder ASC, createdAt ASC",
    )
    fun observeUsable(): Flow<List<StaffEntity>>

    @Query("SELECT * FROM staff WHERE id = :id")
    suspend fun getById(id: String): StaffEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StaffEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<StaffEntity>)

    @Query("SELECT * FROM staff")
    suspend fun getAllIncludingDeleted(): List<StaffEntity>

    @Query("DELETE FROM staff")
    suspend fun deleteAll()
}
