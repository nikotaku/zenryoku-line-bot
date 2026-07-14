package com.store.cti.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.store.cti.data.local.entity.ReservationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReservationDao {

    @Query("SELECT * FROM reservations WHERE deletedAt IS NULL ORDER BY date DESC, startTime ASC")
    fun observeActive(): Flow<List<ReservationEntity>>

    @Query(
        "SELECT * FROM reservations WHERE deletedAt IS NULL AND date = :isoDate " +
            "ORDER BY startTime ASC",
    )
    fun observeByDate(isoDate: String): Flow<List<ReservationEntity>>

    @Query(
        "SELECT * FROM reservations WHERE deletedAt IS NULL AND date = :isoDate " +
            "ORDER BY startTime ASC",
    )
    suspend fun listByDate(isoDate: String): List<ReservationEntity>

    @Query(
        "SELECT * FROM reservations WHERE deletedAt IS NULL AND customerId = :customerId " +
            "ORDER BY date DESC, startTime ASC",
    )
    fun observeByCustomer(customerId: String): Flow<List<ReservationEntity>>

    @Query("SELECT * FROM reservations WHERE id = :id")
    suspend fun getById(id: String): ReservationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReservationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ReservationEntity>)

    @Query("SELECT * FROM reservations")
    suspend fun getAllIncludingDeleted(): List<ReservationEntity>

    @Query("DELETE FROM reservations")
    suspend fun deleteAll()
}
