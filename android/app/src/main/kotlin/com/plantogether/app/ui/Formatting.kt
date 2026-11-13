package com.plantogether.app.ui

import com.plantogether.app.data.PlaceResponse
import com.plantogether.app.data.TripResponse
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** 金额展示：整数不带小数，其余保留两位。 */
fun formatMoney(value: Double?): String? = value?.let {
    if (it == it.toLong().toDouble()) "¥${it.toLong()}" else String.format(Locale.CHINA, "¥%.2f", it)
}

fun parseDate(iso: String?): LocalDate? = iso?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

/** 「D1 · 11月7日 周六」这样的日期标题；没有日期时归到「未定日期」。 */
fun formatDayLabel(dateIso: String?, tripStart: String?): String {
    val date = parseDate(dateIso) ?: return "未定日期"
    val weekday = date.format(DateTimeFormatter.ofPattern("EEE", Locale.SIMPLIFIED_CHINESE))
    val base = "${date.monthValue}月${date.dayOfMonth}日 $weekday"
    val start = parseDate(tripStart) ?: return base
    val dayNo = ChronoUnit.DAYS.between(start, date).toInt() + 1
    return if (dayNo in 1..365) "D$dayNo · $base" else base
}

/** 后端 Instant（ISO-8601）→ 本地「10-28 14:02」。 */
fun formatTimestamp(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching {
        Instant.parse(iso).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
    }.getOrElse { iso.take(16).replace("T", " ") }
}

fun roleLabel(role: String): String = when (role) {
    "OWNER" -> "创建者"
    "EDITOR" -> "可编辑"
    "VIEWER" -> "只读"
    else -> role
}

/** 行程日期范围展示。 */
fun tripDateRange(trip: TripResponse?): String {
    if (trip == null) return "加载中…"
    val start = parseDate(trip.startDate)
    val end = parseDate(trip.endDate)
    return when {
        start != null && end != null ->
            "${start.monthValue}月${start.dayOfMonth}日 – ${end.monthValue}月${end.dayOfMonth}日"
        start != null -> "${start.monthValue}月${start.dayOfMonth}日出发"
        end != null -> "至 ${end.monthValue}月${end.dayOfMonth}日"
        else -> "日期待定"
    }
}

data class PlaceGroup(
    val date: String?,
    val label: String,
    val places: List<PlaceResponse>,
)

/** 按日期分组（未定日期排最后），组内保持手动排序。 */
fun groupPlaces(places: List<PlaceResponse>, trip: TripResponse?): List<PlaceGroup> {
    val grouped = LinkedHashMap<String?, MutableList<PlaceResponse>>()
    places.sortedBy { it.sortOrder }.forEach { place ->
        grouped.getOrPut(place.date) { mutableListOf() }.add(place)
    }
    return grouped.entries
        .sortedWith(compareBy(nullsLast()) { it.key })
        .map { (date, list) -> PlaceGroup(date, formatDayLabel(date, trip?.startDate), list) }
}
