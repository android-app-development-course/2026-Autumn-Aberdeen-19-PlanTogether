package com.plantogether.trip

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class ChecklistRequest(
    @field:NotBlank(message = "清单内容不能为空")
    @field:Size(max = 200, message = "清单内容最多 200 字")
    val title: String,

    val done: Boolean? = null,
    val assignee: String? = null,
    val version: Int? = null,
)

data class ChecklistResponse(
    val id: Long,
    val title: String,
    val done: Boolean,
    val assignee: String?,
    val createdByName: String?,
    val sortOrder: Int,
    val version: Int,
    val createdAt: Instant,
)
