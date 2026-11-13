package com.plantogether.poi

/**
 * 一条地点搜索结果（来自高德 Web 服务 API）。
 *
 * 所有可选字段都做了「拿不到就是 null」的降级：高德对不同 POI 返回的字段并不一致，
 * 小店铺常常没有评分、没有人均、没有营业时间。客户端要能接受残缺数据。
 */
data class PoiSuggestion(
    val id: String,
    val name: String,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    /** 高德的分类，例如「餐饮服务;中餐厅;浙江菜」。 */
    val type: String?,
    /** 评分，高德给的是字符串（如 "4.5"），原样透传，避免精度问题。 */
    val rating: String?,
    /** 人均消费，同样原样透传（如 "120.00"）。 */
    val avgCost: String?,
    /** 特色标签，例如「西湖醋鱼,龙井虾仁」。 */
    val tag: String?,
    /** 今日营业时间，例如「10:00-21:30」。 */
    val openTimeToday: String?,
    val tel: String?,
    val businessArea: String?,
)

/**
 * 搜索结果包装。
 *
 * [configured] = false 表示服务端没配高德 Key。这不是错误，是一种正常状态：
 * 客户端据此提示「还没接地图服务」，而不是弹一个红色报错。
 */
data class PoiSearchResponse(
    val configured: Boolean,
    val items: List<PoiSuggestion>,
    val message: String? = null,
)
