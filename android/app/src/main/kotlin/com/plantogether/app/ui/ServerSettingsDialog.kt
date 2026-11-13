package com.plantogether.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 服务器地址设置。
 *
 * 之前地址写死成 http://127.0.0.1:8080/，靠 `adb reverse` 才能用——
 * 也就是说只有开发机上跑得起来，装到队友手机上一个字都连不上。
 * 现在可以填局域网 IP 或线上域名，存本机，下次启动自动生效。
 */
@Composable
fun ServerSettingsDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
) {
    val current by viewModel.serverAddress.collectAsStateWithLifecycle()
    val check by viewModel.serverCheck.collectAsStateWithLifecycle()
    var text by remember(current) { mutableStateOf(current) }

    LaunchedEffect(Unit) { viewModel.resetServerCheck() }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                Text("服务器设置", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "填后端地址。同一个 WiFi 下用电脑的局域网 IP（如 http://192.168.1.8:8080）。" +
                        "如果是手机上调试、电脑上跑后端，先执行 adb reverse tcp:8080 tcp:8080，再用默认的 127.0.0.1。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; viewModel.resetServerCheck() },
                    label = { Text("后端地址") },
                    placeholder = { Text("http://192.168.1.8:8080") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "当前生效：$current",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))
                when (val c = check) {
                    is MainViewModel.ServerCheck.Testing ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("正在试连…", style = MaterialTheme.typography.bodySmall)
                        }

                    is MainViewModel.ServerCheck.Ok ->
                        Text(
                            "✓ ${c.display}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )

                    is MainViewModel.ServerCheck.Failed ->
                        Text(
                            "✗ ${c.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )

                    MainViewModel.ServerCheck.Idle -> Unit
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { viewModel.resetServerAddress(); text = viewModel.serverAddress.value }) {
                        Text("恢复默认")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) { Text("关闭") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { viewModel.saveServerAddress(text) }) { Text("保存并测试") }
                }
            }
        }
    }
}
