package com.plantogether.poi

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/poi")
class PoiController(private val amapPoiService: AmapPoiService) {

    /**
     * 按关键词搜地点，用来在「添加地点」时自动填地址、经纬度、评分、人均、营业时间。
     *
     * 需要登录（SecurityConfig 里只放行了认证接口和健康检查），
     * 这样即使 Key 泄露也能按用户维度审计是谁在刷。
     */
    @GetMapping("/search")
    fun search(
        @RequestParam q: String,
        @RequestParam(required = false) city: String?,
        @RequestParam(required = false, defaultValue = "10") limit: Int,
    ): PoiSearchResponse = amapPoiService.search(q, city, limit)
}
