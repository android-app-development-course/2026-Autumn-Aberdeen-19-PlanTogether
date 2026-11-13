package com.plantogether.trip

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CommentRequest(
    @field:NotBlank(message = "评论内容不能为空")
    @field:Size(max = 2000, message = "评论最多 2000 字")
    val body: String,

    /** 可选：把这条评论挂在某个地点下面。 */
    val placeId: Long? = null,
)

data class CommentResponse(
    val id: Long,
    val body: String,
    val placeId: Long?,
    val placeName: String?,
    val authorId: Long,
    val authorName: String,
    val createdAt: Instant,
    val canDelete: Boolean,
)
