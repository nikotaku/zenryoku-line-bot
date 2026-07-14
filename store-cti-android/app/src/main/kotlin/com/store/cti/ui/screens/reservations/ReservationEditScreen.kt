package com.store.cti.ui.screens.reservations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.store.cti.core.model.ReservationChannel
import com.store.cti.core.model.ReservationStatus
import com.store.cti.core.reservation.OverlapConflict
import com.store.cti.core.validation.ReservationTimeValidator
import com.store.cti.core.validation.ValidationResult
import com.store.cti.data.local.entity.CustomerEntity
import com.store.cti.data.local.entity.ReservationEntity
import com.store.cti.data.local.entity.StaffEntity
import com.store.cti.data.repository.CustomerRepository
import com.store.cti.data.repository.StaffRepository
import com.store.cti.data.settings.SettingsRepository
import com.store.cti.domain.SaveReservationUseCase
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class ReservationEditUiState(
    val isNew: Boolean = true,
    val entity: ReservationEntity = ReservationEntity(id = ""),
    val customer: CustomerEntity? = null,
    val staffList: List<StaffEntity> = emptyList(),
    val priceInput: String = "",
    val dateError: String = "",
    val timeError: String = "",
    val conflicts: List<OverlapConflict> = emptyList(),
    val showConflictDialog: Boolean = false,
    val savedId: String? = null,
    val loading: Boolean = true,
)

sealed class ReservationEditEvent {
    data class Message(val text: String) : ReservationEditEvent()
}

@HiltViewModel
class ReservationEditViewModel @Inject constructor(
    private val saveReservationUseCase: SaveReservationUseCase,
    private val customerRepository: CustomerRepository,
    private val staffRepository: StaffRepository,
    private val settingsRepository: SettingsRepository,
    private val reservationRepository: com.store.cti.data.repository.ReservationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReservationEditUiState())
    val uiState: StateFlow<ReservationEditUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ReservationEditEvent>()
    val events: SharedFlow<ReservationEditEvent> = _events.asSharedFlow()

    /** 顧客選択ダイアログ用 */
    val allCustomers: StateFlow<List<CustomerEntity>> =
        customerRepository.observeActive().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    fun load(reservationId: String?, customerId: String?) {
        viewModelScope.launch {
            val staff = staffRepository.observeUsable().first()
            if (reservationId != null) {
                val existing = reservationRepository.getById(reservationId)
                if (existing == null) {
                    _events.emit(ReservationEditEvent.Message("予約が見つかりません"))
                    return@launch
                }
                _uiState.value = ReservationEditUiState(
                    isNew = false,
                    entity = existing,
                    customer = existing.customerId?.let { customerRepository.getById(it) },
                    staffList = staff,
                    priceInput = existing.price?.toString() ?: "",
                    loading = false,
                )
            } else {
                val settings = settingsRepository.current()
                val customer = customerId?.let { customerRepository.getById(it) }
                val start = LocalTime.now().plusHours(1).withMinute(0)
                val end = start.plusMinutes(settings.defaultSlotMinutes.toLong())
                _uiState.value = ReservationEditUiState(
                    isNew = true,
                    entity = ReservationEntity(
                        id = newUuid(),
                        customerId = customer?.id,
                        date = LocalDate.now().toString(),
                        startTime = "%02d:%02d".format(start.hour, start.minute),
                        endTime = "%02d:%02d".format(end.hour, end.minute),
                        createdByStaffName = settings.defaultStaffName,
                        staffName = settings.defaultStaffName,
                    ),
                    customer = customer,
                    staffList = staff,
                    loading = false,
                )
            }
        }
    }

    fun update(transform: (ReservationEntity) -> ReservationEntity) {
        _uiState.value = _uiState.value.copy(
            entity = transform(_uiState.value.entity),
            dateError = "", timeError = "",
        )
    }

    fun setPriceInput(value: String) {
        val digits = value.filter { it.isDigit() }
        _uiState.value = _uiState.value.copy(
            priceInput = digits,
            entity = _uiState.value.entity.copy(price = digits.toLongOrNull()),
        )
    }

    fun selectCustomer(customer: CustomerEntity?) {
        _uiState.value = _uiState.value.copy(
            customer = customer,
            entity = _uiState.value.entity.copy(customerId = customer?.id),
        )
    }

    /** 保存前検証 → 重複があれば確認ダイアログ、なければ保存 */
    fun requestSave() {
        val state = _uiState.value
        val entity = state.entity

        if (runCatching { LocalDate.parse(entity.date) }.isFailure) {
            _uiState.value = state.copy(dateError = "日付は 2026-07-20 の形式で入力してください")
            return
        }
        val start = runCatching { LocalTime.parse(entity.startTime) }.getOrNull()
        val end = runCatching { LocalTime.parse(entity.endTime) }.getOrNull()
        if (start == null || end == null) {
            _uiState.value = state.copy(timeError = "時間は 14:00 の形式で入力してください")
            return
        }
        val timeResult = ReservationTimeValidator.validate(start, end)
        if (timeResult is ValidationResult.Error) {
            _uiState.value = state.copy(timeError = timeResult.message)
            return
        }

        viewModelScope.launch {
            val conflicts = saveReservationUseCase.findConflicts(entity)
            if (conflicts.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(conflicts = conflicts, showConflictDialog = true)
            } else {
                doSave()
            }
        }
    }

    fun confirmSaveDespiteConflicts() {
        _uiState.value = _uiState.value.copy(showConflictDialog = false)
        viewModelScope.launch { doSave() }
    }

    fun dismissConflictDialog() {
        _uiState.value = _uiState.value.copy(showConflictDialog = false)
    }

    private suspend fun doSave() {
        val state = _uiState.value
        try {
            val saved = saveReservationUseCase(state.entity, state.isNew)
            _uiState.value = _uiState.value.copy(savedId = saved.id, isNew = false)
        } catch (t: Throwable) {
            AppLogger.e("reservation save failed", t)
            _events.emit(ReservationEditEvent.Message("保存に失敗しました。空き容量を確認してください"))
        }
    }

    fun softDelete(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                reservationRepository.softDelete(_uiState.value.entity.id)
                onDone()
            } catch (t: Throwable) {
                _events.emit(ReservationEditEvent.Message("削除に失敗しました"))
            }
        }
    }
}

@Composable
fun ReservationEditScreen(
    reservationId: String?,
    customerId: String?,
    onBack: () -> Unit,
    onComposeSms: (String?, String) -> Unit,
    viewModel: ReservationEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showCustomerPicker by remember { mutableStateOf(false) }

    LaunchedEffect(reservationId, customerId) { viewModel.load(reservationId, customerId) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ReservationEditEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }

    if (state.showConflictDialog) {
        ConflictWarningDialog(
            conflicts = state.conflicts,
            onConfirm = viewModel::confirmSaveDespiteConflicts,
            onDismiss = viewModel::dismissConflictDialog,
        )
    }

    if (state.savedId != null) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("予約を保存しました") },
            text = { Text("予約確定のSMSを作成しますか?") },
            confirmButton = {
                TextButton(onClick = {
                    onComposeSms(state.entity.customerId, state.savedId!!)
                }) { Text("SMSを作成") }
            },
            dismissButton = {
                TextButton(onClick = onBack) { Text("閉じる") }
            },
        )
    }

    if (showCustomerPicker) {
        CustomerPickerDialog(
            customers = viewModel.allCustomers.collectAsState().value,
            onSelect = {
                viewModel.selectCustomer(it)
                showCustomerPicker = false
            },
            onDismiss = { showCustomerPicker = false },
        )
    }

    val entity = state.entity
    ScreenScaffold(
        title = if (state.isNew) "予約を登録" else "予約を編集",
        onBack = onBack,
        snackbarHostState = snackbar,
        bottomBar = {
            BigActionButton("保存する", viewModel::requestSave, Modifier.fillMaxWidth().padding(12.dp))
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
            SectionCard("顧客") {
                Text(state.customer?.let { "${it.name} 様 (${it.displayPhoneNumber})" } ?: "顧客未設定")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showCustomerPicker = true }) { Text("顧客を選択") }
                    if (state.customer != null) {
                        OutlinedButton(onClick = { viewModel.selectCustomer(null) }) { Text("解除") }
                    }
                }
            }

            AppTextField(entity.date, { v -> viewModel.update { it.copy(date = v) } }, "予約日",
                placeholder = "2026-07-20", isError = state.dateError.isNotEmpty(), supportingText = state.dateError)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(entity.startTime, { v -> viewModel.update { it.copy(startTime = v) } }, "開始時間",
                    Modifier.weight(1f), placeholder = "14:00", isError = state.timeError.isNotEmpty())
                AppTextField(entity.endTime, { v -> viewModel.update { it.copy(endTime = v) } }, "終了時間",
                    Modifier.weight(1f), placeholder = "15:00", isError = state.timeError.isNotEmpty())
            }
            if (state.timeError.isNotEmpty()) {
                Text(state.timeError, color = MaterialTheme.colorScheme.error)
            }

            if (state.staffList.isNotEmpty()) {
                ChipSelector(
                    label = "担当者",
                    options = state.staffList.map { it.name },
                    selected = entity.staffName.takeIf { it.isNotBlank() },
                    optionLabel = { it },
                    onSelect = { v -> viewModel.update { it.copy(staffName = v) } },
                )
            } else {
                AppTextField(entity.staffName, { v -> viewModel.update { it.copy(staffName = v) } }, "担当者")
            }

            AppTextField(entity.courseName, { v -> viewModel.update { it.copy(courseName = v) } }, "コース名")
            AppTextField(state.priceInput, viewModel::setPriceInput, "料金(円)", placeholder = "8000")
            AppTextField(entity.room, { v -> viewModel.update { it.copy(room = v) } }, "部屋")

            ChipSelector(
                label = "予約経路",
                options = ReservationChannel.entries,
                selected = entity.channelEnum,
                optionLabel = { it.label },
                onSelect = { v -> viewModel.update { it.copy(channel = v.name) } },
            )
            ChipSelector(
                label = "予約状態",
                options = ReservationStatus.entries,
                selected = entity.statusEnum,
                optionLabel = { it.label },
                onSelect = { v -> viewModel.update { it.copy(status = v.name) } },
            )

            AppTextField(entity.requests, { v -> viewModel.update { it.copy(requests = v) } }, "要望", singleLine = false)
            AppTextField(entity.cautionNotes, { v -> viewModel.update { it.copy(cautionNotes = v) } }, "注意事項", singleLine = false)

            if (!state.isNew && state.savedId == null) {
                OutlinedButton(
                    onClick = { viewModel.softDelete(onBack) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("この予約を削除する", color = MaterialTheme.colorScheme.error) }
            }
            Text("", Modifier.padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun ConflictWarningDialog(
    conflicts: List<OverlapConflict>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⚠ 予約時間が重複しています") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                conflicts.forEach { c ->
                    val kind = buildList {
                        if (c.sameStaff) add("同じ担当者")
                        if (c.sameRoom) add("同じ部屋")
                    }.joinToString("・")
                    Text(
                        "${c.other.startTime}〜${c.other.endTime} " +
                            "(${c.other.staffName ?: "-"} / ${c.other.room ?: "-"}): $kind",
                    )
                }
                Text("このまま保存しますか?")
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("重複を承知で保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("戻って修正") }
        },
    )
}

@Composable
private fun CustomerPickerDialog(
    customers: List<CustomerEntity>,
    onSelect: (CustomerEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = customers.filter {
        query.isBlank() || it.name.contains(query) || it.kana.contains(query) ||
            it.normalizedPhoneNumber.contains(query.filter { ch -> ch.isDigit() }.ifEmpty { "#" })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("顧客を選択") },
        text = {
            Column {
                AppTextField(query, { query = it }, "氏名・電話番号で検索")
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(filtered, key = { it.id }) { c ->
                        Text(
                            "${c.name} (${c.displayPhoneNumber})",
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(c) }
                                .padding(vertical = 10.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}
