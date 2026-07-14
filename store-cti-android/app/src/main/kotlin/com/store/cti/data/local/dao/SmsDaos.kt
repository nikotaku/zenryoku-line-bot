package com.store.cti.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.store.cti.data.local.entity.SmsDraftEntity
import com.store.cti.data.local.entity.SmsTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDraftDao {

    @Query("SELECT * FROM sms_drafts WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<SmsDraftEntity>>

    @Query(
        "SELECT * FROM sms_drafts WHERE deletedAt IS NULL AND customerId = :customerId " +
            "ORDER BY createdAt DESC",
    )
    fun observeByCustomer(customerId: String): Flow<List<SmsDraftEntity>>

    @Query("SELECT * FROM sms_drafts WHERE id = :id")
    suspend fun getById(id: String): SmsDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SmsDraftEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SmsDraftEntity>)

    @Query("SELECT * FROM sms_drafts")
    suspend fun getAllIncludingDeleted(): List<SmsDraftEntity>

    @Query("DELETE FROM sms_drafts")
    suspend fun deleteAll()
}

@Dao
interface SmsTemplateDao {

    @Query("SELECT * FROM sms_templates WHERE deletedAt IS NULL ORDER BY sortOrder ASC, createdAt ASC")
    fun observeActive(): Flow<List<SmsTemplateEntity>>

    @Query(
        "SELECT * FROM sms_templates WHERE deletedAt IS NULL AND enabled = 1 " +
            "ORDER BY sortOrder ASC, createdAt ASC",
    )
    fun observeEnabled(): Flow<List<SmsTemplateEntity>>

    @Query("SELECT * FROM sms_templates WHERE id = :id")
    suspend fun getById(id: String): SmsTemplateEntity?

    @Query("SELECT COUNT(*) FROM sms_templates")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SmsTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SmsTemplateEntity>)

    @Query("SELECT * FROM sms_templates")
    suspend fun getAllIncludingDeleted(): List<SmsTemplateEntity>

    @Query("DELETE FROM sms_templates")
    suspend fun deleteAll()
}
