package com.plantogether.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * 「点一下选，不用手打」的日期/时间输入。
 *
 * 原来全 App 都是让人手输 yyyy-MM-dd，打错一个字符后端直接 400，
 * 而且中文标签里写死格式本身就难看懂。这里统一换成系统选择器：
 * 点得准、不可能填出非法格式。
 */

// ---------------- 日期 ----------------

/** 日期输入框：点框里任何位置都弹日历。日期是可选项时右侧带「清除」。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    allowClear: Boolean = true,
) {
    var picking by remember { mutableStateOf(false) }

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                placeholder = { Text("点击选择") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // 透明遮罩吃掉点击：readOnly 的输入框本身不会弹日历
            Box(
                Modifier
                    .matchParentSize()
                    .clickable { picking = true },
            )
        }
        if (allowClear && value.isNotBlank()) {
            TextButton(onClick = { onValueChange("") }) { Text("清除") }
        }
    }

    if (picking) {
        val initialMillis = remember(value) {
            parseIsoDate(value)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            onValueChange(date.toString())
                        }
                        picking = false
                    },
                ) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("取消") } },
        ) {
            DatePicker(state = state)
        }
    }
}

/** 时间输入框：点一下弹表盘，输出 HH:mm。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf(false) }

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                placeholder = { Text("点击选择") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .clickable { picking = true },
            )
        }
        if (value.isNotBlank()) {
            TextButton(onClick = { onValueChange("") }) { Text("清除") }
        }
    }

    if (picking) {
        val initial = parseIsoTime(value)
        val state = rememberTimePickerState(
            initialHour = initial?.hour ?: 9,
            initialMinute = initial?.minute ?: 0,
            is24Hour = true,
        )
        Dialog(onDismissRequest = { picking = false }) {
            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                Column(Modifier.padding(20.dp)) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    TimePicker(state = state)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { picking = false }) { Text("取消") }
                        TextButton(
                            onClick = {
                                onValueChange("%02d:%02d".format(state.hour, state.minute))
                                picking = false
                            },
                        ) { Text("确定") }
                    }
                }
            }
        }
    }
}

// ---------------- 解析 / 校验 ----------------

/** 解析 2026-11-07；空或看不懂返回 null。 */
fun parseIsoDate(text: String?): LocalDate? =
    text?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/** 解析 HH:mm 或 HH:mm:ss；空或看不懂返回 null。 */
fun parseIsoTime(text: String?): LocalTime? {
    val raw = text?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val parts = raw.split(":")
    if (parts.size < 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return LocalTime.of(hour, minute)
}

/**
 * 把用户输的数字文本变 Double。
 *
 * 关键区别：**空白 -> Empty，是合法的「没填」**；
 * **填了却看不懂 -> Invalid**。以前这里直接 `toDoubleOrNull()`，
 * 输错一个字符整段静默丢成 null，用户以为填上了。
 */
fun parseOptionalNumber(text: String, integerOnly: Boolean = false): NumberResult<Double> {
    val raw = text.trim()
    if (raw.isEmpty()) return NumberResult.Empty
    val value = raw.toDoubleOrNull()
        ?: return NumberResult.Invalid(if (integerOnly) "请填数字" else "请填数字，可带小数")
    if (value < 0) return NumberResult.Invalid("不能是负数")
    if (integerOnly && value != value.toLong().toDouble()) return NumberResult.Invalid("请填整数")
    return NumberResult.Ok(value)
}

fun parseOptionalInt(text: String): NumberResult<Int> {
    val raw = text.trim()
    if (raw.isEmpty()) return NumberResult.Empty
    val value = raw.toIntOrNull() ?: return NumberResult.Invalid("请填整数")
    if (value < 0) return NumberResult.Invalid("不能是负数")
    return NumberResult.Ok(value)
}

sealed interface NumberResult<out T> {
    /** 没填，属于合法的空。 */
    data object Empty : NumberResult<Nothing>

    /** 填了，但看不懂。 */
    data class Invalid(val message: String) : NumberResult<Nothing>

    data class Ok<T>(val value: T) : NumberResult<T>
}

/** 取可能的值；非法时给 null（调用方应先用 errorOf 拦住）。 */
fun <T> NumberResult<T>.orNull(): T? = (this as? NumberResult.Ok)?.value

/** 取错误文案；合法或为空时给 null。 */
fun <T> NumberResult<T>.errorOf(): String? = (this as? NumberResult.Invalid)?.message

/** 数字键盘：之前全 App 一个 keyboardType 都没有，输金额弹的是全键盘。 */
fun numberKeyboard(integerOnly: Boolean = false): KeyboardOptions = KeyboardOptions(
    keyboardType = if (integerOnly) KeyboardType.Number else KeyboardType.Decimal,
)

/** 带错误提示的输入框：错误时框变红并就地说明原因，不用等后端 400。 */
@Composable
fun ValidatedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = if (error != null) {
            { Text(error) }
        } else {
            null
        },
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        modifier = modifier.fillMaxWidth(),
    )
}

/** 结束日期是否早于开始日期；没问题返回 null。 */
fun checkDateRange(startText: String, endText: String): String? {
    val start = parseIsoDate(startText)
    val end = parseIsoDate(endText)
    if (startText.isNotBlank() && start == null) return "开始日期看不懂，请重新选一次"
    if (endText.isNotBlank() && end == null) return "结束日期看不懂，请重新选一次"
    if (start != null && end != null && end.isBefore(start)) return "结束日期不能早于开始日期"
    return null
}
