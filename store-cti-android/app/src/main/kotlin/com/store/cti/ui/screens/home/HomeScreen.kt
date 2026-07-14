package com.store.cti.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.store.cti.core.model.CustomerCategory
import com.store.cti.data.local.entity.CustomerEntity
import com.store.cti.data.local.entity.ReservationEntity
import com.store.cti.data.repository.CallInteractionRepository
import com.store.cti.data.repository.CustomerRepository
import com.store.cti.data.repository.ReservationRepository
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
import com.store.cti.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HomeUiState(
    val todayReservationCount: Int = 0,
    val callbackPendingCount: Int = 0,
    val todayReservations: List<ReservationEntity> = emptyList(),
    val recentCustomers: List<CustomerEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    reservationRepository: ReservationRepository,
    interactionRepository: CallInteractionRepository,
    customerRepository: CustomerRepository,
    settingsRepository: SettingsRepository,
    private val callLauncher: OutboundCallLauncher,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        reservationRepository.observeByDate(LocalDate.now()),
        interactionRepository.observeActive().map { list -> list.count { it.callbackPending } },
        customerRepository.observeRecent(5),
        settingsRepository.settings,
    ) { reservations, callbackCount, recent, settings ->
        val active = reservations.filter { it.statusEnum.occupiesSlot }
        HomeUiState(
            todayReservationCount = active.size,
            callbackPendingCount = callbackCount,
            todayReservations = active,
            recentCustomers = recent,
            settings = settings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun dial(rawPhone: String): LaunchResult = callLauncher.openDialer(rawPhone)
}

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showQuickDial by remember { mutableStateOf(false) }

    if (showQuickDial) {
        QuickDialDialog(
            onDismiss = { showQuickDial = false },
            onDial = { phone ->
                showQuickDial = false
                val result = viewModel.dial(phone)
                if (result is LaunchResult.Failure) {
                    scope.launch { snackbar.showSnackbar(result.userMessage) }
                }
            },
        )
    }

    ScreenScaffold(
        title = state.settings.storeName.ifBlank { "店舗CTI" },
        snackbarHostState = snackbar,
    ) { padding ->
        HomeContent(
            state = state,
            onNavigate = onNavigate,
            onQuickDial = { showQuickDial = true },
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
fun HomeContent(
    state: HomeUiState,
    onNavigate: (String) -> Unit,
    onQuickDial: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatCard("本日の予約", "${state.todayReservationCount}件", Modifier.weight(1f)) {
                    onNavigate(Routes.RESERVATION_LIST)
                }
                StatCard("折り返し未対応", "${state.callbackPendingCount}件", Modifier.weight(1f)) {
                    onNavigate(Routes.INTERACTION_LIST)
                }
            }
        }

        // 主要操作(押しやすい大ボタン2列)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigActionButton("着信対応", { onNavigate(Routes.callIntake()) }, Modifier.weight(1f), Icons.Default.PhoneCallback)
                    BigActionButton("顧客検索", { onNavigate(Routes.CUSTOMER_LIST) }, Modifier.weight(1f), Icons.Default.Search)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigActionButton("新規顧客登録", { onNavigate(Routes.customerEdit()) }, Modifier.weight(1f), Icons.Default.PersonAdd)
                    BigActionButton("本日の予約", { onNavigate(Routes.RESERVATION_LIST) }, Modifier.weight(1f), Icons.Default.Event)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigActionButton("対応履歴", { onNavigate(Routes.INTERACTION_LIST) }, Modifier.weight(1f), Icons.AutoMirrored.Filled.List)
                    BigActionButton("不在着信の登録", { onNavigate(Routes.interactionEdit()) }, Modifier.weight(1f), Icons.Default.PhoneCallback)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigActionButton("クイック発信", onQuickDial, Modifier.weight(1f), Icons.Default.Call)
                    BigActionButton("クイックSMS", { onNavigate(Routes.smsCompose()) }, Modifier.weight(1f), Icons.Default.Chat)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigActionButton("設定", { onNavigate(Routes.SETTINGS) }, Modifier.weight(1f), Icons.Default.Settings)
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        if (state.todayReservations.isNotEmpty()) {
            item {
                SectionCard("本日の予約") {
                    state.todayReservations.take(5).forEach { r ->
                        Text(
                            "${r.startTime}〜${r.endTime} ${r.courseName} (${r.staffName})",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate(Routes.reservationEdit(reservationId = r.id)) }
                                .padding(vertical = 6.dp),
                        )
                    }
                }
            }
        }

        item {
            SectionCard("最近の顧客") {
                if (state.recentCustomers.isEmpty()) {
                    Text("まだ顧客が登録されていません", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.recentCustomers.forEach { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate(Routes.customerDetail(c.id)) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(
                                formatPhoneForUi(c.displayPhoneNumber, state.settings),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        CategoryBadge(c.categoryEnum)
                    }
                }
            }
        }
        item { Spacer(Modifier.width(1.dp)) }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    SectionCard(label, modifier.clickable(onClick = onClick)) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun QuickDialDialog(onDismiss: () -> Unit, onDial: (String) -> Unit) {
    var phone by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("クイック発信") },
        text = {
            AppTextField(phone, { phone = it }, "電話番号", placeholder = "090-1234-5678")
        },
        confirmButton = {
            TextButton(onClick = { onDial(phone) }) { Text("電話アプリを開く") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    com.store.cti.ui.theme.StoreCtiTheme {
        HomeContent(
            state = HomeUiState(
                todayReservationCount = 2,
                callbackPendingCount = 1,
                recentCustomers = listOf(
                    CustomerEntity(
                        id = "1", name = "山田 太郎", displayPhoneNumber = "090-1234-5678",
                        category = CustomerCategory.FREQUENT.name,
                    ),
                    CustomerEntity(
                        id = "2", name = "鈴木 一郎", displayPhoneNumber = "070-3456-7890",
                        category = CustomerCategory.CAUTION.name,
                    ),
                ),
            ),
            onNavigate = {},
            onQuickDial = {},
        )
    }
}
