package com.store.cti.di

import android.content.Context
import androidx.room.Room
import com.store.cti.data.local.AppDatabase
import com.store.cti.data.local.dao.AuditLogDao
import com.store.cti.data.local.dao.CallInteractionDao
import com.store.cti.data.local.dao.CustomerDao
import com.store.cti.data.local.dao.ReservationDao
import com.store.cti.data.local.dao.SmsDraftDao
import com.store.cti.data.local.dao.SmsTemplateDao
import com.store.cti.data.local.dao.StaffDao
import com.store.cti.telephony.CallScreeningGateway
import com.store.cti.telephony.DialerRoleGateway
import com.store.cti.telephony.IntentLaunchers
import com.store.cti.telephony.NotImplementedDialerRoleGateway
import com.store.cti.telephony.OutboundCallLauncher
import com.store.cti.telephony.RecordingProvider
import com.store.cti.telephony.RoleManagerCallScreeningGateway
import com.store.cti.telephony.SmsComposerLauncher
import com.store.cti.telephony.UnsupportedRecordingProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides fun provideCustomerDao(db: AppDatabase): CustomerDao = db.customerDao()
    @Provides fun provideCallInteractionDao(db: AppDatabase): CallInteractionDao = db.callInteractionDao()
    @Provides fun provideReservationDao(db: AppDatabase): ReservationDao = db.reservationDao()
    @Provides fun provideSmsDraftDao(db: AppDatabase): SmsDraftDao = db.smsDraftDao()
    @Provides fun provideSmsTemplateDao(db: AppDatabase): SmsTemplateDao = db.smsTemplateDao()
    @Provides fun provideStaffDao(db: AppDatabase): StaffDao = db.staffDao()
    @Provides fun provideAuditLogDao(db: AppDatabase): AuditLogDao = db.auditLogDao()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TelephonyModule {

    @Binds
    abstract fun bindOutboundCallLauncher(impl: IntentLaunchers): OutboundCallLauncher

    @Binds
    abstract fun bindSmsComposerLauncher(impl: IntentLaunchers): SmsComposerLauncher

    @Binds
    abstract fun bindCallScreeningGateway(impl: RoleManagerCallScreeningGateway): CallScreeningGateway

    @Binds
    abstract fun bindDialerRoleGateway(impl: NotImplementedDialerRoleGateway): DialerRoleGateway

    @Binds
    abstract fun bindRecordingProvider(impl: UnsupportedRecordingProvider): RecordingProvider
}
