package com.plantogether.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

    // 只有「提交过一次」之后才显示红字，否则一进页面全是报错很难看
    var tried by remember { mutableStateOf(false) }

    val createState by viewModel.createState.collectAsStateWithLifecycle()

    LaunchedEffect(createState) {
        if (createState is MainViewModel.CreateState.Success) onDone()
    }

    val loading = createState is MainViewModel.CreateState.Loading
    val serverError = (createState as? MainViewModel.CreateState.Error)?.message

    val dateRangeError = checkDateRange(startDate, endDate)
    val groupSizeResult = parseOptionalInt(groupSize)
    val budgetResult = parseOptionalNumber(budgetTarget)

    val canSubmit = title.isNotBlank() && dateRangeError == null &&
        groupSizeResult.errorOf() == null && budgetResult.errorOf() == null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
            isError = tried && title.isBlank(),
            supportingText = if (tried && title.isBlank()) {
                { Text("给这趟旅行起个名字，比如「周末去杭州」") }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = destination,
            onValueChange = { destination = it },
            label = { Text("目的地") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        DateField(startDate, { startDate = it }, "开始日期")
        Spacer(Modifier.height(12.dp))

        DateField(endDate, { endDate = it }, "结束日期")
        if (dateRangeError != null) {
            Text(
                dateRangeError,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        ValidatedField(
            value = groupSize,
            onValueChange = { groupSize = it },
            label = "人数（可选）",
            error = if (tried) groupSizeResult.errorOf() else null,
            keyboardOptions = numberKeyboard(integerOnly = true),
        )
        Spacer(Modifier.height(12.dp))

        ValidatedField(
            value = budgetTarget,
            onValueChange = { budgetTarget = it },
            label = "总预算 ¥（可选）",
            error = if (tried) budgetResult.errorOf() else null,
            keyboardOptions = numberKeyboard(),
        )
        Spacer(Modifier.height(20.dp))

        if (serverError != null) {
            Text(serverError, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                tried = true
                if (!canSubmit) return@Button
                viewModel.createTrip(
                    TripRequest(
                        title = title.trim(),
                        destination = destination.trim().ifBlank { null },
                        startDate = startDate.ifBlank { null },
                        endDate = endDate.ifBlank { null },
                        groupSize = groupSizeResult.orNull()?.toInt(),
                        budgetTarget = budgetResult.orNull(),
                    )
                )
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (loading) "创建中..." else "创建")
        }
        Spacer(Modifier.height(24.dp))
    }
}
