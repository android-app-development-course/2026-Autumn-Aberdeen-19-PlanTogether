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
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "已计划支出",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Pill("$headcount 人", container = MaterialTheme.colorScheme.primaryContainer)
                }
                Spacer(Modifier.height(4.dp))
                PriceTag(planned, emphasize = true)
                Spacer(Modifier.height(2.dp))
                Text(
                    "人均 ${formatMoney(planned / headcount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (target != null && target > 0) {
                    Spacer(Modifier.height(14.dp))
                    val fraction = (planned / target).coerceIn(0.0, 1.0).toFloat()
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
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

        Spacer(Modifier.height(18.dp))
        SectionLabel("按天拆分")
        Spacer(Modifier.height(6.dp))
        val groups = groupPlaces(places, trip)
        if (groups.isEmpty()) {
            Text(
                "还没有地点，先去「行程」里添加吧。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        } else {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    groups.forEachIndexed { index, group ->
                        val dayTotal = group.places.mapNotNull { it.budget }.sum()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                group.label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                formatMoney(dayTotal) ?: "—",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (dayTotal > 0) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = if (dayTotal > 0) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                        if (index < groups.lastIndex) SoftDivider(Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("费用明细")
        Spacer(Modifier.height(6.dp))
        val withBudget = places.filter { it.budget != null }.sortedBy { it.sortOrder }
        if (withBudget.isEmpty()) {
            Text(
                "还没有任何地点填了预算。编辑地点时可以填一个大概金额。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        } else {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    withBudget.forEachIndexed { index, place ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(place.name, style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.height(1.dp))
                                Text(
                                    formatDayLabel(place.date, trip?.startDate),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            PriceTag(place.budget)
                        }
                        if (index < withBudget.lastIndex) SoftDivider(Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "这里的金额全部由成员手动填写，不包含实时票价、汇率或任何平台报价。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
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
    var tried by remember { mutableStateOf(false) }
    val parsed = parseOptionalNumber(value)

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
                ValidatedField(
                    value = value,
                    onValueChange = { value = it },
                    label = "总预算 ¥",
                    error = if (tried) parsed.errorOf() else null,
                    keyboardOptions = numberKeyboard(),
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            tried = true
                            if (parsed.errorOf() != null) return@TextButton
                            onSave(parsed.orNull())
                        },
                    ) { Text("保存") }
                }
            }
        }
    }
}
