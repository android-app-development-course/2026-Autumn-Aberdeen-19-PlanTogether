package com.plantogether.trip

import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class LinkRequest(
    @field:NotBlank(message = "链接不能为空")
    val url: String,

    val title: String? = null,
    val source: String? = null,
    val tags: String? = null,
    val notes: String? = null,
    val version: Int? = null,
)

data class LinkResponse(
    val id: Long,
    val url: String,
    val title: String?,
    val source: String?,
    val tags: String?,
    val notes: String?,
    val createdById: Long?,
    val createdByName: String?,
    val version: Int,
    val createdAt: Instant,
)
