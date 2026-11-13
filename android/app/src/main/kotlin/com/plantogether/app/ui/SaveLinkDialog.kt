package com.plantogether.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantogether.app.data.TripResponse

/** 从系统分享面板分享进来的链接：选一个行程、补上自己的标题和说明再保存。 */
@Composable
fun SaveLinkDialog(
    viewModel: MainViewModel,
    trips: List<TripResponse>,
    onOpenTrip: (Long) -> Unit = {},
) {
    val share = viewModel.pendingShare.collectAsStateWithLifecycle().value ?: return
    val shareError by viewModel.shareError.collectAsStateWithLifecycle()

    var selectedTrip by remember(share.eventId) { mutableStateOf(trips.firstOrNull()) }
    var url by remember(share.eventId) { mutableStateOf(share.url) }
    var title by remember(share.eventId) { mutableStateOf(share.title.orEmpty()) }
    var notes by remember(share.eventId) { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    // 行程列表可能比弹窗晚一步加载完成，加载后自动选中第一趟
    LaunchedEffect(trips) {
        if (selectedTrip == null) selectedTrip = trips.firstOrNull()
    }

    Dialog(onDismissRequest = {
        viewModel.clearShareError()
        viewModel.clearPendingShare()
    }) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp)) {
                Text("保存链接到行程", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))

                if (trips.isEmpty()) {
                    Text(
                        "你还没有行程。先创建一个行程，再回来保存链接。",
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Box {
                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedTrip?.title ?: "选择行程")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            trips.forEach { trip ->
                                DropdownMenuItem(
                                    text = { Text(trip.title) },
                                    onClick = {
                                        selectedTrip = trip
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        url,
                        { url = it },
                        label = { Text("链接") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        title,
                        { title = it },
                        label = { Text("标题（可自己改）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        notes,
                        { notes = it },
                        label = { Text("为什么想去 / 说明（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                shareError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    val trip = selectedTrip
                    if (trip != null) {
                        TextButton(onClick = {
                            viewModel.clearPendingShare()
                            viewModel.clearShareError()
                            onOpenTrip(trip.id)
                        }) { Text("打开行程") }
                    }
                    TextButton(onClick = {
                        viewModel.clearPendingShare()
                        viewModel.clearShareError()
                    }) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val trip = selectedTrip
                            if (trip != null && url.isNotBlank()) {
                                viewModel.addLinkFromShare(
                                    tripId = trip.id,
                                    url = url.trim(),
                                    title = title.trim().ifBlank { null },
                                    notes = notes.trim().ifBlank { null },
                                    onDone = {
                                        viewModel.clearShareError()
                                        viewModel.clearPendingShare()
                                    },
                                )
                            }
                        },
                        enabled = selectedTrip != null && url.isNotBlank(),
                    ) { Text("保存") }
                }
            }
        }
    }
}
