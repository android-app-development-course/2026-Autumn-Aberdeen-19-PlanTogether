package com.plantogether.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantogether.app.data.TripRequest

@Composable
fun CreateTripScreen(
    viewModel: MainViewModel,
    onDone: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var groupSize by remember { mutableStateOf("") }
    var budgetTarget by remember { mutableStateOf("") }

    val createState by viewModel.createState.collectAsStateWithLifecycle()

    LaunchedEffect(createState) {
        if (createState is MainViewModel.CreateState.Success) onDone()
    }

    val loading = createState is MainViewModel.CreateState.Loading
    val error = (createState as? MainViewModel.CreateState.Error)?.message

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        TextButton(onClick = { viewModel.resetCreateState(); onDone() }) { Text("← 返回") }
        Spacer(Modifier.height(8.dp))
        Text("新建行程", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("行程标题 *") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = destination,
            onValueChange = { destination = it },
            label = { Text("目的地") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = startDate,
            onValueChange = { startDate = it },
            label = { Text("开始日期 (yyyy-MM-dd，可选)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = endDate,
            onValueChange = { endDate = it },
            label = { Text("结束日期 (yyyy-MM-dd，可选)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = groupSize,
            onValueChange = { groupSize = it },
            label = { Text("人数（可选）") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = budgetTarget,
            onValueChange = { budgetTarget = it },
            label = { Text("总预算 ¥（可选）") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                viewModel.createTrip(
                    TripRequest(
                        title = title.trim(),
                        destination = destination.trim().ifBlank { null },
                        startDate = startDate.trim().ifBlank { null },
                        endDate = endDate.trim().ifBlank { null },
                        groupSize = groupSize.trim().toIntOrNull(),
                        budgetTarget = budgetTarget.trim().toDoubleOrNull(),
                    )
                )
            },
            enabled = title.isNotBlank() && !loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (loading) "创建中..." else "创建")
        }
    }
}
