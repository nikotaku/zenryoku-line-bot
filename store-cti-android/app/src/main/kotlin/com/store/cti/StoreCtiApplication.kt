package com.store.cti

import android.app.Application
import com.store.cti.data.repository.SmsRepository
import com.store.cti.data.settings.SettingsRepository
import com.store.cti.di.ApplicationScope
import com.store.cti.util.AppLogger
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class StoreCtiApplication : Application() {

    @Inject lateinit var smsRepository: SmsRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            try {
                if (!settingsRepository.current().seededInitialTemplates) {
                    smsRepository.seedInitialTemplatesIfEmpty()
                    settingsRepository.setSeededInitialTemplates(true)
                }
            } catch (t: Throwable) {
                AppLogger.e("initial template seeding failed", t)
            }
        }
    }
}
