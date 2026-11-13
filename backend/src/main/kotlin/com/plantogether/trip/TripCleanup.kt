package com.plantogether.trip

import org.springframework.stereotype.Component

/** 删除行程前，按外键依赖顺序清理子数据（JPA 没有配级联删除，避免隐式行为）。 */
@Component
class TripCleanup(
    private val placeRepository: PlaceRepository,
    private val linkRepository: LinkRepository,
    private val commentRepository: TripCommentRepository,
    private val checklistRepository: ChecklistItemRepository,
    private val decisionRepository: DecisionRepository,
    private val optionRepository: DecisionOptionRepository,
    private val activityRepository: TripActivityRepository,
    private val memberRepository: TripMemberRepository,
) {

    fun purge(tripId: Long) {
        decisionRepository.findAllByTripIdOrderByCreatedAtDesc(tripId).forEach { decision ->
            optionRepository.findAllByDecisionId(decision.id).forEach { optionRepository.delete(it) }
            decisionRepository.delete(decision)
        }
        // 评论先于地点删除（评论可能挂在地点上）
        commentRepository.deleteAllByTripId(tripId)
        checklistRepository.deleteAllByTripId(tripId)
        linkRepository.deleteAllByTripId(tripId)
        placeRepository.deleteAllByTripId(tripId)
        activityRepository.deleteAllByTripId(tripId)
        memberRepository.deleteAllByTripId(tripId)
    }
}
