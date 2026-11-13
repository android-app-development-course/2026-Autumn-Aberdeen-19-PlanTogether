package com.plantogether.trip

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class DecisionRequest(
    @field:NotBlank(message = "决策标题不能为空")
    @field:Size(max = 200, message = "决策标题最多 200 字")
    val title: String,

    @field:Size(max = 2000, message = "说明最多 2000 字")
    val notes: String? = null,

    /** 创建时可直接带上若干候选选项标题。 */
    val options: List<String> = emptyList(),
)

data class OptionRequest(
    @field:NotBlank(message = "选项内容不能为空")
    @field:Size(max = 200, message = "选项最多 200 字")
    val title: String,

    @field:Size(max = 1000, message = "选项说明最多 1000 字")
    val notes: String? = null,
)

data class DecisionStatusRequest(
    @field:NotBlank(message = "状态不能为空")
    val status: String,

    /** status=DECIDED 时必填，指定胜出的选项。 */
    val decidedOptionId: Long? = null,

    /** 客户端读到的决策版本；带上时做乐观锁校验。 */
    val version: Int? = null,
)

data class DecisionOptionResponse(
    val id: Long,
    val title: String,
    val notes: String?,
    val sortOrder: Int,
    val voteCount: Int,
    val votedByMe: Boolean,
    val voterNames: List<String>,
)

data class DecisionResponse(
    val id: Long,
    val title: String,
    val notes: String?,
    val status: String,
    val decidedOptionId: Long?,
    val options: List<DecisionOptionResponse>,
    val totalVotes: Int,
    val createdByName: String?,
    val canDelete: Boolean,
    val version: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** 轻量变更计数：App 轮询它判断「别人是否改过」，避免每次都拉全量数据。 */
data class RevisionResponse(val revision: Long)
