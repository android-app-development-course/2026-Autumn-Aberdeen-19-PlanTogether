package com.plantogether.app.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.plantogether.app.data.LinkRequest
import com.plantogether.app.data.LinkResponse
import com.plantogether.app.data.TripResponse

/** 链接收藏箱：保存大家分享进来的链接 + 自己的标题、标签、说明。 */
@Composable
fun LinksTab(
    viewModel: TripDetailViewModel,
    trip: TripResponse?,
    links: List<LinkResponse>,
) {
    val canWrite = trip?.canWrite == true
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LinkResponse?>(null) }
    var deleting by remember { mutableStateOf<LinkResponse?>(null) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (links.isEmpty()) {
                item {
                    EmptyState(
                        title = "还没有收藏链接",
                        hint = "可以从其他 App 直接分享进来，也可以在这里手动粘贴。",
                        icon = "🔗",
                    )
                }
            }
            items(links, key = { it.id }) { link ->
                LinkCard(
                    link = link,
                    canWrite = canWrite,
                    onEdit = { editing = link },
                    onDelete = { deleting = link },
                )
            }
        }

        if (canWrite) {
            Button(
                onClick = { showAdd = true },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 10.dp),
            ) {
                Text("收藏链接", style = MaterialTheme.typography.titleSmall)
            }
        }
    }

    if (showAdd || editing != null) {
        LinkEditorDialog(
            initial = editing,
            onDismiss = {
                showAdd = false
                editing = null
            },
            onSave = { request ->
                val current = editing
                if (current == null) {
                    viewModel.addLink(request) { showAdd = false }
                } else {
                    viewModel.updateLink(current.id, request) { editing = null }
                }
            },
        )
    }

    deleting?.let { link ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除链接") },
            text = { Text("确定删除「${link.title ?: link.url}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteLink(link.id)
                    deleting = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun LinkCard(
    link: LinkResponse,
    canWrite: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                link.title?.takeIf { it.isNotBlank() } ?: link.url,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                link.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            link.notes?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }

            val meta = buildList {
                link.tags?.takeIf { it.isNotBlank() }?.let { add("#$it") }
                link.source?.takeIf { it.isNotBlank() }?.let { add("来源：$it") }
                link.createdByName?.let { add("由 $it 收藏") }
            }
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    meta.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(6.dp))
            SoftDivider()
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                ) { Text("打开", style = MaterialTheme.typography.labelLarge) }
                if (canWrite) {
                    TextButton(
                        onClick = onEdit,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    ) { Text("编辑", style = MaterialTheme.typography.labelLarge) }
                    TextButton(
                        onClick = onDelete,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
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

@Composable
private fun LinkEditorDialog(
    initial: LinkResponse?,
    onDismiss: () -> Unit,
    onSave: (LinkRequest) -> Unit,
) {
    var url by remember(initial) { mutableStateOf(initial?.url.orEmpty()) }
    var title by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var source by remember(initial) { mutableStateOf(initial?.source.orEmpty()) }
    var tags by remember(initial) { mutableStateOf(initial?.tags.orEmpty()) }
    var notes by remember(initial) { mutableStateOf(initial?.notes.orEmpty()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    if (initial == null) "收藏链接" else "编辑链接",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    OutlinedTextField(
                        url,
                        { url = it },
                        label = { Text("链接 *") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        title,
                        { title = it },
                        label = { Text("标题（自己写）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        source,
                        { source = it },
                        label = { Text("来源（大众点评 / B站…）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        tags,
                        { tags = it },
                        label = { Text("标签（逗号分隔）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        notes,
                        { notes = it },
                        label = { Text("说明 / 为什么想去") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            onSave(
                                LinkRequest(
                                    url = url.trim(),
                                    title = title.trim().ifBlank { null },
                                    source = source.trim().ifBlank { null },
                                    tags = tags.trim().ifBlank { null },
                                    notes = notes.trim().ifBlank { null },
                                    version = initial?.version,
                                ),
                            )
                        },
                        enabled = url.isNotBlank(),
                    ) { Text("保存") }
                }
            }
        }
    }
}
