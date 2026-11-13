package com.plantogether.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.plantogether.app.data.PlaceResponse

/**
 * 「去点评/美团/高德看这家店」的跳转。
 *
 * 只负责把用户带到对方 App/网页，不抓取、不复制对方的评分数据 —— 这是合规边界，
 * 也是这些平台唯一允许的集成方式。
 *
 * 美团和大众点评没有对 C 端开放「按店名搜并拿评分」的接口（开放平台只给商家/服务商
 * 做团购和核销），所以这里只能跳转。评分/人均/营业时间那一份结构化数据走的是高德
 * POI 接口（见 PoiPicker.kt 和后端 AmapPoiService），两边的定位不同、不该混为一谈。
 */
private const val DIANPING_PACKAGE = "com.dianping.v1"
private const val MEITUAN_PACKAGE = "com.sankuai.meituan"
private const val AMAP_PACKAGE = "com.autonavi.minimap"

enum class LookupTarget { DIANPING, MEITUAN, AMAP }

fun dianpingSearchUrl(keyword: String): String =
    "https://www.dianping.com/search/keyword/0/0_" + Uri.encode(keyword)

fun meituanSearchUrl(keyword: String): String =
    "https://www.meituan.com/s/" + Uri.encode(keyword)

fun amapAppUri(keyword: String, city: String?): String = buildString {
    append("androidamap://poi?sourceApplication=plantogether")
    append("&keywords=").append(Uri.encode(keyword))
    if (!city.isNullOrBlank()) append("&city=").append(Uri.encode(city))
}

fun amapWebUrl(keyword: String, city: String?): String = buildString {
    append("https://uri.amap.com/search?keyword=").append(Uri.encode(keyword))
    if (!city.isNullOrBlank()) append("&city=").append(Uri.encode(city))
    append("&src=plantogether&coordinate=gaode&callnative=1")
}

fun isPackageInstalled(context: Context, packageName: String): Boolean =
    runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess

/**
 * 打开外部 App/网页。https 链接优先（App Links 会自动进 App，没装就进浏览器），
 * 高德用官方 scheme，失败再退到网页。
 */
fun openLookup(context: Context, target: LookupTarget, keyword: String, city: String?) {
    val intent = when (target) {
        LookupTarget.DIANPING -> Intent(Intent.ACTION_VIEW, Uri.parse(dianpingSearchUrl(keyword)))
        LookupTarget.MEITUAN -> Intent(Intent.ACTION_VIEW, Uri.parse(meituanSearchUrl(keyword)))
        LookupTarget.AMAP -> {
            if (isPackageInstalled(context, AMAP_PACKAGE)) {
                Intent(Intent.ACTION_VIEW, Uri.parse(amapAppUri(keyword, city))).setPackage(AMAP_PACKAGE)
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(amapWebUrl(keyword, city)))
            }
        }
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // 高德 scheme 打不开时退到网页
        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(amapWebUrl(keyword, city)))
        runCatching { context.startActivity(fallback) }
            .onFailure { Toast.makeText(context, "没有可打开的应用", Toast.LENGTH_SHORT).show() }
    } catch (_: Exception) {
        Toast.makeText(context, "打开失败，请稍后再试", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookupSheet(
    place: PlaceResponse,
    city: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val keyword = place.name

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Text("去哪里看这家店", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                keyword,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 已经在手上的结构化信息先摊开：不用跳出去就能判断「值不值得吃」
            val hasLocal = !place.rating.isNullOrBlank() ||
                !place.avgCost.isNullOrBlank() ||
                !place.openTime.isNullOrBlank()
            if (hasLocal) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            place.rating?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    "★ $it",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            place.avgCost?.takeIf { it.isNotBlank() && it != "0.00" }?.let {
                                Text(
                                    "人均 ¥$it",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        place.openTime?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "营业时间 $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "来自高德地图",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "看真实食客怎么说",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))

            LookupRow(
                title = "大众点评",
                subtitle = if (isPackageInstalled(context, DIANPING_PACKAGE)) {
                    "看评分和真实评价 · 已安装"
                } else {
                    "看评分和真实评价 · 未安装，用浏览器打开"
                },
                onClick = {
                    openLookup(context, LookupTarget.DIANPING, keyword, city)
                    onDismiss()
                },
            )
            LookupRow(
                title = "美团",
                subtitle = if (isPackageInstalled(context, MEITUAN_PACKAGE)) {
                    "看团购和优惠 · 已安装"
                } else {
                    "看团购和优惠 · 未安装，用浏览器打开"
                },
                onClick = {
                    openLookup(context, LookupTarget.MEITUAN, keyword, city)
                    onDismiss()
                },
            )
            LookupRow(
                title = "高德地图",
                subtitle = "看位置、导航过去",
                onClick = {
                    openLookup(context, LookupTarget.AMAP, keyword, city)
                    onDismiss()
                },
            )
            LookupRow(
                title = "小红书 / 微博",
                subtitle = "搜「$keyword 好吃吗」看素人说法",
                onClick = {
                    copyToClipboard(context, "关键词", "$keyword 好吃吗")
                    Toast.makeText(context, "已复制，去小红书搜搜看", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
            )

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            LookupRow(
                title = "复制店名",
                subtitle = "粘到任意 App 里自己搜",
                onClick = {
                    copyToClipboard(context, "店名", keyword)
                    onDismiss()
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LookupRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
