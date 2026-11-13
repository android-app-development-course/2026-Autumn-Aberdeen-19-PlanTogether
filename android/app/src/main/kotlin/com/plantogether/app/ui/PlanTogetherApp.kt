package com.plantogether.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun PlanTogetherApp(
    sharedUrl: String? = null,
    sharedTitle: String? = null,
    shareEventId: Long = 0L,
    inviteCode: String? = null,
    inviteEventId: Long = 0L,
    viewModel: MainViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val bootState by viewModel.bootState.collectAsStateWithLifecycle()
    val pendingShare by viewModel.pendingShare.collectAsStateWithLifecycle()
    val pendingInvite by viewModel.pendingInviteCode.collectAsStateWithLifecycle()
    val joinState by viewModel.joinState.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val tripsState by viewModel.tripsState.collectAsStateWithLifecycle()
    val isLoggedIn = authState is MainViewModel.AuthState.Success

    // 每次从别的 App 分享进来都带一个新的 eventId，保证重复分享同一链接也能再次弹窗
    LaunchedEffect(shareEventId) {
        if (sharedUrl != null) viewModel.setPendingShare(sharedUrl, sharedTitle, shareEventId)
    }

    LaunchedEffect(pendingShare, isLoggedIn) {
        if (pendingShare != null && isLoggedIn) viewModel.loadTrips()
    }

    // 深链邀请：先记下来，登录后自动尝试加入
    LaunchedEffect(inviteEventId) {
        if (inviteCode != null) viewModel.deliverInviteCode(inviteCode)
    }

    LaunchedEffect(pendingInvite, isLoggedIn) {
        val code = pendingInvite
        if (code != null && isLoggedIn && joinState !is MainViewModel.JoinState.Loading) {
            viewModel.joinTrip(code)
        }
    }

    // 加入成功：直接进这趟行程
    LaunchedEffect(joinState) {
        val success = joinState as? MainViewModel.JoinState.Success
        if (success != null && pendingInvite != null) {
            viewModel.clearPendingInviteCode()
            viewModel.resetJoinState()
            navController.navigate("trip/${success.trip.id}")
        }
        val error = joinState as? MainViewModel.JoinState.Error
        if (error != null && pendingInvite != null) {
            Toast.makeText(context, error.message, Toast.LENGTH_LONG).show()
            viewModel.clearPendingInviteCode()
            viewModel.resetJoinState()
        }
    }

    if (bootState is MainViewModel.BootState.Loading) {
        AppSplash()
        return
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = if (isLoggedIn) "trips" else "login",
        ) {
            composable("login") {
                LoginScreen(
                    viewModel = viewModel,
                    onLoggedIn = {
                        navController.navigate("trips") {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                )
            }
            composable("trips") {
                TripListScreen(
                    viewModel = viewModel,
                    onOpenTrip = { id -> navController.navigate("trip/$id") },
                    onCreateTrip = { navController.navigate("create") },
                    onJoinedTrip = { id ->
                        navController.navigate("trip/$id") {
                            popUpTo("trips") { inclusive = false }
                        }
                    },
                    onLogout = {
                        viewModel.logout()
                        navController.navigate("login") {
                            popUpTo("trips") { inclusive = true }
                        }
                    },
                )
            }
            composable("create") {
                CreateTripScreen(
                    viewModel = viewModel,
                    onDone = { navController.popBackStack() },
                )
            }
            composable("trip/{tripId}") { backStackEntry ->
                val tripId = backStackEntry.arguments?.getString("tripId")?.toLongOrNull() ?: 0L
                TripDetailScreen(
                    viewModel = viewModel,
                    tripId = tripId,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        if (pendingShare != null && isLoggedIn) {
            val trips = (tripsState as? MainViewModel.TripsState.Success)?.trips ?: emptyList()
            SaveLinkDialog(
                viewModel = viewModel,
                trips = trips,
                onOpenTrip = { id -> navController.navigate("trip/$id") },
            )
        }
    }
}

@Composable
private fun AppSplash() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("PlanTogether", style = MaterialTheme.typography.headlineMedium)
        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}
