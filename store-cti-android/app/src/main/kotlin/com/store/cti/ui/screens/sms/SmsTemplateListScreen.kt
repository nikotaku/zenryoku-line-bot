package com.store.cti.ui.screens.sms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.store.cti.core.sms.SmsTemplateEngine
import com.store.cti.data.local.entity.SmsTemplateEntity
import com.store.cti.data.repository.SmsRepository
import com.store.cti.ui.common.AppTextField
import com.store.cti.ui.common.BigActionButton
import com.store.cti.ui.common.ConfirmDialog
import com.store.cti.ui.common.EmptyState
import com.store.cti.ui.common.ScreenScaffold
import com.store.cti.ui.common.SectionCard
import com.store.cti.util.newUuid
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SmsTemplateListViewModel @Inject constructor(
    private val smsRepository: SmsRepository,
) : ViewModel() {

    val templates: StateFlow<List<SmsTemplateEntity>> =
        smsRepository.observeTemplates()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(template: SmsTemplateEntity, isNew: Boolean) {
        viewModelScope.launch { smsRepository.saveTemplate(template, isNew) }
    }

    fun setEnabled(template: SmsTemplateEntity, enabled: Boolean) {
        viewModelScope.launch { smsRepository.saveTemplate(template.copy(enabled = enabled), isNew = false) }
    }

    fun delete(template: SmsTemplateEntity) {
        viewModelScope.launch { smsRepository.softDeleteTemplate(template.id) }
    }

    fun move(template: SmsTemplateEntity, up: Boolean) {
        val list = templates.value
        val index = list.indexOfFirst { it.id == template.id }
        val other = list.getOrNull(if (up) index - 1 else index + 1) ?: return
        viewModelScope.launch { smsRepository.swapOrder(template, other) }
    }

    /** 新規テンプレートの雛形(sortOrder は末尾) */
    fun newTemplate(): SmsTemplateEntity = SmsTemplateEntity(
        id = newUuid(),
        sortOrder = (templates.value.maxOfOrNull { it.sortOrder } ?: -1) + 1,
    )
}

@Composable
fun SmsTemplateListScreen(
    onBack: () -> Unit,
    viewModel: SmsTemplateListViewModel = hiltViewModel(),
) {
    val templates by viewModel.templates.collectAsState()
    var editing by remember { mutableStateOf<SmsTemplateEntity?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SmsTemplateEntity?>(null) }

    editing?.let { template ->
        TemplateEditDialog(
            template = template,
            onSave = { updated ->
                viewModel.save(updated, editingIsNew)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { template ->
        ConfirmDialog(
            title = "テンプレートを削除しますか?",
            text = "「${template.name}」を削除します(作成済みのSMS履歴には影響しません)。",
            confirmLabel = "削除する",
            destructive = true,
            onConfirm = {
                viewModel.delete(template)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }

    ScreenScaffold(
        title = "SMSテンプレート",
        onBack = onBack,
        bottomBar = {
            BigActionButton(
                "テンプレートを追加",
                {
                    editing = viewModel.newTemplate()
                    editingIsNew = true
                },
                Modifier.fillMaxWidth().padding(12.dp),
                Icons.Default.Add,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "使える差し込み項目: " + SmsTemplateEngine.SUPPORTED_PLACEHOLDERS.joinToString(" "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
            if (templates.isEmpty()) EmptyState("テンプレートがありません")
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(templates, key = { it.id }) { t ->
                    SectionCard(t.name.ifBlank { "(名称未設定)" }) {
                        Text(t.body, style = MaterialTheme.typography.bodyMedium)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = t.enabled,
                                    onCheckedChange = { viewModel.setEnabled(t, it) },
                                )
                                Text(if (t.enabled) "有効" else "無効")
                            }
                            Row {
                                IconButton(onClick = { viewModel.move(t, up = true) }) {
                                    Icon(Icons.Default.ArrowUpward, "上へ")
                                }
                                IconButton(onClick = { viewModel.move(t, up = false) }) {
                                    Icon(Icons.Default.ArrowDownward, "下へ")
                                }
                                TextButton(onClick = {
                                    editing = t
                                    editingIsNew = false
                                }) { Text("編集") }
                                TextButton(onClick = { deleting = t }) {
                                    Text("削除", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
                item { Text("") }
            }
        }
    }
}

@Composable
private fun TemplateEditDialog(
    template: SmsTemplateEntity,
    onSave: (SmsTemplateEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(template.name) }
    var body by remember { mutableStateOf(template.body) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (template.name.isBlank()) "テンプレートを追加" else "テンプレートを編集") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(name, { name = it }, "テンプレート名")
                AppTextField(body, { body = it }, "本文", singleLine = false)
                Text(
                    "差し込み: " + SmsTemplateEngine.SUPPORTED_PLACEHOLDERS.joinToString(" "),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(template.copy(name = name, body = body)) },
                enabled = name.isNotBlank() && body.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}
