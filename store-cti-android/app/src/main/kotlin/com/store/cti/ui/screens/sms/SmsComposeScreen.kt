package com.store.cti.ui.screens.sms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.store.cti.core.model.SmsDraftStatus
import com.store.cti.core.sms.SmsTemplateEngine
import com.store.cti.data.local.entity.CustomerEntity
import com.store.cti.data.local.entity.ReservationEntity
import com.store.cti.data.local.entity.SmsDraftEntity
import com.store.cti.data.local.entity.SmsTemplateEntity
import com.store.cti.data.repository.CustomerRepository
import com.store.cti.data.repository.ReservationRepository
import com.store.cti.data.repository.SmsRepository
import com.store.cti.data.settings.SettingsRepository
import com.store.cti.domain.BuildSmsBodyUseCase
import com.store.cti.telephony.LaunchResult
import com.store.cti.telephony.SmsComposerLauncher
import com.store.cti.ui.common.AppTextField
import com.store.cti.ui.common.BigActionButton
import com.store.cti.ui.common.ChipSelector
import com.store.cti.ui.common.ScreenScaffold
import com.store.cti.ui.common.SectionCard
import com.store.cti.util.AppLogger
import com.store.cti.util.newUuid
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SmsComposeUiState(
    val customer: CustomerEntity? = null,
    val reservation: ReservationEntity? = null,
    val phone: String = "",
    val selectedTemplate: SmsTemplateEntity? = null,
    val body: String = "",
    val launched: Boolean = false,
    val currentDraftId: String? = null,
    val staffName: String = "",
)

@HiltViewModel
class SmsComposeViewModel @Inject constructor(
    private val customerRepository: CustomerRepository,
    private val reservationRepository: ReservationRepository,
    private val smsRepository: SmsRepository,
    private val settingsRepository: SettingsRepository,
    private val buildSmsBody: BuildSmsBodyUseCase,
    private val smsLauncher: SmsComposerLauncher,
) : ViewModel() {

    val templates: StateFlow<List<SmsTemplateEntity>> =
        smsRepository.observeEnabledTemplates()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(SmsComposeUiState())
    val uiState: StateFlow<SmsComposeUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun load(customerId: String?, reservationId: String?, initialPhone: String) {
        viewModelScope.launch {
            val reservation = reservationId?.let { reservationRepository.getById(it) }
            val customer = (customerId ?: reservation?.customerId)?.let { customerRepository.getById(it) }
            val settings = settingsRepository.current()
            _uiState.value = _uiState.value.copy(
                customer = customer,
                reservation = reservation,
                phone = customer?.displayPhoneNumber ?: initialPhone,
                staffName = settings.defaultStaffName,
            )
        }
    }

    fun setPhone(value: String) {
        _uiState.value = _uiState.value.copy(phone = value)
    }

    fun setBody(value: String) {
        _uiState.value = _uiState.value.copy(body = value)
    }

    fun selectTemplate(template: SmsTemplateEntity) {
        viewModelScope.launch {
            val state = _uiState.value
            val body = buildSmsBody(template.body, state.customer, state.reservation)
            _uiState.value = state.copy(selectedTemplate = template, body = body)
        }
    }

    /** 下書きを保存して標準SMSアプリを開く */
    fun launchSmsApp() {
        val state = _uiState.value
        if (state.body.isBlank()) {
            viewModelScope.launch { _messages.emit("本文が空です") }
            return
        }
        viewModelScope.launch {
            try {
                val draft = smsRepository.saveDraft(
                    SmsDraftEntity(
                        id = state.currentDraftId ?: newUuid(),
                        customerId = state.customer?.id,
                        reservationId = state.reservation?.id,
                        displayPhoneNumber = state.phone,
                        templateId = state.selectedTemplate?.id,
                        templateName = state.selectedTemplate?.name ?: "",
                        body = state.body,
                        status = SmsDraftStatus.DRAFT.name,
                        staffName = state.staffName,
                    ),
                    isNew = state.currentDraftId == null,
                )
                when (val result = smsLauncher.openSmsComposer(state.phone, state.body)) {
                    is LaunchResult.Success -> {
                        smsRepository.updateDraftStatus(draft.id, SmsDraftStatus.LAUNCHED)
                        _uiState.value = _uiState.value.copy(launched = true, currentDraftId = draft.id)
                    }
                    is LaunchResult.Failure -> _messages.emit(result.userMessage)
                }
            } catch (t: Throwable) {
                AppLogger.e("sms draft save failed", t)
                _messages.emit("SMS作成履歴の保存に失敗しました")
            }
        }
    }

    /** スタッフが送信を確認したことを記録する(実送信の成否はアプリからは検知できない) */
    fun markConfirmed() {
        val draftId = _uiState.value.currentDraftId ?: return
        viewModelScope.launch {
            smsRepository.updateDraftStatus(draftId, SmsDraftStatus.CONFIRMED)
            _messages.emit("スタッフ確認済みとして記録しました")
        }
    }
}

@Composable
fun SmsComposeScreen(
    customerId: String?,
    reservationId: String?,
    initialPhone: String,
    onBack: () -> Unit,
    viewModel: SmsComposeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(customerId, reservationId) {
        viewModel.load(customerId, reservationId, initialPhone)
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    ScreenScaffold(
        title = "SMSを作成",
        onBack = onBack,
        snackbarHostState = snackbar,
        bottomBar = {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BigActionButton(
                    "SMSアプリを開く(送信はスタッフ操作)",
                    viewModel::launchSmsApp,
                    Modifier.fillMaxWidth(),
                )
                if (state.launched) {
                    OutlinedButton(onClick = viewModel::markConfirmed, modifier = Modifier.fillMaxWidth()) {
                        Text("送信をスタッフ確認済みにする")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.customer != null) {
                SectionCard("宛先") {
                    Text("${state.customer!!.name} 様")
                }
            }
            AppTextField(state.phone, viewModel::setPhone, "宛先電話番号", placeholder = "090-1234-5678")

            if (templates.isNotEmpty()) {
                ChipSelector(
                    label = "テンプレート",
                    options = templates,
                    selected = state.selectedTemplate,
                    optionLabel = { it.name },
                    onSelect = viewModel::selectTemplate,
                )
            } else {
                Text(
                    "有効なテンプレートがありません(設定 > SMSテンプレートから追加できます)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AppTextField(state.body, viewModel::setBody, "本文(送信前に編集できます)", singleLine = false)
            Text(
                "${state.body.length}文字",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val remaining = SmsTemplateEngine.remainingPlaceholders(state.body)
            if (remaining.isNotEmpty()) {
                Text(
                    "⚠ 未置換の差し込み項目が残っています: ${remaining.joinToString(" ")}",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                "送信は標準SMSアプリで行います。送信されたかどうかはこのアプリでは確認できないため、" +
                    "送信後に「スタッフ確認済み」を押して記録してください。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("", Modifier.padding(bottom = 16.dp))
        }
    }
}
