package com.plantogether.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.plantogether.app.data.PlaceResponse

/**
 * 自研示意图画布：不依赖第三方地图，按地点经纬度相对位置绘制标记点，
 * 并按 sortOrder 顺序连线，展示相对方位与规划路线。
 */
@Composable
fun SchematicMap(places: List<PlaceResponse>, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    // 画布用主题色，不再写死蓝色（第九轮统一视觉）
    val lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val markerColor = MaterialTheme.colorScheme.primary
    val markerTextColor = MaterialTheme.colorScheme.onPrimary
    val plotted = places
        .filter { it.latitude != null && it.longitude != null }
        .sortedBy { it.sortOrder }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (plotted.isEmpty()) return@Canvas

        val minLng = plotted.minOf { it.longitude!! }
        val maxLng = plotted.maxOf { it.longitude!! }
        val minLat = plotted.minOf { it.latitude!! }
        val maxLat = plotted.maxOf { it.latitude!! }

        val lngRange = (maxLng - minLng).takeIf { it > 0.0 } ?: 1.0
        val latRange = (maxLat - minLat).takeIf { it > 0.0 } ?: 1.0

        val pad = 48f
        fun project(lat: Double, lng: Double): Offset {
            val x = (pad + ((lng - minLng) / lngRange) * (size.width - 2 * pad)).toFloat()
            val y = (pad + ((maxLat - lat) / latRange) * (size.height - 2 * pad)).toFloat()
            return Offset(x, y)
        }

        val points = plotted.map { project(it.latitude!!, it.longitude!!) }

        // 按顺序连线
        for (i in 0 until points.size - 1) {
            drawLine(color = lineColor, start = points[i], end = points[i + 1], strokeWidth = 4f)
        }

        // 标记点 + 序号
        points.forEachIndexed { index, point ->
            drawCircle(color = markerColor, radius = 20f, center = point)
            val layout = textMeasurer.measure("${index + 1}", style = labelStyle)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(point.x - layout.size.width / 2f, point.y - layout.size.height / 2f),
                color = markerTextColor,
            )
        }
    }
}
