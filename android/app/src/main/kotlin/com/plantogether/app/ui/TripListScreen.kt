package com.plantogether.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantogether.app.data.TripResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripListScreen(
    viewModel: MainViewModel,
    onOpenTrip: (Long) -> Unit,
    onCreateTrip: () -> Unit,
    onJoinedTrip: (Long) -> Unit,
    onLogout: () -> Unit,
    onChatImport: () -> Unit = {},
) {
    val state by viewModel.tripsState.collectAsStateWithLifecycle()
    val seenMap by viewModel.seenRevisions.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val seen = seenMap.orEmpty()
    var showJoin by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadTrips() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 标题区：左边是大标题，右边只留一个「更多」——不再把四个按钮平铺在标题旁边
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "我的行程",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                val trips = (state as? MainViewModel.TripsState.Success)?.trips
                Text(
                    when {
                        trips == null -> "正在同步…"
                        trips.isEmpty() -> "还没有行程"
                        else -> "${trips.size} 个行程 · 和队友一起改"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                TextButton(onClick = { showMore = true }) { Text("更多") }
                androidx.compose.material3.DropdownMenu(
                    expanded = showMore,
                    onDismissRequest = { showMore = false },
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("从聊天记录导入") },
                        onClick = {
                            showMore = false
                            onChatImport()
                        },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("用邀请码加入") },
                        onClick = {
                            showMore = false
                            showJoin = true
                        },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("退出登录", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMore = false
                            onLogout()
                        },
                    )
                }
            }
        }

        // 主行动：新建行程做成整宽按钮，是这一屏唯一的重色块
        Button(
            onClick = onCreateTrip,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .height(52.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("＋ 新建行程", style = MaterialTheme.typography.titleSmall)
        }

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { viewModel.refreshTrips() },
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            when (val s = state) {
                is MainViewModel.TripsState.Loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                is MainViewModel.TripsState.Error ->
                    EmptyState(
                        title = s.message,
                        hint = "下拉可以重试，或者点上面的按钮新建一个。",
                        icon = "!",
                    )

                is MainViewModel.TripsState.Success ->
                    if (s.trips.isEmpty()) {
                        EmptyState(
                            title = "还没有行程",
                            hint = "新建一个，或者用队友发来的邀请码加入。",
                            icon = "✈",
                        )
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = 4.dp,
                                bottom = 24.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(s.trips) { trip ->
                                TripCard(
                                    trip = trip,
                                    hasUnseen = trip.revision > (seen[trip.id] ?: 0L),
                                ) { onOpenTrip(trip.id) }
                            }
                        }
                    }
            }
        }
    }

    if (showJoin) {
        JoinTripDialog(
            viewModel = viewModel,
            onDismiss = {
                showJoin = false
                viewModel.resetJoinState()
            },
            onJoined = { tripId ->
                showJoin = false
                viewModel.resetJoinState()
                onJoinedTrip(tripId)
            },
        )
    }
}

@Composable
private fun TripCard(trip: TripResponse, hasUnseen: Boolean, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                DestinationBadge(trip.destination, size = 52)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (hasUnseen) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .background(MaterialTheme.colorScheme.secondary, CircleShape),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            trip.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        tripDateRange(trip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill("${trip.memberCount} 人")
                        Pill(roleLabel(trip.role))
                        if (!trip.isOwner) Pill("队友的")
                    }
                }
            }
        }
    }
}

@Composable
private fun JoinTripDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onJoined: (Long) -> Unit,
) {
    val state by viewModel.joinState.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        val success = state as? MainViewModel.JoinState.Success
        if (success != null) onJoined(success.trip.id)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(22.dp)) {
                Text("用邀请码加入", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "输入队友分享给你的 8 位邀请码。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("邀请码") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                (state as? MainViewModel.JoinState.Error)?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    val loading = state is MainViewModel.JoinState.Loading
                    Button(
                        onClick = { viewModel.joinTrip(code.trim()) },
                        enabled = code.isNotBlank() && !loading,
                        shape = MaterialTheme.shapes.medium,
                    ) { Text(if (loading) "加入中…" else "加入") }
                }
            }
        }
    }
}
