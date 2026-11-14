package com.plantogether.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            TextButton(
                onClick = { viewModel.resetCreateState(); onDone() },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            ) { Text("‹ 返回", style = MaterialTheme.typography.labelLarge) }
            Spacer(Modifier.height(6.dp))
            Text(
                "新建行程",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "名字先随便起，之后随时能改。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))

            AppCard {
                Column(Modifier.padding(18.dp)) {
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
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = destination,
                        onValueChange = { destination = it },
                        label = { Text("目的地") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
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
                            color = MaterialTheme.colorScheme.error,
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
                }
            }
            Spacer(Modifier.height(16.dp))

            if (serverError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        serverError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
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
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(if (loading) "创建中…" else "创建行程", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
