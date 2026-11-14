package com.plantogether.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.plantogether.app.data.DecisionOptionResponse
import com.plantogether.app.data.DecisionRequest
import com.plantogether.app.data.DecisionResponse
import com.plantogether.app.data.TripResponse

/** 群决策：一个问题上大家投票，最后标成「已定案」。 */
@Composable
fun DecisionsTab(
    viewModel: TripDetailViewModel,
    trip: TripResponse?,
    decisions: List<DecisionResponse>,
) {
    val canWrite = trip?.canWrite == true
    var showCreate by remember { mutableStateOf(false) }
    var addingOptionFor by remember { mutableStateOf<DecisionResponse?>(null) }
    var confirming by remember { mutableStateOf<Pair<DecisionResponse, DecisionOptionResponse>?>(null) }
    var deleting by remember { mutableStateOf<DecisionResponse?>(null) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (decisions.isEmpty()) {
                item {
                    EmptyState(
                        title = "还没有决策",
                        hint = "遇到「去哪吃」「住哪个片区」这类分歧，发起一个投票让大家一起定。",
                        icon = "⇄",
                    )
                }
            }
            items(decisions, key = { it.id }) { decision ->
                DecisionCard(
                    decision = decision,
                    canWrite = canWrite,
                    onVote = { optionId -> viewModel.vote(decision.id, optionId) },
                    onDecide = { option -> confirming = decision to option },
                    onReopen = { viewModel.reopenDecision(decision) },
                    onCancel = { viewModel.cancelDecision(decision) },
                    onAddOption = { addingOptionFor = decision },
                    onRemoveOption = { optionId -> viewModel.removeDecisionOption(decision.id, optionId) },
                    onDelete = { deleting = decision },
                )
            }
        }

        if (canWrite) {
            Button(
                onClick = { showCreate = true },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 10.dp),
            ) {
                Text("发起新决策", style = MaterialTheme.typography.titleSmall)
            }
        }
    }

    if (showCreate) {
        CreateDecisionDialog(
            onDismiss = { showCreate = false },
            onSave = { request ->
                viewModel.createDecision(request) { showCreate = false }
            },
        )
    }

    addingOptionFor?.let { decision ->
        AddOptionDialog(
            decisionTitle = decision.title,
            onDismiss = { addingOptionFor = null },
            onSave = { title ->
                viewModel.addDecisionOption(decision.id, title) { addingOptionFor = null }
            },
        )
    }

    confirming?.let { (decision, option) ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("定案") },
            text = { Text("把「${option.title}」作为「${decision.title}」的最终结果？定案后仍然可以重新开放。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.decide(decision, option.id)
                    confirming = null
                }) { Text("定案") }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("取消") } },
        )
    }

    deleting?.let { decision ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除决策") },
            text = { Text("确定删除「${decision.title}」以及它的所有选项和投票吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDecision(decision.id)
                    deleting = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun DecisionCard(
    decision: DecisionResponse,
    canWrite: Boolean,
    onVote: (Long) -> Unit,
    onDecide: (DecisionOptionResponse) -> Unit,
    onReopen: () -> Unit,
    onCancel: () -> Unit,
    onAddOption: () -> Unit,
    onRemoveOption: (Long) -> Unit,
    onDelete: () -> Unit,
) {
    val statusText = when {
        decision.isDecided -> "已定案"
        decision.isCancelled -> "已取消"
        else -> "投票中"
    }
    val statusColor = when {
        decision.isDecided -> MaterialTheme.colorScheme.primaryContainer
        decision.isCancelled -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val statusTextColor = when {
        decision.isDecided -> MaterialTheme.colorScheme.onPrimaryContainer
        decision.isCancelled -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    decision.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Pill(
                    statusText,
                    container = statusColor,
                    contentColor = statusTextColor,
                )
            }
            decision.notes?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))

            val totalVotes = decision.totalVotes
            decision.options.forEach { option ->
                val isWinner = decision.decidedOptionId == option.id
                val container = when {
                    option.votedByMe -> MaterialTheme.colorScheme.primaryContainer
                    isWinner -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = container,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable(enabled = canWrite && !decision.isCancelled) { onVote(option.id) },
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                option.title,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            if (isWinner) {
                                Text(
                                    "已定",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                if (option.votedByMe) "已投 · ${option.voteCount} 票" else "${option.voteCount} 票",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        if (totalVotes > 0) {
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { option.voteCount.toFloat() / totalVotes },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp),
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                            )
                        }
                        if (option.voterNames.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                option.voterNames.joinToString("、"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        option.notes?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(2.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        if (canWrite && !decision.isDecided && !decision.isCancelled) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(
                                    onClick = { onDecide(option) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                ) { Text("定为结果", style = MaterialTheme.typography.labelLarge) }
                                TextButton(
                                    onClick = { onRemoveOption(option.id) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        "删选项",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (decision.options.isEmpty()) {
                Text(
                    "还没有选项，先加两个让大家选。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val meta = buildList {
                decision.createdByName?.let { add("$it 发起") }
                if (totalVotes > 0) add("$totalVotes 人已投票")
            }
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    meta.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (canWrite) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onAddOption,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) { Text("加选项", style = MaterialTheme.typography.labelLarge) }
                    if (decision.isDecided) {
                        TextButton(
                            onClick = onReopen,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) { Text("重新开放", style = MaterialTheme.typography.labelLarge) }
                    } else if (!decision.isCancelled) {
                        TextButton(
                            onClick = onCancel,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) { Text("取消决策", style = MaterialTheme.typography.labelLarge) }
                    }
                    if (decision.canDelete) {
                        TextButton(
                            onClick = onDelete,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                "删除",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateDecisionDialog(
    onDismiss: () -> Unit,
    onSave: (DecisionRequest) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var optionsText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp)) {
                Text("发起决策", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    OutlinedTextField(
                        title,
                        { title = it },
                        label = { Text("要决定什么 *") },
                        placeholder = { Text("例如：周六晚上吃什么") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        notes,
                        { notes = it },
                        label = { Text("补充说明（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        optionsText,
                        { optionsText = it },
                        label = { Text("候选选项（一行一个）") },
                        placeholder = { Text("知味观\n新白鹿\n回酒店点外卖") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        maxLines = 6,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            val options = optionsText.split('\n')
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                            onSave(
                                DecisionRequest(
                                    title = title.trim(),
                                    notes = notes.trim().ifBlank { null },
                                    options = options,
                                ),
                            )
                        },
                        enabled = title.isNotBlank(),
                    ) { Text("发起") }
                }
            }
        }
    }
}

@Composable
private fun AddOptionDialog(
    decisionTitle: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var title by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp)) {
                Text("添加选项", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text("决策：$decisionTitle", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    title,
                    { title = it },
                    label = { Text("选项内容 *") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { onSave(title.trim()) },
                        enabled = title.isNotBlank(),
                    ) { Text("添加") }
                }
            }
        }
    }
}
