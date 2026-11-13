package com.plantogether.trip

import com.plantogether.common.ForbiddenException
import com.plantogether.common.NotFoundException
import com.plantogether.security.SecurityUtils
import com.plantogether.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CommentService(
    private val commentRepository: TripCommentRepository,
    private val placeRepository: PlaceRepository,
    private val userRepository: UserRepository,
    private val accessor: TripAccessor,
    private val activityLogger: ActivityLogger,
) {

    @Transactional(readOnly = true)
    fun list(tripId: Long): List<CommentResponse> {
        val userId = SecurityUtils.currentUserId()
        val trip = accessor.read(tripId, userId)
        return commentRepository.findAllByTripIdOrderByCreatedAtDesc(tripId)
            .map { it.toResponse(userId, trip.owner.id) }
    }

    @Transactional
    fun add(tripId: Long, request: CommentRequest): CommentResponse {
        val userId = SecurityUtils.currentUserId()
        val trip = accessor.write(tripId, userId)
        val user = userRepository.findById(userId).orElseThrow { IllegalStateException("用户不存在") }
        val place = request.placeId?.let {
            placeRepository.findByIdAndTripId(it, tripId) ?: throw NotFoundException("地点不存在")
        }
        val comment = TripComment(
            trip = trip,
            user = user,
            body = request.body.trim(),
            place = place,
        )
        val saved = commentRepository.save(comment)
        activityLogger.log(trip, "COMMENT_ADDED", "在讨论区发言：${saved.body.take(40)}")
        return saved.toResponse(userId, trip.owner.id)
    }

    /** 只有作者本人或行程创建者可以删除评论。 */
    @Transactional
    fun delete(tripId: Long, commentId: Long) {
        val userId = SecurityUtils.currentUserId()
        val trip = accessor.read(tripId, userId)
        val comment = commentRepository.findByIdAndTripId(commentId, tripId)
            ?: throw NotFoundException("评论不存在")
        if (comment.user.id != userId && trip.owner.id != userId) {
            throw ForbiddenException("只能删除自己的评论")
        }
        commentRepository.delete(comment)
        activityLogger.log(trip, "COMMENT_DELETED", "撤回了一条评论")
    }

    private fun TripComment.toResponse(userId: Long, ownerId: Long) = CommentResponse(
        id = id,
        body = body,
        placeId = place?.id,
        placeName = place?.name,
        authorId = user.id,
        authorName = user.displayName,
        createdAt = createdAt,
        canDelete = user.id == userId || ownerId == userId,
    )
}
