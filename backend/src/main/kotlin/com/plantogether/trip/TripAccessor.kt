package com.plantogether.trip

import com.plantogether.common.ForbiddenException
import com.plantogether.common.NotFoundException
import com.plantogether.security.SecurityUtils
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 统一的行程权限入口。
 *
 * - [read]：创建者或任意成员可读
 * - [write]：创建者或 EDITOR 可写，VIEWER 拒绝
 * - [ownerOnly]：仅创建者
 *
 * 所有子资源（地点 / 链接 / 评论 / 清单 / 决策）都通过这里校验，避免各处重复写权限判断。
 */
@Component
class TripAccessor(
    private val tripRepository: TripRepository,
    private val tripMemberRepository: TripMemberRepository,
) {

    @Transactional(readOnly = true)
    fun read(tripId: Long, userId: Long = SecurityUtils.currentUserId()): Trip {
        val trip = findTrip(tripId)
        if (trip.owner.id == userId) return trip
        tripMemberRepository.findByTripIdAndUserId(tripId, userId)
            ?: throw ForbiddenException("你还不是这个行程的成员，请先用邀请码加入")
        return trip
    }

    @Transactional(readOnly = true)
    fun write(tripId: Long, userId: Long = SecurityUtils.currentUserId()): Trip {
        val trip = findTrip(tripId)
        if (trip.owner.id == userId) return trip
        val member = tripMemberRepository.findByTripIdAndUserId(tripId, userId)
            ?: throw ForbiddenException("你还不是这个行程的成员，请先用邀请码加入")
        if (member.role == TripRole.VIEWER.name) {
            throw ForbiddenException("你是只读成员，无法修改这个行程")
        }
        return trip
    }

    @Transactional(readOnly = true)
    fun ownerOnly(tripId: Long, userId: Long = SecurityUtils.currentUserId()): Trip {
        val trip = findTrip(tripId)
        if (trip.owner.id != userId) throw ForbiddenException("只有行程创建者可以执行该操作")
        return trip
    }

    fun findTrip(tripId: Long): Trip =
        tripRepository.findById(tripId).orElseThrow { NotFoundException("行程不存在或已被删除") }

    fun roleOf(trip: Trip, userId: Long): String = when {
        trip.owner.id == userId -> TripRole.OWNER.name
        else -> tripMemberRepository.findByTripIdAndUserId(trip.id, userId)?.role ?: "NONE"
    }

    fun canWrite(role: String): Boolean = role == TripRole.OWNER.name || role == TripRole.EDITOR.name

    fun memberCount(tripId: Long): Int = 1 + tripMemberRepository.countByTripId(tripId)
}
