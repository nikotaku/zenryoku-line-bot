package com.store.cti.ui.screens.reservations

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.store.cti.core.model.ReservationStatus
import com.store.cti.data.local.entity.CustomerEntity
import com.store.cti.data.local.entity.ReservationEntity
import com.store.cti.data.repository.CustomerRepository
import com.store.cti.data.repository.ReservationRepository
import com.store.cti.data.settings.AppSettings
import com.store.cti.data.settings.SettingsRepository
import com.store.cti.ui.common.AppTextField
import com.store.cti.ui.common.BigActionButton
import com.store.cti.ui.common.ChipSelector
import com.store.cti.ui.common.EmptyState
import com.store.cti.ui.common.ScreenScaffold
import com.store.cti.ui.common.SectionCard
import com.store.cti.ui.common.formatPhoneForUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class ReservationFilter(
    val dateInput: String = LocalDate.now().toString(), // yyyy-MM-dd。空 = 全日付
    val status: ReservationStatus? = null,
    val staffName: String? = null,
)

data class ReservationListUiState(
    val filter: ReservationFilter = ReservationFilter(),
    val items: List<ReservationEntity> = emptyList(),
    val customers: Map<String, CustomerEntity> = emptyMap(),
    val staffNames: List<String> = emptyList(),
    val settings: AppSettings = AppSettings(),
)

@HiltViewModel
class ReservationListViewModel @Inject constructor(
    reservationRepository: ReservationRepository,
    customerRepository: CustomerRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(ReservationFilter())

    val uiState: StateFlow<ReservationListUiState> = combine(
        reservationRepository.observeActive(),
        customerRepository.observeActive(),
        filter,
        settingsRepository.settings,
    ) { reservations, customers, f, settings ->
        val filtered = reservations
            .filter { f.dateInput.isBlank() || it.date == f.dateInput.trim() }
            .filter { f.status == null || it.status == f.status.name }
            .filter { f.staffName == null || it.staffName == f.staffName }
            .sortedWith(compareBy({ it.date }, { it.startTime }))
        ReservationListUiState(
            filter = f,
            items = filtered,
            customers = customers.associateBy { it.id },
            staffNames = reservations.map { it.staffName }.filter { it.isNotBlank() }.distinct().sorted(),
            settings = settings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReservationListUiState())

    fun setFilter(value: ReservationFilter) { filter.value = value }
}

@Composable
fun ReservationListScreen(
    onBack: () -> Unit,
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenCustomer: (String) -> Unit,
    viewModel: ReservationListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val f = state.filter
    val today = LocalDate.now().toString()
    val tomorrow = LocalDate.now().plusDays(1).toString()

    ScreenScaffold(
        title = "予約一覧",
        onBack = onBack,
        bottomBar = {
            BigActionButton("予約を登録", onNew, Modifier.fillMaxWidth().padding(12.dp))
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = f.dateInput == today,
                    onClick = { viewModel.setFilter(f.copy(dateInput = today)) },
                    label = { Text("本日") },
                )
                FilterChip(
                    selected = f.dateInput == tomorrow,
                    onClick = { viewModel.setFilter(f.copy(dateInput = tomorrow)) },
                    label = { Text("明日") },
                )
                FilterChip(
                    selected = f.dateInput.isBlank(),
                    onClick = { viewModel.setFilter(f.copy(dateInput = "")) },
                    label = { Text("すべての日付") },
                )
            }
            AppTextField(
                value = f.dateInput,
                onValueChange = { viewModel.setFilter(f.copy(dateInput = it)) },
                label = "日付指定",
                placeholder = "2026-07-20(空欄で全日付)",
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                ChipSelector(
                    label = "予約状態",
                    options = listOf<ReservationStatus?>(null) + ReservationStatus.entries,
                    selected = f.status,
                    optionLabel = { it?.label ?: "すべて" },
                    onSelect = { viewModel.setFilter(f.copy(status = it)) },
                )
                if (state.staffNames.isNotEmpty()) {
                    ChipSelector(
                        label = "担当者",
                        options = listOf<String?>(null) + state.staffNames,
                        selected = f.staffName,
                        optionLabel = { it ?: "すべて" },
                        onSelect = { viewModel.setFilter(f.copy(staffName = it)) },
                    )
                }
            }

            if (state.items.isEmpty()) {
                EmptyState("該当する予約がありません")
            }
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items, key = { it.id }) { r ->
                    ReservationCard(
                        reservation = r,
                        customer = r.customerId?.let { state.customers[it] },
                        settings = state.settings,
                        onClick = { onEdit(r.id) },
                        onOpenCustomer = r.customerId?.let { id -> { onOpenCustomer(id) } },
                    )
                }
                item { Text("") }
            }
        }
    }
}

@Composable
fun ReservationCard(
    reservation: ReservationEntity,
    customer: CustomerEntity?,
    settings: AppSettings,
    onClick: () -> Unit,
    onOpenCustomer: (() -> Unit)? = null,
) {
    val status = ReservationStatus.fromName(reservation.status)
    SectionCard(
        "${reservation.date}  ${reservation.startTime}〜${reservation.endTime}",
        Modifier.clickable(onClick = onClick),
    ) {
        Text(
            (customer?.name?.plus(" 様") ?: "(顧客未設定)") +
                "  " + (customer?.let { formatPhoneForUi(it.displayPhoneNumber, settings) } ?: ""),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "担当: ${reservation.staffName.ifBlank { "-" }}  コース: ${reservation.courseName.ifBlank { "-" }}" +
                (reservation.price?.let { "  %,d円".format(it) } ?: "") +
                (reservation.room.takeIf { it.isNotBlank() }?.let { "  部屋: $it" } ?: ""),
        )
        Text(
            "[${status.label}]",
            color = when (status) {
                ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            },
            fontWeight = FontWeight.Bold,
        )
        if (reservation.requests.isNotBlank()) Text("要望: ${reservation.requests}")
        if (reservation.cautionNotes.isNotBlank()) {
            Text("⚠ ${reservation.cautionNotes}", color = MaterialTheme.colorScheme.error)
        }
        if (onOpenCustomer != null) {
            TextButton(onClick = onOpenCustomer) { Text("顧客を開く") }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReservationCardPreview() {
    com.store.cti.ui.theme.StoreCtiTheme {
        ReservationCard(
            reservation = ReservationEntity(
                id = "r1", date = "2026-07-20", startTime = "14:00", endTime = "15:00",
                staffName = "スタッフA", courseName = "60分コース", price = 8000,
                status = ReservationStatus.CONFIRMED.name, room = "1号室",
            ),
            customer = CustomerEntity(id = "c1", name = "山田 太郎", displayPhoneNumber = "090-1234-5678"),
            settings = AppSettings(),
            onClick = {},
        )
    }
}
