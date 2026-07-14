package com.store.cti.ui.screens.customers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.store.cti.core.model.CallResult
import com.store.cti.core.model.ReservationStatus
import com.store.cti.core.model.SmsDraftStatus
import com.store.cti.data.local.entity.CallInteractionEntity
import com.store.cti.data.local.entity.CustomerEntity
import com.store.cti.data.local.entity.ReservationEntity
import com.store.cti.data.local.entity.SmsDraftEntity
import com.store.cti.data.repository.CallInteractionRepository
import com.store.cti.data.repository.CustomerRepository
import com.store.cti.data.repository.ReservationRepository
import com.store.cti.data.repository.SmsRepository
import com.store.cti.data.settings.AppSettings
import com.store.cti.data.settings.SettingsRepository
import com.store.cti.telephony.LaunchResult
import com.store.cti.telephony.OutboundCallLauncher
import com.store.cti.ui.common.BigActionButton
import com.store.cti.ui.common.CategoryBadge
import com.store.cti.ui.common.ScreenScaffold
import com.store.cti.ui.common.SectionCard
import com.store.cti.ui.common.formatPhoneForUi
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomerDetailUiState(
    val customer: CustomerEntity? = null,
    val reservations: List<ReservationEntity> = emptyList(),
    val interactions: List<CallInteractionEntity> = emptyList(),
    val smsDrafts: List<SmsDraftEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
)

@HiltViewModel
class CustomerDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    customerRepository: CustomerRepository,
    reservationRepository: ReservationRepository,
    interactionRepository: CallInteractionRepository,
    smsRepository: SmsRepository,
    settingsRepository: SettingsRepository,
    private val callLauncher: OutboundCallLauncher,
) : ViewModel() {

    private val customerId: String = savedStateHandle.get<String>("customerId").orEmpty()

    val uiState: StateFlow<CustomerDetailUiState> = combine(
        customerRepository.observeById(customerId),
        reservationRepository.observeByCustomer(customerId),
        interactionRepository.observeByCustomer(customerId),
        smsRepository.observeDraftsByCustomer(customerId),
        settingsRepository.settings,
    ) { customer, reservations, interactions, drafts, settings ->
        CustomerDetailUiState(customer, reservations, interactions, drafts, settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustomerDetailUiState())

    fun dial(rawPhone: String): LaunchResult = callLauncher.openDialer(rawPhone)
}

@Composable
fun CustomerDetailScreen(
    customerId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddReservation: () -> Unit,
    onAddInteraction: (String) -> Unit,
    onComposeSms: () -> Unit,
    onOpenReservation: (String) -> Unit,
    onOpenInteraction: (String) -> Unit,
    viewModel: CustomerDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val customer = state.customer

    ScreenScaffold(
        title = customer?.name?.let { "$it 様" } ?: "顧客詳細",
        onBack = onBack,
        snackbarHostState = snackbar,
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BigActionButton(
                    "電話する",
                    {
                        val result = viewModel.dial(customer?.displayPhoneNumber.orEmpty())
                        if (result is LaunchResult.Failure) {
                            scope.launch { snackbar.showSnackbar(result.userMessage) }
                        }
                    },
                    Modifier.weight(1f),
                    Icons.Default.Call,
                )
                BigActionButton("SMSを作成", onComposeSms, Modifier.weight(1f), Icons.Default.Chat)
            }
        },
    ) { padding ->
        if (customer == null) {
            Text("顧客が見つかりません", Modifier.padding(padding).padding(24.dp))
            return@ScreenScaffold
        }
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SectionCard("基本情報") {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(customer.name, style = MaterialTheme.typography.titleLarge)
                        CategoryBadge(customer.categoryEnum)
                    }
                    if (customer.kana.isNotBlank()) Text(customer.kana)
                    Text(
                        formatPhoneForUi(customer.displayPhoneNumber, state.settings),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (customer.displayPhoneNumber2.isNotBlank()) {
                        Text("予備: ${formatPhoneForUi(customer.displayPhoneNumber2, state.settings)}")
                    }
                    Text("利用回数: ${customer.visitCount}回  最終利用日: ${customer.lastVisitDate ?: "-"}")
                    Text("前回担当: ${customer.lastStaffName.ifBlank { "-" }}  希望担当: ${customer.preferredStaffName.ifBlank { "-" }}")
                    if (customer.customerNumber.isNotBlank()) Text("顧客番号: ${customer.customerNumber}")
                    if (customer.lineName.isNotBlank()) Text("LINE名: ${customer.lineName}")
                }
            }

            if (customer.cautionNotes.isNotBlank() || customer.prohibitedActions.isNotBlank()) {
                item {
                    SectionCard("⚠ 注意事項") {
                        if (customer.cautionNotes.isNotBlank()) {
                            Text(customer.cautionNotes, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                        if (customer.prohibitedActions.isNotBlank()) {
                            Text("禁止事項: ${customer.prohibitedActions}", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            if (customer.servicePreferences.isNotBlank() || customer.memo.isNotBlank()) {
                item {
                    SectionCard("メモ・好み") {
                        if (customer.servicePreferences.isNotBlank()) Text("好み: ${customer.servicePreferences}")
                        if (customer.memo.isNotBlank()) Text(customer.memo)
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigActionButton("予約を登録", onAddReservation, Modifier.weight(1f))
                    BigActionButton(
                        "対応履歴を追加",
                        { onAddInteraction(customer.displayPhoneNumber) },
                        Modifier.weight(1f),
                    )
                }
            }
            item {
                androidx.compose.material3.OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                    Text("顧客情報を編集")
                }
            }

            item {
                SectionCard("予約履歴 (${state.reservations.size})") {
                    if (state.reservations.isEmpty()) Text("予約はありません")
                    state.reservations.take(10).forEach { r ->
                        Text(
                            "${r.date} ${r.startTime}〜${r.endTime} ${r.courseName} " +
                                "[${ReservationStatus.fromName(r.status).label}]",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenReservation(r.id) }
                                .padding(vertical = 6.dp),
                        )
                    }
                }
            }

            item {
                SectionCard("通話対応履歴 (${state.interactions.size})") {
                    if (state.interactions.isEmpty()) Text("対応履歴はありません")
                    state.interactions.take(10).forEach { i ->
                        Text(
                            "${i.occurredAt.replace('T', ' ')} ${i.directionEnum.label} " +
                                "[${CallResult.fromName(i.result).label}] ${i.staffName}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenInteraction(i.id) }
                                .padding(vertical = 6.dp),
                        )
                    }
                }
            }

            item {
                SectionCard("SMS履歴 (${state.smsDrafts.size})") {
                    if (state.smsDrafts.isEmpty()) Text("SMS作成履歴はありません")
                    state.smsDrafts.take(10).forEach { d ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(
                                "${d.templateName.ifBlank { "(テンプレートなし)" }} " +
                                    "[${SmsDraftStatus.fromName(d.status).label}]",
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                d.body.take(60) + if (d.body.length > 60) "…" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item { Text("", Modifier.padding(bottom = 8.dp)) }
        }
    }
}
