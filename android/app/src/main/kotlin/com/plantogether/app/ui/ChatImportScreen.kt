package com.plantogether.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.plantogether.app.data.ChatSuggestion
import com.plantogether.app.data.SuggestionKind

/**
 * 聊天记录 → 行程 的预览确认页。
 *
 * 这一页存在的意义就是「不自动写」：左边是原话，右边是要写进去的条目，
 * 用户可以逐条勾选、改名、丢弃。全部取消也能安全退出，什么都不会变。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatImportScreen(
    onBack: () -> Unit,
    onImported: (Long) -> Unit,
    initialZipBytes: ByteArray? = null,
    initialZipName: String? = null,
    initialZipEventId: Long = 0L,
    viewModel: ChatImportViewModel = viewModel(),
) {
    // 从微信直接转发进来时自动开始解析，省掉用户再点一次「选择文件」
    LaunchedEffect(initialZipEventId) {
        if (initialZipBytes != null && initialZipBytes.isNotEmpty()) {
            viewModel.loadZip(initialZipBytes, initialZipName ?: "聊天记录.zip")
        }
    }

    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var pasteOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ChatSuggestion?>(null) }
    var tripMenuOpen by remember { mutableStateOf(false) }
    var targetTripId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) { viewModel.loadTrips() }

    // 只有一个可写行程时默认就选它，省一次点击
    LaunchedEffect(trips) {
        if (targetTripId == null && trips.size == 1) targetTripId = trips.first().id
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            val name = runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst() && cursor.columnCount > 0) {
                        val index = cursor.getColumnIndex("_display_name")
                        if (index >= 0) cursor.getString(index) else null
                    } else null
                }
            }.getOrNull() ?: "聊天记录.zip"
            if (bytes == null || bytes.isEmpty()) {
                viewModel.loadText("", name)
            } else {
                viewModel.loadZip(bytes, name)
            }
        }
    }

    val targetTrip = trips.firstOrNull { it.id == targetTripId }
    val selected = items.count { it.selected }
    val finished = state as? ChatImportViewModel.State.Finished
    val importing = state as? ChatImportViewModel.State.Importing

    LaunchedEffect(finished) {
        if (finished != null && finished.written > 0) onImported(finished.tripId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("从聊天记录生成规划") },
                navigationIcon = { TextButton(onClick = onBack) { Text("← 返回") } },
                actions = {
                    if (items.isNotEmpty()) {
                        TextButton(onClick = { viewModel.setAllSelected(false) }) { Text("全不选") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            when (val s = state) {
                is ChatImportViewModel.State.Reading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                is ChatImportViewModel.State.Failed -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.message, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { viewModel.reset() }) { Text("重新选一个文件") }
                    }
                }

                is ChatImportViewModel.State.Finished -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("已写入 ${s.written} 条", style = MaterialTheme.typography.titleMedium)
                        if (s.skipped > 0) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${s.skipped} 条行程里已经有了，没重复写",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (s.failed > 0) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${s.failed} 条没写成功，可能是权限或网络问题",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Row {
                            TextButton(onClick = { viewModel.reset() }) { Text("再导入一份") }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { onImported(s.tripId) }) { Text("打开行程") }
                        }
                    }
                }

                else -> {
                    val ready = state as? ChatImportViewModel.State.Ready
                    if (ready == null && items.isEmpty()) {
                        DropZone(
                            onPickFile = { picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                            onPasteText = { pasteOpen = true },
                        )
                        return@Column
                    }

                    if (ready != null) {
                        SummaryCard(
                            messageCount = ready.extraction.messageCount,
                            senderCount = ready.extraction.senderCount,
                            attachmentCount = ready.extraction.attachmentCount,
                            summary = ChatTripExtractionSummary(ready.extraction),
                        )
                    }

                    if (items.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("这段聊天里没找出能直接用东西")
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "可以去行程里手动加，或者换一份更完整的记录",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = { viewModel.reset() }) { Text("换一份") }
                            }
                        }
                        return@Column
                    }

                    LazyColumn(Modifier.weight(1f)) {
                        val groups = ready?.let {
                            com.plantogether.app.data.ChatTripExtractor.groupByDay(items)
                        } ?: listOf(null to items)
                        for ((day, list) in groups) {
                            if (day != null) {
                                item {
                                    Text(
                                        formatDayLabel(day, ready?.extraction?.startDate),
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                                    )
                                }
                            }
                            items(list, key = { it.id }) { suggestion ->
                                SuggestionRow(
                                    suggestion = suggestion,
                                    onToggle = { viewModel.toggle(suggestion.id) },
                                    onEdit = { editing = suggestion },
                                    onDrop = { viewModel.drop(suggestion.id) },
                                )
                            }
                        }
                        item { Spacer(Modifier.height(12.dp)) }
                    }

                    if (importing != null) {
                        LinearProgressIndicator(
                            progress = { importing.done.toFloat() / importing.total.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                        Text(
                            "正在写入 ${importing.done}/${importing.total}…",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            OutlinedCard(onClick = { tripMenuOpen = true }) {
                                Text(
                                    targetTrip?.title ?: "选择行程",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            DropdownMenu(
                                expanded = tripMenuOpen,
                                onDismissRequest = { tripMenuOpen = false },
                            ) {
                                if (trips.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("你有编辑权限的行程是空的") },
                                        onClick = { tripMenuOpen = false },
                                    )
                                }
                                for (trip in trips) {
                                    DropdownMenuItem(
                                        text = { Text(trip.title) },
                                        onClick = {
                                            targetTripId = trip.id
                                            tripMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = {
                                val id = targetTripId
                                if (id != null) viewModel.importInto(id)
                            },
                            enabled = selected > 0 && targetTripId != null && importing == null,
                        ) { Text("导入 $selected 条") }
                    }
                }
            }
        }
    }

    message?.let {
        Dialog(onDismissRequest = { viewModel.consumeMessage() }) {
            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                Column(Modifier.padding(20.dp)) {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { viewModel.consumeMessage() }) { Text("好") }
                    }
                }
            }
        }
    }

    editing?.let { suggestion ->
        EditSuggestionDialog(
            initial = suggestion.title,
            kind = suggestion.kind,
            onDismiss = { editing = null },
            onSave = { value ->
                viewModel.rename(suggestion.id, value)
                editing = null
            },
        )
    }

    if (pasteOpen) {
        PasteTextDialog(
            onDismiss = { pasteOpen = false },
            onConfirm = { text ->
                pasteOpen = false
                viewModel.loadText(text)
            },
        )
    }
}

// ---------------- 各块 UI ----------------

@Composable
private fun DropZone(onPickFile: () -> Unit, onPasteText: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPickFile),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("选择聊天记录压缩包", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "微信里长按一条消息 → 多选 → 选好要导入的消息 → 转发到其他应用 → 选择 PlanTogether。\n" +
                        "微信会自己把聊天打成压缩包，我们只在本机解压读文字，不上传。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onPasteText) { Text("或者直接粘贴聊天文字") }

        Spacer(Modifier.height(28.dp))
        Text(
            "能认出什么",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        val notes = listOf(
            "地点 —— 聊到「去西湖」「吃楼外楼」这类，会连同提到的时间一起提出来",
            "待定 —— 有人问「住民宿还是住酒店」，会变成一个待投票的决策",
            "待办 —— 「记得带充电宝」「谁去订票」，会变成清单项",
            "预算 —— 「人均 500 左右」，会填进行程预算",
        )
        for (note in notes) {
            Text(
                "· $note",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "认出来的东西都只是建议，会在下一页让你逐条确认 —— 不确认就不会写进去。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SummaryCard(
    messageCount: Int,
    senderCount: Int,
    attachmentCount: Int,
    summary: String,
) {
    OutlinedCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(summary, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            val meta = buildList {
                add("$messageCount 条消息")
                add("$senderCount 人")
                if (attachmentCount > 0) add("$attachmentCount 个附件（已跳过）")
            }
            Text(
                meta.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: ChatSuggestion,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDrop: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.Top) {
            Checkbox(checked = suggestion.selected, onCheckedChange = { onToggle() })
            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KindChip(suggestion.kind)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        suggestion.title,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val meta = buildList {
                    suggestion.startTime?.let {
                        add(if (suggestion.endTime != null) "$it–${suggestion.endTime}" else it)
                    }
                    // 预算条的标题已经是「预算 ¥500」，元信息里不重复
                    if (suggestion.kind != SuggestionKind.BUDGET) {
                        suggestion.budget?.let { add("¥${formatMoney(it)?.removePrefix("¥")}") }
                    }
                    // 待定的标题就是选项拼的，不重复再列一遍
                    if (suggestion.options.isNotEmpty() && suggestion.kind != SuggestionKind.DECISION) {
                        add(suggestion.options.joinToString(" / "))
                    }
                    add(suggestion.sender)
                }
                Text(
                    meta.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "「${suggestion.quote.take(90)}」",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FontFamily.Default,
                )
            }
            Row {
                TextButton(onClick = onEdit) { Text("改") }
                TextButton(onClick = onDrop) {
                    Text("不要", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun KindChip(kind: SuggestionKind) {
    val color = when (kind) {
        SuggestionKind.PLACE -> MaterialTheme.colorScheme.primaryContainer
        SuggestionKind.DECISION -> MaterialTheme.colorScheme.tertiaryContainer
        SuggestionKind.TODO -> MaterialTheme.colorScheme.secondaryContainer
        SuggestionKind.BUDGET -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(shape = RoundedCornerShape(50), color = color) {
        Text(
            kind.label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun EditSuggestionDialog(
    initial: String,
    kind: SuggestionKind,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp)) {
                Text("改个名字", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "写进行程时会用这个名字（${kind.label}）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("名字") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { if (text.isNotBlank()) onSave(text.trim()) },
                        enabled = text.isNotBlank(),
                    ) { Text("保存") }
                }
            }
        }
    }
}

@Composable
private fun PasteTextDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp)) {
                Text("粘贴聊天记录", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "把微信里复制的聊天内容贴进来，格式是「·昵称 / 日期 / 正文」三行一条。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("·张三\n2026年11月7日 09:12\n我们周末去杭州吧") },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                        Text("解析")
                    }
                }
            }
        }
    }
}

private fun ChatTripExtractionSummary(extraction: com.plantogether.app.data.ChatExtraction): String {
    val parts = mutableListOf(com.plantogether.app.data.ChatTripExtractor.summary(extraction))
    extraction.destination?.let { parts += "目的地 $it" }
    extraction.startDate?.let { start ->
        val end = extraction.endDate
        parts += if (end != null) "$start 至 $end" else start
    }
    return parts.joinToString(" · ")
}
