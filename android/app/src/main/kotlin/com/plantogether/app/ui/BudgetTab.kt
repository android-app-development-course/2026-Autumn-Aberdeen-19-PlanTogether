package com.plantogether.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
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
import com.plantogether.app.data.PlaceResponse
import com.plantogether.app.data.TripRequest
import com.plantogether.app.data.TripResponse

/**
 * 预算页：把成员手填的金额汇总起来，按天和人头摊开。
 * 全部是本地位数，不含任何实时票价或汇率。
 */
@Composable
fun BudgetTab(
    viewModel: TripDetailViewModel,
    trip: TripResponse?,
    places: List<PlaceResponse>,
) {
    val planned = places.mapNotNull { it.budget }.sum()
    val headcount = (trip?.groupSize ?: trip?.memberCount ?: 1).coerceAtLeast(1)
    val target = trip?.budgetTarget
    var showTargetDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("已计划支出", style = MaterialTheme.typography.labelMedium)
                Text(
                    formatMoney(planned) ?: "¥0",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "按 $headcount 人摊，人均 ${formatMoney(planned / headcount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (target != null && target > 0) {
                    Spacer(Modifier.height(12.dp))
                    val fraction = (planned / target).coerceIn(0.0, 1.0).toFloat()
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    val diff = target - planned
                    Text(
                        "预算目标 ${formatMoney(target)} · " +
                            if (diff >= 0) "还剩 ${formatMoney(diff)}" else "已超出 ${formatMoney(-diff)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (diff >= 0) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "还没有设置总预算目标。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (trip?.isOwner == true) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showTargetDialog = true }) {
                        Text(if (target == null) "设置预算目标" else "修改预算目标")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("按天拆分", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        val groups = groupPlaces(places, trip)
        if (groups.isEmpty()) {
            Text(
                "还没有地点，先去「行程」里添加吧。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            groups.forEach { group ->
                val dayTotal = group.places.mapNotNull { it.budget }.sum()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(group.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        formatMoney(dayTotal) ?: "—",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (dayTotal > 0) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                HorizontalDivider()
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("费用明细", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        val withBudget = places.filter { it.budget != null }.sortedBy { it.sortOrder }
        if (withBudget.isEmpty()) {
            Text(
                "还没有任何地点填了预算。编辑地点时可以填一个大概金额。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            withBudget.forEach { place ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(place.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            formatDayLabel(place.date, trip?.startDate),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(formatMoney(place.budget) ?: "", style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider()
            }
        }

        Spacer(Modifier.height(16.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "这里的金额全部由成员手动填写，不包含实时票价、汇率或任何平台报价。",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(10.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showTargetDialog && trip != null) {
        BudgetTargetDialog(
            current = trip.budgetTarget,
            onDismiss = { showTargetDialog = false },
            onSave = { value ->
                viewModel.updateTrip(
                    TripRequest(
                        title = trip.title,
                        destination = trip.destination,
                        startDate = trip.startDate,
                        endDate = trip.endDate,
                        groupSize = trip.groupSize,
                        budgetTarget = value,
                        visibility = trip.visibility,
                        version = trip.version,
                    ),
                ) { showTargetDialog = false }
            },
        )
    }
}

@Composable
private fun BudgetTargetDialog(
    current: Double?,
    onDismiss: () -> Unit,
    onSave: (Double?) -> Unit,
) {
    var value by remember {
        mutableStateOf(current?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }.orEmpty())
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp)) {
                Text("预算目标", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "填一个大概的总预算，用来对比已计划支出。留空表示不设置。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("总预算 ¥") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onSave(value.trim().toDoubleOrNull()) }) { Text("保存") }
                }
            }
        }
    }
}
