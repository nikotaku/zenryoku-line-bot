package com.store.cti.ui.screens.customers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.store.cti.core.model.CustomerCategory
import com.store.cti.core.phone.PhoneNumberUtils
import com.store.cti.core.validation.PhoneNumberValidator
import com.store.cti.core.validation.ValidationResult
import com.store.cti.data.local.entity.CustomerEntity
import com.store.cti.data.repository.CustomerRepository
import com.store.cti.domain.SaveCustomerUseCase
import com.store.cti.ui.common.AppTextField
import com.store.cti.ui.common.BigActionButton
import com.store.cti.ui.common.ChipSelector
import com.store.cti.ui.common.ConfirmDialog
import com.store.cti.ui.common.ScreenScaffold
import com.store.cti.util.AppLogger
import com.store.cti.util.newUuid
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomerEditUiState(
    val isNew: Boolean = true,
    val entity: CustomerEntity = CustomerEntity(id = ""),
    val phoneError: String = "",
    val nameError: String = "",
    val duplicateWarning: List<String> = emptyList(),
    val loading: Boolean = true,
)

sealed class CustomerEditEvent {
    data class Saved(val customerId: String) : CustomerEditEvent()
    data class Message(val text: String) : CustomerEditEvent()
}

@HiltViewModel
class CustomerEditViewModel @Inject constructor(
    private val customerRepository: CustomerRepository,
    private val saveCustomerUseCase: SaveCustomerUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomerEditUiState())
    val uiState: StateFlow<CustomerEditUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CustomerEditEvent>()
    val events: SharedFlow<CustomerEditEvent> = _events.asSharedFlow()

    fun load(customerId: String?, initialPhone: String) {
        viewModelScope.launch {
            if (customerId == null) {
                _uiState.value = CustomerEditUiState(
                    isNew = true,
                    entity = CustomerEntity(id = newUuid(), displayPhoneNumber = initialPhone),
                    loading = false,
                )
            } else {
                val existing = customerRepository.getById(customerId)
                if (existing == null) {
                    _events.emit(CustomerEditEvent.Message("顧客が見つかりませんでした"))
                } else {
                    _uiState.value = CustomerEditUiState(isNew = false, entity = existing, loading = false)
                }
            }
        }
    }

    fun update(transform: (CustomerEntity) -> CustomerEntity) {
        _uiState.value = _uiState.value.copy(entity = transform(_uiState.value.entity))
    }

    /** 保存。重複警告が未確認の場合は警告を出して中断する。 */
    fun save(confirmedDuplicates: Boolean) {
        val state = _uiState.value
        val entity = state.entity

        val nameError = if (entity.name.isBlank()) "氏名を入力してください" else ""
        val phoneResult = PhoneNumberValidator.validate(entity.displayPhoneNumber, required = true)
        val phoneError = (phoneResult as? ValidationResult.Error)?.message ?: ""
        if (nameError.isNotEmpty() || phoneError.isNotEmpty()) {
            _uiState.value = state.copy(nameError = nameError, phoneError = phoneError)
            return
        }

        viewModelScope.launch {
            try {
                if (!confirmedDuplicates) {
                    val duplicates = saveCustomerUseCase.findDuplicateNames(entity.displayPhoneNumber, entity.id)
                    if (duplicates.isNotEmpty()) {
                        _uiState.value = state.copy(
                            nameError = "", phoneError = "", duplicateWarning = duplicates,
                        )
                        return@launch
                    }
                }
                val result = saveCustomerUseCase(entity, state.isNew)
                _events.emit(CustomerEditEvent.Saved(result.saved.id))
            } catch (t: Throwable) {
                AppLogger.e("customer save failed", t)
                _events.emit(CustomerEditEvent.Message("保存に失敗しました。空き容量を確認してください"))
            }
        }
    }

    fun dismissDuplicateWarning() {
        _uiState.value = _uiState.value.copy(duplicateWarning = emptyList())
    }

    /** 論理削除 */
    fun softDelete() {
        val id = _uiState.value.entity.id
        viewModelScope.launch {
            try {
                customerRepository.softDelete(id)
                _events.emit(CustomerEditEvent.Message("顧客を削除しました(復元はバックアップから可能です)"))
                _events.emit(CustomerEditEvent.Saved(""))
            } catch (t: Throwable) {
                AppLogger.e("customer delete failed", t)
                _events.emit(CustomerEditEvent.Message("削除に失敗しました"))
            }
        }
    }
}

@Composable
fun CustomerEditScreen(
    customerId: String?,
    initialPhone: String,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: CustomerEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    androidx.compose.runtime.LaunchedEffect(customerId) {
        viewModel.load(customerId, initialPhone)
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CustomerEditEvent.Saved ->
                    if (event.customerId.isEmpty()) onBack() else onSaved(event.customerId)
                is CustomerEditEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }

    if (state.duplicateWarning.isNotEmpty()) {
        ConfirmDialog(
            title = "電話番号が重複しています",
            text = "同じ電話番号の顧客が既に登録されています:\n" +
                state.duplicateWarning.joinToString("、") +
                "\n\nこのまま保存しますか?",
            confirmLabel = "このまま保存",
            onConfirm = {
                viewModel.dismissDuplicateWarning()
                viewModel.save(confirmedDuplicates = true)
            },
            onDismiss = { viewModel.dismissDuplicateWarning() },
        )
    }

    val entity = state.entity
    ScreenScaffold(
        title = if (state.isNew) "新規顧客登録" else "顧客情報を編集",
        onBack = onBack,
        snackbarHostState = snackbar,
        bottomBar = {
            BigActionButton(
                "保存する",
                { viewModel.save(confirmedDuplicates = false) },
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
            AppTextField(entity.name, { v -> viewModel.update { it.copy(name = v) } }, "氏名(必須)",
                isError = state.nameError.isNotEmpty(), supportingText = state.nameError)
            AppTextField(entity.kana, { v -> viewModel.update { it.copy(kana = v) } }, "ふりがな")
            AppTextField(
                entity.displayPhoneNumber,
                { v -> viewModel.update { it.copy(displayPhoneNumber = v) } },
                "電話番号(必須)",
                placeholder = "090-1234-5678",
                isError = state.phoneError.isNotEmpty(),
                supportingText = state.phoneError.ifEmpty {
                    PhoneNumberUtils.normalize(entity.displayPhoneNumber)
                        .takeIf { it.isNotEmpty() }?.let { "検索用: $it" } ?: ""
                },
            )
            AppTextField(entity.displayPhoneNumber2, { v -> viewModel.update { it.copy(displayPhoneNumber2 = v) } }, "予備電話番号")
            AppTextField(entity.customerNumber, { v -> viewModel.update { it.copy(customerNumber = v) } }, "顧客番号")
            AppTextField(entity.lineName, { v -> viewModel.update { it.copy(lineName = v) } }, "LINE名")

            ChipSelector(
                label = "顧客区分",
                options = CustomerCategory.entries,
                selected = entity.categoryEnum,
                optionLabel = { it.label },
                onSelect = { v -> viewModel.update { it.copy(category = v.name) } },
            )

            AppTextField(entity.firstVisitDate ?: "", { v -> viewModel.update { it.copy(firstVisitDate = v.ifBlank { null }) } },
                "初回来店日", placeholder = "2026-01-31")
            AppTextField(entity.lastVisitDate ?: "", { v -> viewModel.update { it.copy(lastVisitDate = v.ifBlank { null }) } },
                "最終来店日", placeholder = "2026-07-01")
            AppTextField(
                entity.visitCount.toString(),
                { v -> viewModel.update { it.copy(visitCount = v.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0) } },
                "利用回数",
            )
            AppTextField(entity.lastStaffName, { v -> viewModel.update { it.copy(lastStaffName = v) } }, "前回担当者")
            AppTextField(entity.preferredStaffName, { v -> viewModel.update { it.copy(preferredStaffName = v) } }, "希望担当者")
            AppTextField(entity.cautionNotes, { v -> viewModel.update { it.copy(cautionNotes = v) } }, "注意事項", singleLine = false)
            AppTextField(entity.servicePreferences, { v -> viewModel.update { it.copy(servicePreferences = v) } }, "接客上の好み", singleLine = false)
            AppTextField(entity.prohibitedActions, { v -> viewModel.update { it.copy(prohibitedActions = v) } }, "禁止事項", singleLine = false)
            AppTextField(entity.memo, { v -> viewModel.update { it.copy(memo = v) } }, "メモ", singleLine = false)

            if (!state.isNew) {
                DeleteCustomerButton(onDelete = viewModel::softDelete)
            }
            Text("", Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun DeleteCustomerButton(onDelete: () -> Unit) {
    var confirming by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.material3.OutlinedButton(
        onClick = { confirming = true },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("この顧客を削除する", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
    }
    if (confirming) {
        ConfirmDialog(
            title = "顧客を削除しますか?",
            text = "削除しても内部データは論理削除として保持され、完全には消えません。一覧や検索には表示されなくなります。",
            confirmLabel = "削除する",
            destructive = true,
            onConfirm = {
                confirming = false
                onDelete()
            },
            onDismiss = { confirming = false },
        )
    }
}
