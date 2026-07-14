package com.store.cti.ui.screens.customers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.store.cti.core.phone.PhoneNumberUtils
import com.store.cti.data.local.entity.CustomerEntity
import com.store.cti.data.repository.CustomerRepository
import com.store.cti.data.settings.AppSettings
import com.store.cti.data.settings.SettingsRepository
import com.store.cti.ui.common.AppTextField
import com.store.cti.ui.common.BigActionButton
import com.store.cti.ui.common.CategoryBadge
import com.store.cti.ui.common.EmptyState
import com.store.cti.ui.common.ScreenScaffold
import com.store.cti.ui.common.formatPhoneForUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class CustomerSort(val label: String) {
    UPDATED("更新順"),
    LAST_VISIT("最終利用日順"),
    VISIT_COUNT("利用回数順"),
}

data class CustomerListUiState(
    val query: String = "",
    val sort: CustomerSort = CustomerSort.UPDATED,
    val cautionOnly: Boolean = false,
    val customers: List<CustomerEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
)

@HiltViewModel
class CustomerListViewModel @Inject constructor(
    customerRepository: CustomerRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(CustomerSort.UPDATED)
    private val cautionOnly = MutableStateFlow(false)

    val uiState: StateFlow<CustomerListUiState> = combine(
        customerRepository.observeActive(), query, sort, cautionOnly, settingsRepository.settings,
    ) { customers, q, s, caution, settings ->
        CustomerListUiState(
            query = q,
            sort = s,
            cautionOnly = caution,
            customers = customers.filter { matches(it, q, caution) }.sortedWith(comparator(s)),
            settings = settings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustomerListUiState())

    fun setQuery(value: String) { query.value = value }
    fun setSort(value: CustomerSort) { sort.value = value }
    fun setCautionOnly(value: Boolean) { cautionOnly.value = value }

    private fun matches(c: CustomerEntity, rawQuery: String, cautionOnly: Boolean): Boolean {
        if (cautionOnly && !c.categoryEnum.requiresAttention) return false
        val q = rawQuery.trim()
        if (q.isEmpty()) return true
        // 数字を含む検索語は電話番号として(ハイフン有無に関係なく)照合する
        val digits = PhoneNumberUtils.normalize(q)
        if (digits.length >= 4) {
            if (c.normalizedPhoneNumber.contains(digits) || c.normalizedPhoneNumber2.contains(digits)) {
                return true
            }
        }
        return c.name.contains(q) || c.kana.contains(q) ||
            c.customerNumber.contains(q) || c.lineName.contains(q)
    }

    private fun comparator(sort: CustomerSort): Comparator<CustomerEntity> = when (sort) {
        CustomerSort.UPDATED -> compareByDescending { it.updatedAt }
        CustomerSort.LAST_VISIT -> compareByDescending { it.lastVisitDate ?: "" }
        CustomerSort.VISIT_COUNT -> compareByDescending { it.visitCount }
    }
}

@Composable
fun CustomerListScreen(
    onBack: () -> Unit,
    onOpenCustomer: (String) -> Unit,
    onNewCustomer: () -> Unit,
    viewModel: CustomerListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    ScreenScaffold(
        title = "顧客一覧",
        onBack = onBack,
        bottomBar = {
            BigActionButton(
                "新規顧客登録",
                onNewCustomer,
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                Icons.Default.PersonAdd,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            AppTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = "氏名・ふりがな・電話番号・顧客番号で検索",
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CustomerSort.entries.forEach { s ->
                    FilterChip(
                        selected = state.sort == s,
                        onClick = { viewModel.setSort(s) },
                        label = { Text(s.label) },
                    )
                }
                FilterChip(
                    selected = state.cautionOnly,
                    onClick = { viewModel.setCautionOnly(!state.cautionOnly) },
                    label = { Text("注意顧客のみ") },
                )
            }
            if (state.customers.isEmpty()) {
                EmptyState("該当する顧客がいません")
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.customers, key = { it.id }) { c ->
                    CustomerRow(c, state.settings) { onOpenCustomer(c.id) }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun CustomerRow(
    customer: CustomerEntity,
    settings: AppSettings,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(customer.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(
                buildString {
                    append(formatPhoneForUi(customer.displayPhoneNumber, settings))
                    if (!customer.lastVisitDate.isNullOrBlank()) append("  前回: ${customer.lastVisitDate}")
                    append("  利用${customer.visitCount}回")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CategoryBadge(customer.categoryEnum)
    }
}
