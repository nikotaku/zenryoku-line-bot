package com.store.cti.ui.screens.interactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.store.cti.core.model.CallDirection
import com.store.cti.core.model.CallResult
import com.store.cti.data.local.entity.CallInteractionEntity
import com.store.cti.data.local.entity.CustomerEntity
import com.store.cti.data.local.entity.StaffEntity
import com.store.cti.data.repository.CallInteractionRepository
import com.store.cti.data.repository.CustomerRepository
import com.store.cti.data.repository.StaffRepository
import com.store.cti.data.settings.SettingsRepository
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// STRICT + "uuuu" で存在しない日付(2/30 など)を確実に弾く
private val INPUT_FORMAT =
    DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
        .withResolverStyle(java.time.format.ResolverStyle.STRICT)

/** "yyyy-MM-dd HH:mm" ←→ ISO("yyyy-MM-ddTHH:mm") の相互変換 */
internal fun isoToInput(iso: String?): String =
    iso?.replace('T', ' ')?.take(16).orEmpty()

internal fun inputToIso(input: String): String? {
    if (input.isBlank()) return null
    return runCatching { LocalDateTime.parse(input.trim(), INPUT_FORMAT).toString() }.getOrNull()
}

data class InteractionEditUiState(
    val isNew: Boolean = true,
    val entity: CallInteractionEntity = CallInteractionEntity(id = ""),
    val occurredAtInput: String = "",
    val nextActionAtInput: String = "",
    val customer: CustomerEntity? = null,
    val staffList: List<StaffEntity> = emptyList(),
    val occurredAtError: String = "",
    val nextActionAtError: String = "",
    val loading: Boolean = true,
)

sealed class InteractionEditEvent {
    data object Saved : InteractionEditEvent()
    data class Message(val text: String) : InteractionEditEvent()
}

@HiltViewModel
class InteractionEditViewModel @Inject constructor(
    private val interactionRepository: CallInteractionRepository,
    private val customerRepository: CustomerRepository,
    private val staffRepository: StaffRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InteractionEditUiState())
    val uiState: StateFlow<InteractionEditUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<InteractionEditEvent>()
    val events: SharedFlow<InteractionEditEvent> = _events.asSharedFlow()

    fun load(interactionId: String?, customerId: String?, initialPhone: String) {
        viewModelScope.launch {
            val staff = staffRepository.observeUsable().first()
            if (interactionId != null) {
                val existing = interactionRepository.getById(interactionId)
                if (existing == null) {
                    _events.emit(InteractionEditEvent.Message("対応履歴が見つかりません"))
                    return@launch
                }
                _uiState.value = InteractionEditUiState(
                    isNew = false,
                    entity = existing,
                    occurredAtInput = isoToInput(existing.occurredAt),
                    nextActionAtInput = isoToInput(existing.nextActionAt),
                    customer = existing.customerId?.let { customerRepository.getById(it) },
                    staffList = staff,
                    loading = false,
                )
            } else {
                val settings = settingsRepository.current()
                val customer = customerId?.let { customerRepository.getById(it) }
                val now = LocalDateTime.now().withSecond(0).withNano(0)
                _uiState.value = InteractionEditUiState(
                    isNew = true,
                    entity = CallInteractionEntity(
                        id = newUuid(),
                        customerId = customer?.id,
                        displayPhoneNumber = customer?.displayPhoneNumber ?: initialPhone,
                        occurredAt = now.toString(),
                        staffName = settings.defaultStaffName,
                    ),
                    occurredAtInput = now.format(INPUT_FORMAT),
                    customer = customer,
                    staffList = staff,
                    loading = false,
                )
            }
        }
    }

    fun update(transform: (CallInteractionEntity) -> CallInteractionEntity) {
        _uiState.value = _uiState.value.copy(entity = transform(_uiState.value.entity))
    }

    fun setOccurredAtInput(value: String) {
        _uiState.value = _uiState.value.copy(occurredAtInput = value, occurredAtError = "")
    }

    fun setNextActionAtInput(value: String) {
        _uiState.value = _uiState.value.copy(nextActionAtInput = value, nextActionAtError = "")
    }

    fun save() {
        val state = _uiState.value
        val occurredIso = inputToIso(state.occurredAtInput)
        if (occurredIso == null) {
            _uiState.value = state.copy(occurredAtError = "日時は 2026-07-14 15:00 の形式で入力してください")
            return
        }
        val nextIso = if (state.nextActionAtInput.isBlank()) {
            null
        } else {
            inputToIso(state.nextActionAtInput) ?: run {
                _uiState.value = state.copy(nextActionAtError = "日時は 2026-07-14 15:00 の形式で入力してください")
                return
            }
        }
        viewModelScope.launch {
            try {
                interactionRepository.save(
                    state.entity.copy(occurredAt = occurredIso, nextActionAt = nextIso),
                    state.isNew,
                )
                _events.emit(InteractionEditEvent.Saved)
            } catch (t: Throwable) {
                AppLogger.e("interaction save failed", t)
                _events.emit(InteractionEditEvent.Message("保存に失敗しました。空き容量を確認してください"))
            }
        }
    }
}

@Composable
fun InteractionEditScreen(
    interactionId: String?,
    customerId: String?,
    initialPhone: String,
    onBack: () -> Unit,
    onCreateReservation: (String?) -> Unit,
    viewModel: InteractionEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(interactionId, customerId) {
        viewModel.load(interactionId, customerId, initialPhone)
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is InteractionEditEvent.Saved -> onBack()
                is InteractionEditEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }

    val entity = state.entity
    ScreenScaffold(
        title = if (state.isNew) "対応履歴を登録" else "対応履歴を編集",
        onBack = onBack,
        snackbarHostState = snackbar,
        bottomBar = {
            BigActionButton(
                "保存する",
                viewModel::save,
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            )
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
                SectionCard("対応相手") {
                    Text("${state.customer!!.name} 様 (${state.customer!!.displayPhoneNumber})")
                }
            } else {
                AppTextField(
                    entity.displayPhoneNumber,
                    { v -> viewModel.update { it.copy(displayPhoneNumber = v) } },
                    "電話番号(未登録の相手)",
                )
            }

            AppTextField(
                state.occurredAtInput,
                viewModel::setOccurredAtInput,
                "対応日時",
                placeholder = "2026-07-14 15:00",
                isError = state.occurredAtError.isNotEmpty(),
                supportingText = state.occurredAtError,
            )

            ChipSelector(
                label = "着信・発信",
                options = CallDirection.entries,
                selected = entity.directionEnum,
                optionLabel = { it.label },
                onSelect = { v -> viewModel.update { it.copy(direction = v.name) } },
            )
            ChipSelector(
                label = "電話に出たか",
                options = listOf(true, false),
                selected = entity.answered,
                optionLabel = { if (it) "出た" else "出ていない" },
                onSelect = { v -> viewModel.update { it.copy(answered = v) } },
            )
            ChipSelector(
                label = "対応結果",
                options = CallResult.entries,
                selected = entity.resultEnum,
                optionLabel = { it.label },
                onSelect = { v -> viewModel.update { it.copy(result = v.name) } },
            )

            AppTextField(entity.inquiry, { v -> viewModel.update { it.copy(inquiry = v) } }, "問い合わせ内容", singleLine = false)
            AppTextField(entity.desiredDateTime, { v -> viewModel.update { it.copy(desiredDateTime = v) } }, "希望日時", placeholder = "例: 来週土曜の午後")
            AppTextField(entity.desiredStaffName, { v -> viewModel.update { it.copy(desiredStaffName = v) } }, "希望担当者")
            AppTextField(entity.guidanceGiven, { v -> viewModel.update { it.copy(guidanceGiven = v) } }, "案内した内容", singleLine = false)

            SwitchRow("予約成立", entity.reservationCreated) { v ->
                viewModel.update { it.copy(reservationCreated = v) }
            }
            SwitchRow("折り返しが必要", entity.callbackRequired) { v ->
                viewModel.update { it.copy(callbackRequired = v) }
            }
            if (entity.callbackRequired) {
                SwitchRow("折り返し済み", entity.callbackDone) { v ->
                    viewModel.update { it.copy(callbackDone = v) }
                }
            }

            AppTextField(
                state.nextActionAtInput,
                viewModel::setNextActionAtInput,
                "次回対応予定日時(任意)",
                placeholder = "2026-07-15 10:00",
                isError = state.nextActionAtError.isNotEmpty(),
                supportingText = state.nextActionAtError,
            )

            if (state.staffList.isNotEmpty()) {
                ChipSelector(
                    label = "スタッフ名",
                    options = state.staffList.map { it.name },
                    selected = entity.staffName.takeIf { it.isNotBlank() },
                    optionLabel = { it },
                    onSelect = { v -> viewModel.update { it.copy(staffName = v) } },
                )
            } else {
                AppTextField(entity.staffName, { v -> viewModel.update { it.copy(staffName = v) } }, "スタッフ名")
            }

            AppTextField(entity.memo, { v -> viewModel.update { it.copy(memo = v) } }, "自由メモ", singleLine = false)

            if (entity.reservationCreated) {
                androidx.compose.material3.OutlinedButton(
                    onClick = { onCreateReservation(entity.customerId) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("続けて予約を登録する") }
                Text(
                    "※ 先にこの画面を保存してから予約登録へ進むことをおすすめします",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("", Modifier.padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
