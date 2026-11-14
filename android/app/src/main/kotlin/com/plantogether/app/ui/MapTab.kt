package com.plantogether.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.plantogether.app.data.PlaceResponse
import com.plantogether.app.data.TripResponse

/**
 * 示意图地图：只画有经纬度的地点，按手动顺序连线。
 * 本版本不计算路线距离/用时，也不做导航，仅用于讨论相对位置和顺序。
 */
@Composable
fun MapTab(trip: TripResponse?, places: List<PlaceResponse>) {
    val plotted = remember(places) {
        places.filter { it.latitude != null && it.longitude != null }.sortedBy { it.sortOrder }
    }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (plotted.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "给地点填上经纬度，这里就会出现示意图。\n（本地图是手绘示意图，不做真实导航）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                } else {
                    SchematicMap(places = places, modifier = Modifier.fillMaxSize())
                }
            }
        }

        if (plotted.size == 1) {
            Text(
                "只有一个地点有坐标，再加一个就能看到连线。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        LazyColumn(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            items(plotted, key = { it.id }) { place ->
                val index = plotted.indexOfFirst { it.id == place.id } + 1
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(enabled = !place.mapLink.isNullOrBlank()) {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(place.mapLink)))
                            }
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NumberBadge("$index")
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(place.name, style = MaterialTheme.typography.bodyLarge)
                        val dateLabel = formatDayLabel(place.date, trip?.startDate)
                        Text(
                            listOfNotNull(dateLabel, place.startTime).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!place.mapLink.isNullOrBlank()) {
                        Text(
                            "打开地图 ›",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "示意图只反映手动填写的坐标和排序，不代表真实路线或通行时间。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
