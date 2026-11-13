package com.plantogether.poi

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * 高德 POI 搜索代理。
 *
 * 为什么要在后端转发，而不是 App 直接调高德：
 * 1. **Key 安全**：把 Key 打进 APK，任何人 unzip 一下就能拿走，然后拿你的配额去刷；
 *    放在后端，Key 只存在于服务器环境变量里。
 * 2. **配额控制**：高德按 Key 限 QPS，后端集中做缓存和限流，比每台手机各自去打更稳。
 * 3. **合规**：第三方数据统一从服务端出口出去，日后要换数据源（或加价购）只改一处。
 *
 * 只读官方 API 返回的结构化字段，不抓取网页、不存储对方的评价正文 —— 这是合规边界。
 */
@Service
class AmapPoiService(
    @Value("\${app.amap.key:}") private val apiKey: String,
    // 允许改成自建/镜像端点：本地联调时可以用假响应把整条链路跑通，不必消耗真实配额
    @Value("\${app.amap.endpoint:https://restapi.amap.com/v5/place/text}") private val endpoint: String,
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(AmapPoiService::class.java)

    /** 没配 Key 时功能整体隐身，App 里显示「还没接地图服务」而不是报错。 */
    val configured: Boolean get() = apiKey.isNotBlank()

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    /** 搜索结果缓存：同一个城市 + 关键词 5 分钟内直接复用，省配额。 */
    private data class CacheEntry(val at: Long, val value: PoiSearchResponse)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private companion object {
        const val CACHE_TTL_MS = 5 * 60 * 1000L
        const val MAX_CACHE_SIZE = 200
    }

    /**
     * 按关键词搜地点。[city] 为空时不限城市。
     *
     * 用 v5 接口而不是 v3：v5 的 `show_fields=business` 才能一次拿到营业时间，
     * v3 要额外再打一次「POI 详情」接口，翻倍消耗配额。
     */
    fun search(keyword: String, city: String?, limit: Int = 10): PoiSearchResponse {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) {
            return PoiSearchResponse(configured = configured, items = emptyList(), message = "请输入要搜索的店名或地点")
        }
        if (!configured) {
            return PoiSearchResponse(
                configured = false,
                items = emptyList(),
                message = "服务端还没配置高德 Key，暂时搜不了",
            )
        }

        val safeLimit = limit.coerceIn(1, 25)
        val cacheKey = "${city.orEmpty()}|$trimmed|$safeLimit"
        cache[cacheKey]?.let { entry ->
            if (System.currentTimeMillis() - entry.at < CACHE_TTL_MS) return entry.value
            cache.remove(cacheKey)
        }

        val response = runCatching { callAmap(trimmed, city, safeLimit) }
            .getOrElse { ex ->
                log.warn("高德 POI 搜索失败: keyword={}, city={}", trimmed, city, ex)
                return PoiSearchResponse(
                    configured = true,
                    items = emptyList(),
                    message = "地图服务暂时连不上，请稍后再试",
                )
            }

        if (cache.size > MAX_CACHE_SIZE) cache.clear()
        cache[cacheKey] = CacheEntry(System.currentTimeMillis(), response)
        return response
    }

    private fun callAmap(keyword: String, city: String?, limit: Int): PoiSearchResponse {
        val query = buildString {
            append("key=").append(enc(apiKey))
            append("&keywords=").append(enc(keyword))
            append("&page_size=").append(limit)
            append("&page_num=1")
            // business 才会带评分/人均/营业时间/电话
            append("&show_fields=business")
            city?.takeIf { it.isNotBlank() }?.let {
                append("&region=").append(enc(it))
                append("&city_limit=true")
            }
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$endpoint?$query"))
            .timeout(Duration.ofSeconds(6))
            .header("Accept", "application/json")
            .GET()
            .build()

        val body = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body()
        val root = objectMapper.readTree(body)

        // 高德成功时 status = "1"，失败时 infocode 会说明原因（如 10001 = Key 不对）
        val status = root.path("status").asText()
        if (status != "1") {
            val infoCode = root.path("infocode").asText()
            val info = root.path("info").asText()
            log.warn("高德返回失败: status={}, infocode={}, info={}", status, infoCode, info)
            // Key 无效/过期/超额，都不该把原始英文抛给用户
            val message = when (infoCode) {
                "10001", "10002", "10003", "10004" -> "服务端的高德 Key 无效或已过期"
                "10020", "10021", "10029" -> "高德地图今日配额已用完"
                else -> "地图服务返回异常，请稍后再试"
            }
            return PoiSearchResponse(configured = true, items = emptyList(), message = message)
        }

        val pois = root.path("pois")
        val items = if (pois.isArray) {
            pois.mapNotNull { node -> node.toSuggestion() }
        } else {
            emptyList()
        }
        return PoiSearchResponse(configured = true, items = items)
    }

    private fun JsonNode.toSuggestion(): PoiSuggestion? {
        val id = path("id").asText().takeIf { it.isNotBlank() } ?: return null
        val name = path("name").asText().takeIf { it.isNotBlank() } ?: return null

        // v5 的经纬度是 "120.1489,30.2587" 这种单字符串，v3 时代是分开的 lon/lat 字段
        val location = path("location").asText()
        val (lng, lat) = parseLocation(location)

        val business = path("business")
        return PoiSuggestion(
            id = id,
            name = name,
            address = path("address").asText().takeIf { it.isNotBlank() && it != "[]" },
            latitude = lat,
            longitude = lng,
            type = path("type").asText().takeIf { it.isNotBlank() },
            rating = business.path("rating").asText().takeIf { it.isNotBlank() && it != "[]" },
            avgCost = business.path("cost").asText().takeIf { it.isNotBlank() && it != "[]" },
            tag = business.path("tag").asText().takeIf { it.isNotBlank() && it != "[]" },
            openTimeToday = business.path("opentime_today").asText().takeIf { it.isNotBlank() && it != "[]" },
            tel = business.path("tel").asText().takeIf { it.isNotBlank() && it != "[]" },
            businessArea = business.path("business_area").asText().takeIf { it.isNotBlank() && it != "[]" },
        )
    }

    private fun parseLocation(raw: String): Pair<Double?, Double?> {
        val parts = raw.split(",")
        if (parts.size != 2) return null to null
        return parts[0].trim().toDoubleOrNull() to parts[1].trim().toDoubleOrNull()
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
