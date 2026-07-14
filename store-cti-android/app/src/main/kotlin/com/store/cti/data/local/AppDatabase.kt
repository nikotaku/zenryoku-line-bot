package com.store.cti.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.store.cti.data.local.dao.AuditLogDao
import com.store.cti.data.local.dao.CallInteractionDao
import com.store.cti.data.local.dao.CustomerDao
import com.store.cti.data.local.dao.ReservationDao
import com.store.cti.data.local.dao.SmsDraftDao
import com.store.cti.data.local.dao.SmsTemplateDao
import com.store.cti.data.local.dao.StaffDao
import com.store.cti.data.local.entity.AuditLogEntity
import com.store.cti.data.local.entity.CallInteractionEntity
import com.store.cti.data.local.entity.CustomerEntity
import com.store.cti.data.local.entity.ReservationEntity
import com.store.cti.data.local.entity.SmsDraftEntity
import com.store.cti.data.local.entity.SmsTemplateEntity
import com.store.cti.data.local.entity.StaffEntity

@Database(
    entities = [
        CustomerEntity::class,
        CallInteractionEntity::class,
        ReservationEntity::class,
        SmsDraftEntity::class,
        SmsTemplateEntity::class,
        StaffEntity::class,
        AuditLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun callInteractionDao(): CallInteractionDao
    abstract fun reservationDao(): ReservationDao
    abstract fun smsDraftDao(): SmsDraftDao
    abstract fun smsTemplateDao(): SmsTemplateDao
    abstract fun staffDao(): StaffDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        const val NAME = "store_cti.db"
    }
}
