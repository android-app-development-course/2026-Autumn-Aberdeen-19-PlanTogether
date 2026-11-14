package com.plantogether.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Surface
import com.plantogether.app.data.ChecklistResponse
import com.plantogether.app.data.TripResponse

/** 行前清单：完全手动维护，用来对齐「谁负责订什么」。 */
@Composable
fun ChecklistTab(
    viewModel: TripDetailViewModel,
    trip: TripResponse?,
    checklist: List<ChecklistResponse>,
) {
    val canWrite = trip?.canWrite == true
    var newTitle by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ChecklistResponse?>(null) }
    var deleting by remember { mutableStateOf<ChecklistResponse?>(null) }

    val doneCount = checklist.count { it.done }
    val total = checklist.size

    Column(Modifier.fillMaxSize()) {
        if (total > 0) {
            AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "已完成 $doneCount / $total",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.weight(1f))
                        Pill(
                            "${doneCount * 100 / total}%",
                            container = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            bold = true,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { doneCount.toFloat() / total },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            if (total == 0) {
                item {
                    EmptyState(
                        title = "清单还是空的",
                        hint = "加几件行前要办的事，比如「订高铁票」「取现金」「买门票」。",
                        icon = "✓",
                        modifier = Modifier.padding(horizontal = 0.dp),
                    )
                }
            }
            items(checklist, key = { it.id }) { item ->
                AppCard(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = item.done,
                            onCheckedChange = { checked ->
                                if (canWrite) viewModel.setChecklistDone(item, checked)
                            },
                            enabled = canWrite,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (item.done) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
                            )
                            val meta = buildList {
                                item.assignee?.takeIf { it.isNotBlank() }?.let { add("负责人 $it") }
                                item.createdByName?.let { add("由 $it 添加") }
                            }
                            if (meta.isNotEmpty()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    meta.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (canWrite) {
                            TextButton(
                                onClick = { editing = item },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) { Text("改", style = MaterialTheme.typography.labelLarge) }
                            TextButton(
                                onClick = { deleting = item },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    "删",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (canWrite) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("添加一件事…") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
                Button(
                    onClick = {
                        viewModel.addChecklistItem(newTitle.trim(), null) { newTitle = "" }
                    },
                    enabled = newTitle.isNotBlank(),
                ) { Text("添加") }
            }
        }
    }

    editing?.let { item ->
        ChecklistEditDialog(
            item = item,
            onDismiss = { editing = null },
            onSave = { title, assignee ->
                viewModel.updateChecklistItem(item, title, assignee)
                editing = null
            },
        )
    }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除清单项") },
            text = { Text("确定删除「${item.title}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteChecklistItem(item.id)
                    deleting = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ChecklistEditDialog(
    item: ChecklistResponse,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit,
) {
    var title by remember(item) { mutableStateOf(item.title) }
    var assignee by remember(item) { mutableStateOf(item.assignee.orEmpty()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp)) {
                Text("编辑清单项", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    title,
                    { title = it },
                    label = { Text("事项 *") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    assignee,
                    { assignee = it },
                    label = { Text("负责人（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { onSave(title.trim(), assignee.trim().ifBlank { null }) },
                        enabled = title.isNotBlank(),
                    ) { Text("保存") }
                }
            }
        }
    }
}
