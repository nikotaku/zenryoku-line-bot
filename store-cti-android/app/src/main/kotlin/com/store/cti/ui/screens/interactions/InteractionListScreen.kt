package com.store.cti.ui.screens.interactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.store.cti.core.model.CallDirection
import com.store.cti.core.model.CallResult
import com.store.cti.core.phone.PhoneNumberUtils
import com.store.cti.data.local.entity.CallInteractionEntity
import com.store.cti.data.local.entity.CustomerEntity
import com.store.cti.data.repository.CallInteractionRepository
import com.store.cti.data.repository.CustomerRepository
import com.store.cti.data.settings.AppSettings
import com.store.cti.data.settings.SettingsRepository
import com.store.cti.ui.common.AppTextField
import com.store.cti.ui.common.EmptyState
import com.store.cti.ui.common.ScreenScaffold
import com.store.cti.ui.common.formatPhoneForUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InteractionFilter(
    val fromDate: String = "",   // yyyy-MM-dd
    val toDate: String = "",
    val text: String = "",       // 顧客名・電話番号・スタッフ名
    val result: CallResult? = null,
    val direction: CallDirection? = null,
    val callbackPendingOnly: Boolean = false,
)

data class InteractionListUiState(
    val filter: InteractionFilter = InteractionFilter(),
    val items: List<CallInteractionEntity> = emptyList(),
    val customerNames: Map<String, String> = emptyMap(),
    val settings: AppSettings = AppSettings(),
)

@HiltViewModel
class InteractionListViewModel @Inject constructor(
    interactionRepository: CallInteractionRepository,
    customerRepository: CustomerRepository,
    settingsRepository: SettingsRepository,
    private val repository: CallInteractionRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(InteractionFilter())

    val uiState: StateFlow<InteractionListUiState> = combine(
        interactionRepository.observeActive(),
        customerRepository.observeActive(),
        filter,
        settingsRepository.settings,
    ) { interactions, customers, f, settings ->
        val names = customers.associate { it.id to it.name }
        InteractionListUiState(
            filter = f,
            items = interactions.filter { matches(it, f, names) },
            customerNames = names,
            settings = settings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InteractionListUiState())

    fun setFilter(value: InteractionFilter) { filter.value = value }

    fun markCallbackDone(id: String) {
        viewModelScope.launch { repository.markCallbackDone(id) }
    }

    private fun matches(
        i: CallInteractionEntity,
        f: InteractionFilter,
        names: Map<String, String>,
    ): Boolean {
        if (f.callbackPendingOnly && !i.callbackPending) return false
        if (f.result != null && i.result != f.result.name) return false
        if (f.direction != null && i.direction != f.direction.name) return false
        // 期間(ISO文字列の辞書順比較 = 時系列比較)
        val dateOnly = i.occurredAt.take(10)
        if (f.fromDate.isNotBlank() && dateOnly < f.fromDate) return false
        if (f.toDate.isNotBlank() && dateOnly > f.toDate) return false
        val text = f.text.trim()
        if (text.isNotEmpty()) {
            val digits = PhoneNumberUtils.normalize(text)
            val name = i.customerId?.let { names[it] }.orEmpty()
            val phoneHit = digits.length >= 4 && i.normalizedPhoneNumber.contains(digits)
            val textHit = name.contains(text) || i.staffName.contains(text)
            if (!phoneHit && !textHit) return false
        }
        return true
    }
}

@Composable
fun InteractionListScreen(
    onBack: () -> Unit,
    onOpenCustomer: (String) -> Unit,
    onOpenReservation: (String) -> Unit,
    onOpenInteraction: (String) -> Unit,
    viewModel: InteractionListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showFilters by remember { mutableStateOf(false) }
    val f = state.filter

    ScreenScaffold(title = "対応履歴", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            AppTextField(
                value = f.text,
                onValueChange = { viewModel.setFilter(f.copy(text = it)) },
                label = "顧客名・電話番号・スタッフ名で検索",
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = f.callbackPendingOnly,
                    onClick = { viewModel.setFilter(f.copy(callbackPendingOnly = !f.callbackPendingOnly)) },
                    label = { Text("折り返し未完了") },
                )
                FilterChip(
                    selected = showFilters,
                    onClick = { showFilters = !showFilters },
                    label = { Text("詳細条件") },
                )
            }
            if (showFilters) {
                Column(
                    Modifier.padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextField(f.fromDate, { viewModel.setFilter(f.copy(fromDate = it)) }, "開始日",
                            Modifier.weight(1f), placeholder = "2026-07-01")
                        AppTextField(f.toDate, { viewModel.setFilter(f.copy(toDate = it)) }, "終了日",
                            Modifier.weight(1f), placeholder = "2026-07-31")
                    }
                    com.store.cti.ui.common.ChipSelector(
                        label = "着信・発信",
                        options = listOf<CallDirection?>(null) + CallDirection.entries,
                        selected = f.direction,
                        optionLabel = { it?.label ?: "すべて" },
                        onSelect = { viewModel.setFilter(f.copy(direction = it)) },
                    )
                    com.store.cti.ui.common.ChipSelector(
                        label = "対応結果",
                        options = listOf<CallResult?>(null) + CallResult.entries,
                        selected = f.result,
                        optionLabel = { it?.label ?: "すべて" },
                        onSelect = { viewModel.setFilter(f.copy(result = it)) },
                    )
                }
            }

            if (state.items.isEmpty()) {
                EmptyState("該当する対応履歴がありません")
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.items, key = { it.id }) { item ->
                    InteractionRow(
                        item = item,
                        customerName = item.customerId?.let { state.customerNames[it] },
                        settings = state.settings,
                        onOpen = { onOpenInteraction(item.id) },
                        onOpenCustomer = item.customerId?.let { id -> { onOpenCustomer(id) } },
                        onOpenReservation = item.reservationId?.let { id -> { onOpenReservation(id) } },
                        onMarkCallbackDone = { viewModel.markCallbackDone(item.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun InteractionRow(
    item: CallInteractionEntity,
    customerName: String?,
    settings: AppSettings,
    onOpen: () -> Unit,
    onOpenCustomer: (() -> Unit)?,
    onOpenReservation: (() -> Unit)?,
    onMarkCallbackDone: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            "${item.occurredAt.replace('T', ' ')}  ${item.directionEnum.label}" +
                (if (!item.answered) "(不応答)" else ""),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            (customerName ?: "未登録") + "  " + formatPhoneForUi(item.displayPhoneNumber, settings),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "[${item.resultEnum.label}] ${item.staffName}" +
                if (item.callbackPending) "  ⚠折り返し未完了" else "",
            color = if (item.callbackPending) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (onOpenCustomer != null) {
                TextButton(onClick = onOpenCustomer) { Text("顧客を開く") }
            }
            if (onOpenReservation != null) {
                TextButton(onClick = onOpenReservation) { Text("予約を開く") }
            }
            if (item.callbackPending) {
                TextButton(onClick = onMarkCallbackDone) { Text("折り返し完了にする") }
            }
        }
    }
}
