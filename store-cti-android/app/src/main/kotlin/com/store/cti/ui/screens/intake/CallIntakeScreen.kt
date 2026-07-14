package com.store.cti.ui.screens.intake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.store.cti.core.model.CallResult
import com.store.cti.core.phone.PhoneNumberUtils
import com.store.cti.data.local.entity.CallInteractionEntity
import com.store.cti.data.local.entity.CustomerEntity
import com.store.cti.data.repository.CallInteractionRepository
import com.store.cti.data.repository.CustomerRepository
import com.store.cti.data.settings.AppSettings
import com.store.cti.data.settings.SettingsRepository
import com.store.cti.telephony.LaunchResult
import com.store.cti.telephony.OutboundCallLauncher
import com.store.cti.ui.common.AppTextField
import com.store.cti.ui.common.BigActionButton
import com.store.cti.ui.common.CategoryBadge
import com.store.cti.ui.common.ScreenScaffold
import com.store.cti.ui.common.SectionCard
import com.store.cti.ui.common.formatPhoneForUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CallIntakeUiState(
    val phoneInput: String = "",
    val searched: Boolean = false,
    val matchedCustomers: List<CustomerEntity> = emptyList(),
    val recentInteractions: List<CallInteractionEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val inputError: String = "",
)

@HiltViewModel
class CallIntakeViewModel @Inject constructor(
    private val customerRepository: CustomerRepository,
    private val interactionRepository: CallInteractionRepository,
    private val settingsRepository: SettingsRepository,
    private val callLauncher: OutboundCallLauncher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CallIntakeUiState())
    val uiState: StateFlow<CallIntakeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(settings = settingsRepository.settings.first())
        }
    }

    fun setPhoneInput(value: String) {
        _uiState.value = _uiState.value.copy(phoneInput = value, inputError = "")
    }

    /** A/B: 貼り付け・手入力による顧客検索 */
    fun search() {
        val raw = _uiState.value.phoneInput
        val normalized = PhoneNumberUtils.normalize(raw)
        if (normalized.length < 4) {
            _uiState.value = _uiState.value.copy(
                inputError = "電話番号を4桁以上入力してください", searched = false,
            )
            return
        }
        viewModelScope.launch {
            val matches = customerRepository.findByPhone(normalized)
            val recent = interactionRepository.recentFor(
                customerId = matches.firstOrNull()?.id,
                rawPhone = normalized,
                limit = 3,
            )
            _uiState.value = _uiState.value.copy(
                searched = true,
                matchedCustomers = matches,
                recentInteractions = recent,
            )
        }
    }

    fun dial(): LaunchResult = callLauncher.openDialer(_uiState.value.phoneInput)
}

@Composable
fun CallIntakeScreen(
    initialPhone: String,
    onBack: () -> Unit,
    onRegisterNew: (String) -> Unit,
    onOpenCustomer: (String) -> Unit,
    onAddInteraction: (String?, String) -> Unit,
    viewModel: CallIntakeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(initialPhone) {
        if (initialPhone.isNotBlank()) {
            viewModel.setPhoneInput(initialPhone)
            viewModel.search()
        }
    }

    ScreenScaffold(title = "着信・通話後対応", onBack = onBack, snackbarHostState = snackbar) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "着信番号を入力または貼り付けて検索してください。\n" +
                    "(この端末設定では着信番号の自動取得は行いません)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppTextField(
                value = state.phoneInput,
                onValueChange = viewModel::setPhoneInput,
                label = "電話番号",
                placeholder = "090-1234-5678",
                isError = state.inputError.isNotEmpty(),
                supportingText = state.inputError,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigActionButton(
                    "貼り付け",
                    {
                        val text = clipboard.getText()?.text.orEmpty()
                        val phone = PhoneNumberUtils.extractFirstPhoneNumber(text)
                            ?: PhoneNumberUtils.normalize(text)
                        if (phone.isBlank()) {
                            scope.launch { snackbar.showSnackbar("クリップボードに電話番号が見つかりません") }
                        } else {
                            viewModel.setPhoneInput(phone)
                            viewModel.search()
                        }
                    },
                    Modifier.weight(1f),
                    Icons.Default.ContentPaste,
                )
                BigActionButton("検索", viewModel::search, Modifier.weight(1f), Icons.Default.Search)
            }

            if (state.searched) {
                if (state.matchedCustomers.isEmpty()) {
                    SectionCard("未登録の番号") {
                        Text("この電話番号の顧客は登録されていません。")
                        BigActionButton(
                            "新規顧客として登録",
                            { onRegisterNew(state.phoneInput) },
                            Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(
                            onClick = { onAddInteraction(null, state.phoneInput) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("顧客登録せずに対応履歴だけ残す") }
                    }
                } else {
                    state.matchedCustomers.forEach { customer ->
                        CustomerIntakeCard(
                            customer = customer,
                            settings = state.settings,
                            recentInteractions = state.recentInteractions,
                            onOpen = { onOpenCustomer(customer.id) },
                            onAddInteraction = {
                                onAddInteraction(customer.id, customer.displayPhoneNumber)
                            },
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val result = viewModel.dial()
                            if (result is LaunchResult.Failure) {
                                scope.launch { snackbar.showSnackbar(result.userMessage) }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("この番号に発信") }
                }
            }
            Text("", Modifier.padding(bottom = 16.dp))
        }
    }
}

/** 登録済み顧客を大きく表示するカード(通話中に一目で確認できるように) */
@Composable
fun CustomerIntakeCard(
    customer: CustomerEntity,
    settings: AppSettings,
    recentInteractions: List<CallInteractionEntity>,
    onOpen: () -> Unit,
    onAddInteraction: () -> Unit,
) {
    SectionCard(customer.name + " 様") {
        CategoryBadge(customer.categoryEnum)
        Text(
            formatPhoneForUi(customer.displayPhoneNumber, settings),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "利用${customer.visitCount}回  前回: ${customer.lastVisitDate ?: "-"}  担当: ${customer.lastStaffName.ifBlank { "-" }}",
            style = MaterialTheme.typography.bodyLarge,
        )
        if (customer.cautionNotes.isNotBlank()) {
            Text(
                "⚠ ${customer.cautionNotes}",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (recentInteractions.isNotEmpty()) {
            Text("直近の対応:", fontWeight = FontWeight.Bold)
            recentInteractions.forEach { i ->
                Text(
                    "・${i.occurredAt.replace('T', ' ')} ${i.directionEnum.label} " +
                        "[${CallResult.fromName(i.result).label}]",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigActionButton("対応を登録", onAddInteraction, Modifier.weight(1f))
            OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) { Text("顧客詳細") }
        }
    }
}
