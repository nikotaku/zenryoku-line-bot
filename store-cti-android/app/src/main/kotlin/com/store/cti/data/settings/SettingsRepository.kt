package com.store.cti.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** アプリ設定のスナップショット */
data class AppSettings(
    val storeName: String = "",
    val storePhone: String = "",
    val defaultStaffName: String = "",
    val businessHoursStart: String = "10:00",
    val businessHoursEnd: String = "22:00",
    val defaultSlotMinutes: Int = 60,
    val phoneDisplayFormat: String = PHONE_FORMAT_HYPHEN, // HYPHEN / RAW
    val maskPhoneNumbers: Boolean = false,
    val blockScreenshots: Boolean = false,
    val callScreeningEnabled: Boolean = false,
    val seededInitialTemplates: Boolean = false,
) {
    companion object {
        const val PHONE_FORMAT_HYPHEN = "HYPHEN"
        const val PHONE_FORMAT_RAW = "RAW"
    }
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val storeName = stringPreferencesKey("storeName")
        val storePhone = stringPreferencesKey("storePhone")
        val defaultStaffName = stringPreferencesKey("defaultStaffName")
        val businessHoursStart = stringPreferencesKey("businessHoursStart")
        val businessHoursEnd = stringPreferencesKey("businessHoursEnd")
        val defaultSlotMinutes = intPreferencesKey("defaultSlotMinutes")
        val phoneDisplayFormat = stringPreferencesKey("phoneDisplayFormat")
        val maskPhoneNumbers = booleanPreferencesKey("maskPhoneNumbers")
        val blockScreenshots = booleanPreferencesKey("blockScreenshots")
        val callScreeningEnabled = booleanPreferencesKey("callScreeningEnabled")
        val seededInitialTemplates = booleanPreferencesKey("seededInitialTemplates")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            storeName = p[Keys.storeName] ?: "",
            storePhone = p[Keys.storePhone] ?: "",
            defaultStaffName = p[Keys.defaultStaffName] ?: "",
            businessHoursStart = p[Keys.businessHoursStart] ?: "10:00",
            businessHoursEnd = p[Keys.businessHoursEnd] ?: "22:00",
            defaultSlotMinutes = p[Keys.defaultSlotMinutes] ?: 60,
            phoneDisplayFormat = p[Keys.phoneDisplayFormat] ?: AppSettings.PHONE_FORMAT_HYPHEN,
            maskPhoneNumbers = p[Keys.maskPhoneNumbers] ?: false,
            blockScreenshots = p[Keys.blockScreenshots] ?: false,
            callScreeningEnabled = p[Keys.callScreeningEnabled] ?: false,
            seededInitialTemplates = p[Keys.seededInitialTemplates] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setStoreName(value: String) = edit { it[Keys.storeName] = value }
    suspend fun setStorePhone(value: String) = edit { it[Keys.storePhone] = value }
    suspend fun setDefaultStaffName(value: String) = edit { it[Keys.defaultStaffName] = value }
    suspend fun setBusinessHours(start: String, end: String) = edit {
        it[Keys.businessHoursStart] = start
        it[Keys.businessHoursEnd] = end
    }
    suspend fun setDefaultSlotMinutes(value: Int) = edit { it[Keys.defaultSlotMinutes] = value }
    suspend fun setPhoneDisplayFormat(value: String) = edit { it[Keys.phoneDisplayFormat] = value }
    suspend fun setMaskPhoneNumbers(value: Boolean) = edit { it[Keys.maskPhoneNumbers] = value }
    suspend fun setBlockScreenshots(value: Boolean) = edit { it[Keys.blockScreenshots] = value }
    suspend fun setCallScreeningEnabled(value: Boolean) = edit { it[Keys.callScreeningEnabled] = value }
    suspend fun setSeededInitialTemplates(value: Boolean) = edit { it[Keys.seededInitialTemplates] = value }

    /** バックアップ復元用の一括反映 */
    suspend fun restoreFrom(s: AppSettings) = edit {
        it[Keys.storeName] = s.storeName
        it[Keys.storePhone] = s.storePhone
        it[Keys.defaultStaffName] = s.defaultStaffName
        it[Keys.businessHoursStart] = s.businessHoursStart
        it[Keys.businessHoursEnd] = s.businessHoursEnd
        it[Keys.defaultSlotMinutes] = s.defaultSlotMinutes
        it[Keys.phoneDisplayFormat] = s.phoneDisplayFormat
        it[Keys.maskPhoneNumbers] = s.maskPhoneNumbers
        it[Keys.blockScreenshots] = s.blockScreenshots
        it[Keys.callScreeningEnabled] = s.callScreeningEnabled
        it[Keys.seededInitialTemplates] = s.seededInitialTemplates
    }

    suspend fun clearAll() {
        context.settingsDataStore.edit { it.clear() }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit { block(it) }
    }
}
