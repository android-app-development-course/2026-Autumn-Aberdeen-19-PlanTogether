package com.plantogether.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plantogether.app.data.PoiSuggestion

/**
 * 「从高德搜店」输入区。
 *
 * 只做一件事：把搜到的一条结果交给外面去填表单。
 * 评分/人均/营业时间都是高德官方接口返回的结构化字段，不是从网页上扒的。
 */
@Composable
fun PoiSearchSection(
    viewModel: TripDetailViewModel,
    city: String?,
    onPick: (PoiSuggestion) -> Unit,
) {
    val state by viewModel.poiState.collectAsStateWithLifecycle()
    var keyword by remember { mutableStateOf("") }

    fun triggerSearch() {
        if (keyword.isNotBlank()) viewModel.searchPoi(keyword)
    }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                keyword,
                { keyword = it },
                label = { Text("搜店名，自动带出地址和评分") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { triggerSearch() }) { Text("搜索") }
        }

        // 城市只是提示，搜不到时可以自己换关键词；这里不改 trip，只读
        if (!city.isNullOrBlank()) {
            Text(
                "优先搜「$city」附近",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        when (val s = state) {
            is TripDetailViewModel.PoiSearchState.Idle -> Unit

            is TripDetailViewModel.PoiSearchState.Searching -> Row(
                Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在查…", style = MaterialTheme.typography.bodySmall)
            }

            is TripDetailViewModel.PoiSearchState.NotConfigured -> Text(
                "这个服务器还没接地图服务，先手动填吧。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            is TripDetailViewModel.PoiSearchState.Empty -> Text(
                s.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            is TripDetailViewModel.PoiSearchState.Results -> Column(
                Modifier
                    .padding(top = 8.dp)
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                s.items.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider()
                    PoiRow(item) {
                        onPick(item)
                        viewModel.resetPoiSearch()
                    }
                }
            }
        }
    }
}

@Composable
private fun PoiRow(item: PoiSuggestion, onClick: () -> Unit) {    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            item.typeLabel?.let {
                Spacer(Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
        }

        item.scoreLabel?.let {
            Spacer(Modifier.height(2.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        val sub = buildList {
            item.address?.let { add(it) }
            item.businessArea?.let { add(it) }
        }.joinToString(" · ")
        if (sub.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        item.openTimeToday?.let {
            Spacer(Modifier.height(2.dp))
            Text(
                "营业 $it",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
