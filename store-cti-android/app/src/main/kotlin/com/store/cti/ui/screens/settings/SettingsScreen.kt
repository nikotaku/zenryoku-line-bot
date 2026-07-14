package com.store.cti.ui.screens.settings

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.store.cti.BuildConfig
import com.store.cti.data.SampleDataSeeder
import com.store.cti.data.local.dao.AuditLogDao
import com.store.cti.data.local.entity.AuditLogEntity
import com.store.cti.data.local.entity.StaffEntity
import com.store.cti.data.repository.StaffRepository
import com.store.cti.data.settings.AppSettings
import com.store.cti.data.settings.SettingsRepository
import com.store.cti.data.transfer.BackupManager
import com.store.cti.data.transfer.CsvExporter
import com.store.cti.data.transfer.CsvTarget
import com.store.cti.data.transfer.TransferOutcome
import com.store.cti.telephony.CallScreeningGateway
import com.store.cti.telephony.RecordingProvider
import com.store.cti.ui.common.AppTextField
import com.store.cti.ui.common.ChipSelector
import com.store.cti.ui.common.ConfirmDialog
import com.store.cti.ui.common.ScreenScaffold
import com.store.cti.ui.common.SectionCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val staffRepository: StaffRepository,
    private val backupManager: BackupManager,
    private val csvExporter: CsvExporter,
    private val sampleDataSeeder: SampleDataSeeder,
    val callScreeningGateway: CallScreeningGateway,
    val recordingProvider: RecordingProvider,
    auditLogDao: AuditLogDao,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val staff: StateFlow<List<StaffEntity>> = staffRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val auditLogs: StateFlow<List<AuditLogEntity>> = auditLogDao.observeRecent(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun setStoreName(v: String) = run { settingsRepository.setStoreName(v) }
    fun setStorePhone(v: String) = run { settingsRepository.setStorePhone(v) }
    fun setDefaultStaff(v: String) = run { settingsRepository.setDefaultStaffName(v) }
    fun setBusinessHours(start: String, end: String) = run { settingsRepository.setBusinessHours(start, end) }
    fun setSlotMinutes(v: Int) = run { settingsRepository.setDefaultSlotMinutes(v) }
    fun setPhoneFormat(v: String) = run { settingsRepository.setPhoneDisplayFormat(v) }
    fun setMask(v: Boolean) = run { settingsRepository.setMaskPhoneNumbers(v) }
    fun setBlockScreenshots(v: Boolean) = run { settingsRepository.setBlockScreenshots(v) }
    fun setCallScreeningEnabled(v: Boolean) = run { settingsRepository.setCallScreeningEnabled(v) }

    fun addStaff(name: String) {
        if (name.isBlank()) return
        run { staffRepository.add(name, staff.value.size) }
    }

    fun setStaffActive(entity: StaffEntity, active: Boolean) = run { staffRepository.setActive(entity, active) }
    fun deleteStaff(entity: StaffEntity) = run { staffRepository.softDelete(entity) }

    fun exportBackup(uri: Uri) = run { emitOutcome(backupManager.exportTo(uri)) }
    fun restoreBackup(uri: Uri) = run { emitOutcome(backupManager.restoreFrom(uri)) }
    fun exportCsv(target: CsvTarget, uri: Uri) = run { emitOutcome(csvExporter.exportTo(target, uri)) }
    fun wipeAll() = run { emitOutcome(backupManager.wipeAll()) }

    fun seedSampleData() = run {
        sampleDataSeeder.seed()
        _messages.emit("サンプルデータを投入しました")
    }

    private suspend fun emitOutcome(outcome: TransferOutcome) {
        when (outcome) {
            is TransferOutcome.Success -> _messages.emit(outcome.message)
            is TransferOutcome.Failure -> _messages.emit(outcome.userMessage)
        }
    }

    fun autoBackupCount(): Int = backupManager.listAutoBackups().size
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenTemplates: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val staff by viewModel.staff.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    // --- SAF ランチャー ---
    var pendingCsvTarget by remember { mutableStateOf<CsvTarget?>(null) }
    val backupCreateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportBackup) }
    val csvCreateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/comma-separated-values"),
    ) { uri ->
        val target = pendingCsvTarget
        if (uri != null && target != null) viewModel.exportCsv(target, uri)
        pendingCsvTarget = null
    }
    val restoreOpenLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::restoreBackup) }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    // --- ダイアログ状態 ---
    var showExportWarning by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showRestoreWarning by remember { mutableStateOf(false) }
    var showWipeConfirm1 by remember { mutableStateOf(false) }
    var showWipeConfirm2 by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showAuditLog by remember { mutableStateOf(false) }

    showExportWarning?.let { proceed ->
        ConfirmDialog(
            title = "個人情報の持ち出しに注意",
            text = "顧客の個人情報を含むファイルを端末外に保存します。保存先とその後の取り扱いに十分注意してください。",
            confirmLabel = "続行する",
            onConfirm = {
                showExportWarning = null
                proceed()
            },
            onDismiss = { showExportWarning = null },
        )
    }
    if (showRestoreWarning) {
        ConfirmDialog(
            title = "データの復元",
            text = "復元すると現在のデータはすべて置き換えられます。実行前に現在のデータの自動バックアップを内部ストレージへ作成します。続行しますか?",
            confirmLabel = "ファイルを選ぶ",
            destructive = true,
            onConfirm = {
                showRestoreWarning = false
                restoreOpenLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
            },
            onDismiss = { showRestoreWarning = false },
        )
    }
    if (showWipeConfirm1) {
        ConfirmDialog(
            title = "データの初期化",
            text = "顧客・予約・対応履歴・SMS履歴・設定をすべて削除します。この操作は元に戻せません。",
            confirmLabel = "次へ",
            destructive = true,
            onConfirm = {
                showWipeConfirm1 = false
                showWipeConfirm2 = true
            },
            onDismiss = { showWipeConfirm1 = false },
        )
    }
    if (showWipeConfirm2) {
        ConfirmDialog(
            title = "本当に削除しますか?",
            text = "必要であれば先にバックアップを作成してください。「削除する」を押すと即座に全データが消去されます。",
            confirmLabel = "削除する",
            destructive = true,
            onConfirm = {
                showWipeConfirm2 = false
                viewModel.wipeAll()
            },
            onDismiss = { showWipeConfirm2 = false },
        )
    }
    if (showPrivacyPolicy) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicy = false },
            title = { Text("プライバシーポリシー(要旨)") },
            text = {
                Text(
                    "・本アプリは店舗の顧客管理のための社内専用アプリです。\n" +
                        "・顧客情報はこの端末内(アプリ専用領域)にのみ保存され、外部サーバーへ送信されません。\n" +
                        "・バックアップ・CSVはスタッフが明示的に操作した場合のみ作成されます。\n" +
                        "・通話内容の録音は行いません。\n" +
                        "・詳細は配布物の PRIVACY_POLICY_TEMPLATE.md を参照してください。",
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyPolicy = false }) { Text("閉じる") }
            },
        )
    }
    if (showAuditLog) {
        AlertDialog(
            onDismissRequest = { showAuditLog = false },
            title = { Text("デバッグ情報(監査ログ 直近${auditLogs.size}件)") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    auditLogs.forEach { log ->
                        Text(
                            "${java.time.Instant.ofEpochMilli(log.occurredAt)} ${log.action} " +
                                "${log.targetType} ${log.detail}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (auditLogs.isEmpty()) Text("記録はありません")
                }
            },
            confirmButton = {
                TextButton(onClick = { showAuditLog = false }) { Text("閉じる") }
            },
        )
    }

    ScreenScaffold(title = "設定", onBack = onBack, snackbarHostState = snackbar) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionCard("店舗情報") {
                var storeName by remember(settings.storeName) { mutableStateOf(settings.storeName) }
                var storePhone by remember(settings.storePhone) { mutableStateOf(settings.storePhone) }
                AppTextField(storeName, { storeName = it }, "店舗名")
                AppTextField(storePhone, { storePhone = it }, "店舗電話番号", placeholder = "03-1234-5678")
                OutlinedButton(onClick = {
                    viewModel.setStoreName(storeName)
                    viewModel.setStorePhone(storePhone)
                }) { Text("店舗情報を保存") }
            }

            SectionCard("スタッフ") {
                staff.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(s.name, style = MaterialTheme.typography.bodyLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = s.active, onCheckedChange = { viewModel.setStaffActive(s, it) })
                            TextButton(onClick = { viewModel.deleteStaff(s) }) {
                                Text("削除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                var newStaff by remember { mutableStateOf("") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppTextField(newStaff, { newStaff = it }, "スタッフ名を追加", Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        viewModel.addStaff(newStaff)
                        newStaff = ""
                    }) { Text("追加") }
                }
                if (staff.any { it.active }) {
                    ChipSelector(
                        label = "既定スタッフ(対応履歴などの初期値)",
                        options = staff.filter { it.active }.map { it.name },
                        selected = settings.defaultStaffName.takeIf { it.isNotBlank() },
                        optionLabel = { it },
                        onSelect = viewModel::setDefaultStaff,
                    )
                }
            }

            SectionCard("営業時間・予約枠") {
                var start by remember(settings.businessHoursStart) { mutableStateOf(settings.businessHoursStart) }
                var end by remember(settings.businessHoursEnd) { mutableStateOf(settings.businessHoursEnd) }
                var slot by remember(settings.defaultSlotMinutes) { mutableStateOf(settings.defaultSlotMinutes.toString()) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextField(start, { start = it }, "開店", Modifier.weight(1f), placeholder = "10:00")
                    AppTextField(end, { end = it }, "閉店", Modifier.weight(1f), placeholder = "22:00")
                }
                AppTextField(slot, { slot = it.filter { c -> c.isDigit() } }, "予約枠の標準時間(分)")
                OutlinedButton(onClick = {
                    viewModel.setBusinessHours(start, end)
                    viewModel.setSlotMinutes(slot.toIntOrNull() ?: 60)
                }) { Text("保存") }
            }

            SectionCard("表示と保護") {
                ChipSelector(
                    label = "電話番号の表示形式",
                    options = listOf(AppSettings.PHONE_FORMAT_HYPHEN, AppSettings.PHONE_FORMAT_RAW),
                    selected = settings.phoneDisplayFormat,
                    optionLabel = { if (it == AppSettings.PHONE_FORMAT_HYPHEN) "自動ハイフン" else "入力どおり" },
                    onSelect = viewModel::setPhoneFormat,
                )
                SwitchRow("電話番号をマスク表示", settings.maskPhoneNumbers, viewModel::setMask)
                SwitchRow("スクリーンショットを禁止", settings.blockScreenshots, viewModel::setBlockScreenshots)
            }

            SectionCard("SMSテンプレート") {
                OutlinedButton(onClick = onOpenTemplates, modifier = Modifier.fillMaxWidth()) {
                    Text("テンプレートを管理する")
                }
            }

            SectionCard("着信識別(実験機能)") {
                if (!viewModel.callScreeningGateway.isSupported) {
                    Text("この端末では利用できません", color = MaterialTheme.colorScheme.error)
                } else {
                    Text(
                        "有効にすると、着信時に登録顧客の情報を通知で表示します。" +
                            "着信のブロックは行いません。端末の「通話スクリーニング」役割と通知の許可が必要です。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    SwitchRow("着信識別を有効にする", settings.callScreeningEnabled) { enabled ->
                        viewModel.setCallScreeningEnabled(enabled)
                        if (enabled) {
                            if (Build.VERSION.SDK_INT >= 33) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            if (!viewModel.callScreeningGateway.hasRole()) {
                                viewModel.callScreeningGateway.roleRequestIntent()
                                    ?.let { roleLauncher.launch(it) }
                            }
                        }
                    }
                    Text(
                        if (viewModel.callScreeningGateway.hasRole()) {
                            "役割の状態: 許可済み"
                        } else {
                            "役割の状態: 未許可(スイッチを入れると許可画面が開きます)"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            SectionCard("バックアップと復元") {
                OutlinedButton(
                    onClick = {
                        showExportWarning = {
                            backupCreateLauncher.launch("store_cti_backup_${LocalDate.now()}.json")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("バックアップを作成(JSON)") }
                OutlinedButton(
                    onClick = { showRestoreWarning = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("バックアップから復元") }
                Text(
                    "復元前の自動バックアップ: ${viewModel.autoBackupCount()}件(アプリ内部に保持)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard("CSV出力(Excel対応・UTF-8 BOM付き)") {
                CsvTarget.entries.forEach { target ->
                    OutlinedButton(
                        onClick = {
                            showExportWarning = {
                                pendingCsvTarget = target
                                csvCreateLauncher.launch(
                                    "${target.fileNamePrefix}_${LocalDate.now()}.csv",
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("${target.label}を出力") }
                }
            }

            SectionCard("権限状態") {
                Text("このアプリは電話・SMS・通話履歴の権限を使用しません。", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "通知: " + if (androidx.core.app.NotificationManagerCompat.from(
                            androidx.compose.ui.platform.LocalContext.current,
                        ).areNotificationsEnabled()
                    ) {
                        "許可済み"
                    } else {
                        "未許可(着信識別の通知に必要)"
                    },
                )
                Text(
                    "通話スクリーニング役割: " + when {
                        !viewModel.callScreeningGateway.isSupported -> "この端末では利用できません"
                        viewModel.callScreeningGateway.hasRole() -> "許可済み"
                        else -> "未許可"
                    },
                )
                Text("通話録音: " + viewModel.recordingProvider.unsupportedReason)
            }

            SectionCard("アプリ情報") {
                Text("店舗CTI (Store CTI) バージョン ${BuildConfig.VERSION_NAME}")
                OutlinedButton(onClick = { showPrivacyPolicy = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("プライバシーポリシー")
                }
                OutlinedButton(onClick = { showAuditLog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("デバッグ情報(監査ログ)")
                }
            }

            if (BuildConfig.DEBUG) {
                SectionCard("開発用(debugビルドのみ)") {
                    OutlinedButton(onClick = viewModel::seedSampleData, modifier = Modifier.fillMaxWidth()) {
                        Text("サンプルデータを投入")
                    }
                }
            }

            SectionCard("データの初期化") {
                OutlinedButton(
                    onClick = { showWipeConfirm1 = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("すべてのデータを削除", color = MaterialTheme.colorScheme.error) }
            }
            Text("", Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
