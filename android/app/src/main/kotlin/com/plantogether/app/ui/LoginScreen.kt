package com.plantogether.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    onLoggedIn: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var isRegister by remember { mutableStateOf(false) }
    var showServerSettings by remember { mutableStateOf(false) }

    val state by viewModel.authState.collectAsStateWithLifecycle()
    val serverAddress by viewModel.serverAddress.collectAsStateWithLifecycle()
    val authFailed = state is MainViewModel.AuthState.Error

    LaunchedEffect(state) {
        if (state is MainViewModel.AuthState.Success) onLoggedIn()
    }

    val loading = state is MainViewModel.AuthState.Loading
    val error = (state as? MainViewModel.AuthState.Error)?.message

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("PlanTogether", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("让团队旅行计划更简单", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))

        if (isRegister) {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("昵称") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("邮箱") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                if (isRegister) viewModel.register(email.trim(), password, displayName.trim())
                else viewModel.login(email.trim(), password)
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(if (isRegister) "注册" else "登录")
            }
        }
        Spacer(Modifier.height(8.dp))

        TextButton(onClick = {
            isRegister = !isRegister
            viewModel.resetAuthState()
        }) {
            Text(if (isRegister) "已有账号？去登录" else "没有账号？去注册")
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "服务器：$serverAddress",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { showServerSettings = true }) {
            Text(if (authFailed) "连不上？检查服务器地址" else "服务器设置")
        }
    }

    if (showServerSettings) {
        ServerSettingsDialog(
            viewModel = viewModel,
            onDismiss = { showServerSettings = false },
        )
    }
}
