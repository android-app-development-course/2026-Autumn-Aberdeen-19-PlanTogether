package com.plantogether.trip

import com.plantogether.security.SecurityUtils
import com.plantogether.user.UserRepository
import org.springframework.stereotype.Component

/**
 * 轻量编辑历史：把协作动作写成一条动态，供成员了解「谁改了什么」。
 * 只记录摘要，不保存修改前后的完整快照。
 *
 * 每条动态也是一次「变更计数」的增长，提交后会通过 SSE 推给同一行程的在线成员。
 */
@Component
class ActivityLogger(
    private val tripActivityRepository: TripActivityRepository,
    private val userRepository: UserRepository,
    private val tripEvents: TripEvents,
) {

    fun log(trip: Trip, kind: String, summary: String) {
        val userId = runCatching { SecurityUtils.currentUserId() }.getOrNull()
        val user = userId?.let { userRepository.findById(it).orElse(null) }
        val saved = tripActivityRepository.save(
            TripActivity(
                trip = trip,
                user = user,
                kind = kind,
                summary = summary.take(400),
            ),
        )
        tripEvents.publishAfterCommit(trip.id, saved.id)
    }

    fun toResponse(activity: TripActivity) = ActivityResponse(
        id = activity.id,
        kind = activity.kind,
        summary = activity.summary,
        userName = activity.user?.displayName,
        createdAt = activity.createdAt,
    )
}
