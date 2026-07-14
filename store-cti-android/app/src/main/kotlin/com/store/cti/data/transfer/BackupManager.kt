package com.store.cti.data.transfer

import android.content.Context
import android.net.Uri
import com.store.cti.BuildConfig
import com.store.cti.data.local.AppDatabase
import com.store.cti.data.local.entity.AuditLogEntity
import com.store.cti.data.local.entity.CallInteractionEntity
import com.store.cti.data.local.entity.CustomerEntity
import com.store.cti.data.local.entity.ReservationEntity
import com.store.cti.data.local.entity.SmsDraftEntity
import com.store.cti.data.local.entity.SmsTemplateEntity
import com.store.cti.data.local.entity.StaffEntity
import com.store.cti.data.repository.AuditLogger
import com.store.cti.data.settings.AppSettings
import com.store.cti.data.settings.SettingsRepository
import com.store.cti.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** 処理結果(利用者向けメッセージのみ。例外文をUIへ出さない) */
sealed class TransferOutcome {
    data class Success(val message: String) : TransferOutcome()
    data class Failure(val userMessage: String) : TransferOutcome()
}

@Serializable
data class BackupSettings(
    val storeName: String = "",
    val storePhone: String = "",
    val defaultStaffName: String = "",
    val businessHoursStart: String = "10:00",
    val businessHoursEnd: String = "22:00",
    val defaultSlotMinutes: Int = 60,
    val phoneDisplayFormat: String = "HYPHEN",
    val maskPhoneNumbers: Boolean = false,
    val blockScreenshots: Boolean = false,
    val callScreeningEnabled: Boolean = false,
)

@Serializable
data class BackupFile(
    @SerialName("formatVersion") val formatVersion: Int,
    val appVersion: String = "",
    val exportedAt: String = "",
    val customers: List<CustomerEntity> = emptyList(),
    val callInteractions: List<CallInteractionEntity> = emptyList(),
    val reservations: List<ReservationEntity> = emptyList(),
    val smsDrafts: List<SmsDraftEntity> = emptyList(),
    val smsTemplates: List<SmsTemplateEntity> = emptyList(),
    val staff: List<StaffEntity> = emptyList(),
    val auditLogs: List<AuditLogEntity> = emptyList(),
    val settings: BackupSettings = BackupSettings(),
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val auditLogger: AuditLogger,
    private val json: Json,
) {
    /** 現在の全データからバックアップJSONを生成する(論理削除済みも含む) */
    suspend fun buildBackup(): BackupFile {
        val settings = settingsRepository.current()
        return BackupFile(
            formatVersion = BackupFile.CURRENT_FORMAT_VERSION,
            appVersion = BuildConfig.VERSION_NAME,
            exportedAt = LocalDateTime.now().toString(),
            customers = database.customerDao().getAllIncludingDeleted(),
            callInteractions = database.callInteractionDao().getAllIncludingDeleted(),
            reservations = database.reservationDao().getAllIncludingDeleted(),
            smsDrafts = database.smsDraftDao().getAllIncludingDeleted(),
            smsTemplates = database.smsTemplateDao().getAllIncludingDeleted(),
            staff = database.staffDao().getAllIncludingDeleted(),
            auditLogs = database.auditLogDao().getAll(),
            settings = settings.toBackup(),
        )
    }

    /** SAFで選択された保存先へバックアップを書き出す */
    suspend fun exportTo(uri: Uri): TransferOutcome = withContext(Dispatchers.IO) {
        try {
            val backup = buildBackup()
            val text = json.encodeToString(BackupFile.serializer(), backup)
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
            } ?: return@withContext TransferOutcome.Failure("保存先を開けませんでした")
            auditLogger.log(
                AuditLogger.ACTION_EXPORT_BACKUP, AuditLogger.TARGET_ALL,
                detail = "customers=${backup.customers.size} reservations=${backup.reservations.size}",
            )
            TransferOutcome.Success("バックアップを保存しました(顧客${backup.customers.size}件)")
        } catch (t: Throwable) {
            AppLogger.e("backup export failed", t)
            TransferOutcome.Failure("バックアップの保存に失敗しました。空き容量と保存先を確認してください")
        }
    }

    /**
     * バックアップから復元する(全置換)。
     * 実行前に内部ストレージへ自動プリバックアップを作成する。
     */
    suspend fun restoreFrom(uri: Uri): TransferOutcome = withContext(Dispatchers.IO) {
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return@withContext TransferOutcome.Failure("ファイルを開けませんでした")
        } catch (t: Throwable) {
            AppLogger.e("backup read failed", t)
            return@withContext TransferOutcome.Failure("ファイルの読み込みに失敗しました")
        }

        val backup = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (t: Throwable) {
            AppLogger.e("backup parse failed", t)
            return@withContext TransferOutcome.Failure("復元ファイルが壊れているか、形式が違います。現在のデータは変更されていません")
        }
        if (backup.formatVersion > BackupFile.CURRENT_FORMAT_VERSION) {
            return@withContext TransferOutcome.Failure(
                "このバックアップは新しいバージョンのアプリで作成されています。アプリを更新してから復元してください",
            )
        }

        try {
            createAutoPreBackup()
        } catch (t: Throwable) {
            AppLogger.e("pre-backup failed", t)
            return@withContext TransferOutcome.Failure("復元前の自動バックアップに失敗したため、復元を中止しました")
        }

        try {
            replaceAll(backup)
            auditLogger.log(
                AuditLogger.ACTION_IMPORT_BACKUP, AuditLogger.TARGET_ALL,
                detail = "customers=${backup.customers.size}",
            )
            TransferOutcome.Success("復元が完了しました(顧客${backup.customers.size}件)")
        } catch (t: Throwable) {
            AppLogger.e("restore failed", t)
            TransferOutcome.Failure("復元に失敗しました。復元前の自動バックアップは設定画面から確認できます")
        }
    }

    /** 復元前の自動バックアップ(内部ストレージ files/backups/) */
    suspend fun createAutoPreBackup(): File {
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        val stamp = LocalDateTime.now().toString().replace(":", "-")
        val file = File(dir, "pre_restore_$stamp.json")
        val backup = buildBackup()
        file.writeText(json.encodeToString(BackupFile.serializer(), backup), Charsets.UTF_8)
        // 直近5件だけ残す
        dir.listFiles()?.sortedByDescending { it.name }?.drop(5)?.forEach { it.delete() }
        return file
    }

    fun listAutoBackups(): List<File> =
        File(context.filesDir, "backups").listFiles()?.sortedByDescending { it.name } ?: emptyList()

    private suspend fun replaceAll(backup: BackupFile) {
        with(database) {
            customerDao().deleteAll()
            callInteractionDao().deleteAll()
            reservationDao().deleteAll()
            smsDraftDao().deleteAll()
            smsTemplateDao().deleteAll()
            staffDao().deleteAll()
            auditLogDao().deleteAll()

            customerDao().upsertAll(backup.customers)
            callInteractionDao().upsertAll(backup.callInteractions)
            reservationDao().upsertAll(backup.reservations)
            smsDraftDao().upsertAll(backup.smsDrafts)
            smsTemplateDao().upsertAll(backup.smsTemplates)
            staffDao().upsertAll(backup.staff)
            auditLogDao().insertAll(backup.auditLogs)
        }
        settingsRepository.restoreFrom(backup.settings.toAppSettings())
    }

    /** データ初期化(全削除)。設定も初期値へ戻す。 */
    suspend fun wipeAll(): TransferOutcome = withContext(Dispatchers.IO) {
        try {
            with(database) {
                customerDao().deleteAll()
                callInteractionDao().deleteAll()
                reservationDao().deleteAll()
                smsDraftDao().deleteAll()
                smsTemplateDao().deleteAll()
                staffDao().deleteAll()
                auditLogDao().deleteAll()
            }
            settingsRepository.clearAll()
            auditLogger.log(AuditLogger.ACTION_WIPE_ALL, AuditLogger.TARGET_ALL)
            TransferOutcome.Success("すべてのデータを削除しました")
        } catch (t: Throwable) {
            AppLogger.e("wipe failed", t)
            TransferOutcome.Failure("データの削除に失敗しました")
        }
    }
}

private fun AppSettings.toBackup() = BackupSettings(
    storeName = storeName,
    storePhone = storePhone,
    defaultStaffName = defaultStaffName,
    businessHoursStart = businessHoursStart,
    businessHoursEnd = businessHoursEnd,
    defaultSlotMinutes = defaultSlotMinutes,
    phoneDisplayFormat = phoneDisplayFormat,
    maskPhoneNumbers = maskPhoneNumbers,
    blockScreenshots = blockScreenshots,
    callScreeningEnabled = callScreeningEnabled,
)

private fun BackupSettings.toAppSettings() = AppSettings(
    storeName = storeName,
    storePhone = storePhone,
    defaultStaffName = defaultStaffName,
    businessHoursStart = businessHoursStart,
    businessHoursEnd = businessHoursEnd,
    defaultSlotMinutes = defaultSlotMinutes,
    phoneDisplayFormat = phoneDisplayFormat,
    maskPhoneNumbers = maskPhoneNumbers,
    blockScreenshots = blockScreenshots,
    callScreeningEnabled = callScreeningEnabled,
    seededInitialTemplates = true,
)
