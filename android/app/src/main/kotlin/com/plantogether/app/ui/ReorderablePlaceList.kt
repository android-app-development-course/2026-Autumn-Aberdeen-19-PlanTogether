package com.plantogether.app.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import com.plantogether.app.data.PlaceResponse

/**
 * 长按拖动排序的小列表。只在「一天之内」排序，跨天的顺序由日期决定。
 *
 * 拖动过程中先改本地顺序（视觉跟手），松手时才把整组 id 提交给服务端。
 */
@Composable
fun ReorderablePlaceList(
    places: List<PlaceResponse>,
    enabled: Boolean,
    onCommit: (List<Long>) -> Unit,
    itemContent: @Composable (place: PlaceResponse, index: Int, isDragging: Boolean) -> Unit,
) {
    val items = remember { mutableStateListOf<PlaceResponse>() }
    LaunchedEffect(places) {
        if (items.map { it.id } != places.map { it.id }) {
            items.clear()
            items.addAll(places)
        }
    }

    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Long, Int>() }

    Column {
        items.forEachIndexed { index, place ->
            key(place.id) {
                val isDragging = draggingId == place.id
                Box(
                    Modifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            if (isDragging) {
                                translationY = dragOffset
                                scaleX = 1.02f
                                scaleY = 1.02f
                            }
                        }
                        .onSizeChanged { heights[place.id] = it.height }
                        .pointerInput(place.id, enabled) {
                            if (!enabled) return@pointerInput
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingId = place.id
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    draggingId = null
                                    dragOffset = 0f
                                },
                                onDragEnd = {
                                    draggingId = null
                                    dragOffset = 0f
                                    onCommit(items.map { it.id }.toList())
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val current = items.indexOfFirst { it.id == place.id }
                                    if (current < 0) return@detectDragGesturesAfterLongPress
                                    val height = (heights[place.id] ?: 1).toFloat()
                                    if (dragOffset > height * 0.6f && current < items.lastIndex) {
                                        items.removeAt(current)
                                        items.add(current + 1, place)
                                        dragOffset -= height
                                    } else if (dragOffset < -height * 0.6f && current > 0) {
                                        items.removeAt(current)
                                        items.add(current - 1, place)
                                        dragOffset += height
                                    }
                                },
                            )
                        },
                ) {
                    itemContent(place, index, isDragging)
                }
            }
        }
    }
}
