package com.plantogether.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.plantogether.app.data.PlaceRequest
import com.plantogether.app.data.PlaceResponse
import com.plantogether.app.data.TripResponse

/** 按天分组的行程安排：手动排序、编辑、删除、评论入口。 */
@Composable
fun ItineraryTab(
    viewModel: TripDetailViewModel,
    trip: TripResponse?,
    places: List<PlaceResponse>,
) {
    val canWrite = trip?.canWrite == true
    val groups = remember(places, trip?.startDate) { groupPlaces(places, trip) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PlaceResponse?>(null) }
    var deleting by remember { mutableStateOf<PlaceResponse?>(null) }
    var lookupPlace by remember { mutableStateOf<PlaceResponse?>(null) }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (places.isEmpty()) {
                EmptyHint("行程还是空的。把大家想去的地方一个个加进来，再按天排好顺序。")
            }
            if (canWrite && places.isNotEmpty()) {
                Text(
                    "长按地点可以拖动排序",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            groups.forEach { group ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        group.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    val dayTotal = group.places.mapNotNull { it.budget }.sum()
                    if (dayTotal > 0) {
                        Text(
                            formatMoney(dayTotal) ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ReorderablePlaceList(
                    places = group.places,
                    enabled = canWrite,
                    onCommit = { orderedIds ->
                        viewModel.reorderPlaces(mergeGroupOrder(places, group.places, orderedIds))
                    },
                ) { place, index, isDragging ->
                    PlaceCard(
                        index = index + 1,
                        place = place,
                        canWrite = canWrite,
                        isDragging = isDragging,
                        onLookup = { lookupPlace = place },
                        onMoveUp = { viewModel.movePlace(place.id, -1) },
                        onMoveDown = { viewModel.movePlace(place.id, 1) },
                        onEdit = { editing = place },
                        onDelete = { deleting = place },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (canWrite) {
            Button(
                onClick = { showAdd = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text("添加地点") }
        } else {
            Text(
                "你是只读成员，可以查看行程但不能修改。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    if (showAdd || editing != null) {
        PlaceEditorDialog(
            initial = editing,
            onDismiss = {
                showAdd = false
                editing = null
            },
            onSave = { request ->
                val current = editing
                if (current == null) {
                    viewModel.addPlace(request) {
                        showAdd = false
                    }
                } else {
                    viewModel.updatePlace(current.id, request) {
                        editing = null
                    }
                }
            },
        )
    }

    deleting?.let { place ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除地点") },
            text = { Text("确定删除「${place.name}」吗？挂在这个地点下的评论也会一起删除。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlace(place.id)
                    deleting = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            },
        )
    }

    lookupPlace?.let { place ->
        LookupSheet(
            place = place,
            city = trip?.destination,
            onDismiss = { lookupPlace = null },
        )
    }
}

/**
 * 把「某一天内拖动后的顺序」合并回全局顺序：
 * 其他天的地点保持原位，只替换这一天占用的槽位。
 */
private fun mergeGroupOrder(
    all: List<PlaceResponse>,
    group: List<PlaceResponse>,
    newGroupOrder: List<Long>,
): List<Long> {
    val groupIds = group.map { it.id }.toSet()
    var cursor = 0
    return all.sortedBy { it.sortOrder }.map { place ->
        if (place.id in groupIds && cursor < newGroupOrder.size) {
            newGroupOrder[cursor++]
        } else {
            place.id
        }
    }
}

@Composable
private fun PlaceCard(
    index: Int,
    place: PlaceResponse,
    canWrite: Boolean,
    isDragging: Boolean,
    onLookup: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 0.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        "$index",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.titleMedium)
                    val timeLabel = buildString {
                        if (!place.startTime.isNullOrBlank()) append(place.startTime)
                        if (!place.endTime.isNullOrBlank()) {
                            if (isNotEmpty()) append("–")
                            append(place.endTime)
                        }
                        place.stayLength?.takeIf { it.isNotBlank() }?.let {
                            if (isNotEmpty()) append(" · ")
                            append("停留 $it")
                        }
                    }
                    if (timeLabel.isNotBlank()) {
                        Text(
                            timeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                formatMoney(place.budget)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }

            place.address?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            place.notes?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text("备注：$it", style = MaterialTheme.typography.bodySmall)
            }
            place.backupOption?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    "备选：$it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onLookup) { Text("看点评") }
                if (!place.mapLink.isNullOrBlank()) {
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(place.mapLink)))
                        }
                    }) { Text("地图") }
                }
                Spacer(Modifier.weight(1f))
                place.createdByName?.let {
                    Text(
                        "由 $it 添加",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (canWrite) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onMoveUp) { Text("↑") }
                    TextButton(onClick = onMoveDown) { Text("↓") }
                    TextButton(onClick = onEdit) { Text("编辑") }
                    TextButton(onClick = onDelete) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyHint(text: String) {    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlaceEditorDialog(
    initial: PlaceResponse?,
    onDismiss: () -> Unit,
    onSave: (PlaceRequest) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var date by remember(initial) { mutableStateOf(initial?.date.orEmpty()) }
    var startTime by remember(initial) { mutableStateOf(initial?.startTime.orEmpty()) }
    var endTime by remember(initial) { mutableStateOf(initial?.endTime.orEmpty()) }
    var address by remember(initial) { mutableStateOf(initial?.address.orEmpty()) }
    var latitude by remember(initial) { mutableStateOf(initial?.latitude?.toString().orEmpty()) }
    var longitude by remember(initial) { mutableStateOf(initial?.longitude?.toString().orEmpty()) }
    var budget by remember(initial) {
        mutableStateOf(initial?.budget?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }.orEmpty())
    }
    var stayLength by remember(initial) { mutableStateOf(initial?.stayLength.orEmpty()) }
    var notes by remember(initial) { mutableStateOf(initial?.notes.orEmpty()) }
    var backup by remember(initial) { mutableStateOf(initial?.backupOption.orEmpty()) }
    var mapLink by remember(initial) { mutableStateOf(initial?.mapLink.orEmpty()) }
    var tried by remember { mutableStateOf(false) }

    val latitudeResult = parseOptionalNumber(latitude)
    val longitudeResult = parseOptionalNumber(longitude)
    val budgetResult = parseOptionalNumber(budget)
    val hasTimeError = startTime.isNotBlank() && parseIsoTime(startTime) == null ||
        endTime.isNotBlank() && parseIsoTime(endTime) == null
    val timeRangeError = run {
        val s = parseIsoTime(startTime)
        val e = parseIsoTime(endTime)
        if (s != null && e != null && e.isBefore(s)) "结束时间不能早于开始时间" else null
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    if (initial == null) "添加地点" else "编辑地点",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    OutlinedTextField(
                        name,
                        { name = it },
                        label = { Text("名称 *") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        address,
                        { address = it },
                        label = { Text("地址") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    DateField(date, { date = it }, "日期（留空=未定）")
                    Spacer(Modifier.height(8.dp))
                    Row {
                        TimeField(
                            startTime,
                            { startTime = it },
                            "开始",
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        TimeField(
                            endTime,
                            { endTime = it },
                            "结束",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (hasTimeError || timeRangeError != null) {
                        Text(
                            timeRangeError ?: "时间格式不对，请重新选一次",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        ValidatedField(
                            value = latitude,
                            onValueChange = { latitude = it },
                            label = "纬度",
                            error = if (tried) latitudeResult.errorOf() else null,
                            keyboardOptions = numberKeyboard(),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        ValidatedField(
                            value = longitude,
                            onValueChange = { longitude = it },
                            label = "经度",
                            error = if (tried) longitudeResult.errorOf() else null,
                            keyboardOptions = numberKeyboard(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        ValidatedField(
                            value = budget,
                            onValueChange = { budget = it },
                            label = "预算 ¥",
                            error = if (tried) budgetResult.errorOf() else null,
                            keyboardOptions = numberKeyboard(),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            stayLength,
                            { stayLength = it },
                            label = { Text("停留时长") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        mapLink,
                        { mapLink = it },
                        label = { Text("地图链接（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        notes,
                        { notes = it },
                        label = { Text("备注") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        backup,
                        { backup = it },
                        label = { Text("备选方案") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            tried = true
                            val blocking = name.isBlank() ||
                                latitudeResult.errorOf() != null ||
                                longitudeResult.errorOf() != null ||
                                budgetResult.errorOf() != null ||
                                hasTimeError || timeRangeError != null
                            if (blocking) return@TextButton
                            onSave(
                                PlaceRequest(
                                    name = name.trim(),
                                    address = address.trim().ifBlank { null },
                                    latitude = latitudeResult.orNull(),
                                    longitude = longitudeResult.orNull(),
                                    date = date.ifBlank { null },
                                    startTime = startTime.ifBlank { null },
                                    endTime = endTime.ifBlank { null },
                                    budget = budgetResult.orNull(),
                                    stayLength = stayLength.trim().ifBlank { null },
                                    notes = notes.trim().ifBlank { null },
                                    backupOption = backup.trim().ifBlank { null },
                                    mapLink = mapLink.trim().ifBlank { null },
                                    sortOrder = initial?.sortOrder ?: 0,
                                    version = initial?.version,
                                ),
                            )
                        },
                    ) { Text("保存") }
                }
            }
        }
    }
}
