package com.plantogether.app.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
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
) {
    val state by viewModel.tripsState.collectAsStateWithLifecycle()
    val seenMap by viewModel.seenRevisions.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val seen = seenMap.orEmpty()
    var showJoin by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadTrips() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("我的行程", style = MaterialTheme.typography.headlineSmall)
            Row {
                TextButton(onClick = { showJoin = true }) { Text("加入行程") }
                TextButton(onClick = onCreateTrip) { Text("新建") }
                TextButton(onClick = onLogout) { Text("退出") }
            }
        }
        Spacer(Modifier.height(12.dp))

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
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(s.message, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { viewModel.loadTrips() }) { Text("重试") }
                            Text(
                                "下拉也可以重试",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                is MainViewModel.TripsState.Success ->
                    if (s.trips.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("还没有行程")
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "点右上角「新建」，或者用队友发来的邀请码「加入行程」",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasUnseen) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    trip.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (!trip.isOwner) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            "队友的行程",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            if (!trip.destination.isNullOrBlank()) {
                Text(trip.destination, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                tripDateRange(trip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            val meta = buildList {
                add("${trip.memberCount} 人")
                if (!trip.isOwner) add("创建者：${trip.ownerName}")
                add(roleLabel(trip.role))
            }
            Text(
                meta.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp)) {
                Text("加入行程", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "输入队友分享给你的 8 位邀请码。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("邀请码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                (state as? MainViewModel.JoinState.Error)?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    val loading = state is MainViewModel.JoinState.Loading
                    TextButton(
                        onClick = { viewModel.joinTrip(code.trim()) },
                        enabled = code.isNotBlank() && !loading,
                    ) { Text(if (loading) "加入中…" else "加入") }
                }
            }
        }
    }
}
