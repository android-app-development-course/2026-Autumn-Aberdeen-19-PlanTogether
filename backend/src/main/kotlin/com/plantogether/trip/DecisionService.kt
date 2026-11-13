package com.plantogether.trip

import com.plantogether.common.ConflictException
import com.plantogether.common.ForbiddenException
import com.plantogether.common.NotFoundException
import com.plantogether.security.SecurityUtils
import com.plantogether.user.UserRepository
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class DecisionService(
    private val decisionRepository: DecisionRepository,
    private val optionRepository: DecisionOptionRepository,
    private val userRepository: UserRepository,
    private val accessor: TripAccessor,
    private val activityLogger: ActivityLogger,
    private val entityManager: EntityManager,
) {

    @Transactional(readOnly = true)
    fun list(tripId: Long): List<DecisionResponse> {
        val userId = SecurityUtils.currentUserId()
        val trip = accessor.read(tripId, userId)
        return decisionRepository.findAllByTripIdOrderByCreatedAtDesc(tripId)
            .map { it.toResponse(userId, trip.owner.id) }
    }

    @Transactional
    fun create(tripId: Long, request: DecisionRequest): DecisionResponse {
        val userId = SecurityUtils.currentUserId()
        val trip = accessor.write(tripId, userId)
        val creator = userRepository.findById(userId).orElseThrow { IllegalStateException("用户不存在") }
        val decision = decisionRepository.save(
            Decision(
                trip = trip,
                title = request.title.trim(),
                notes = request.notes?.trim()?.ifBlank { null },
                createdBy = creator,
            ),
        )
        request.options
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEachIndexed { index, title ->
                optionRepository.save(
                    DecisionOption(decision = decision, title = title, sortOrder = index, createdBy = creator),
                )
            }
        activityLogger.log(trip, "DECISION_CREATED", "发起决策「${decision.title}」")
        return decision.toResponse(userId, trip.owner.id)
    }

    @Transactional
    fun addOption(tripId: Long, decisionId: Long, request: OptionRequest): DecisionResponse {
        val userId = SecurityUtils.currentUserId()
        val trip = accessor.write(tripId, userId)
        val decision = findDecision(tripId, decisionId)
        val nextOrder = optionRepository.findAllByDecisionId(decisionId).size
        val creator = userRepository.findById(userId).orElse(null)
        optionRepository.save(
            DecisionOption(
                decision = decision,
                title = request.title.trim(),
                notes = request.notes?.trim()?.ifBlank { null },
                sortOrder = nextOrder,
                createdBy = creator,
            ),
        )
        decision.updatedAt = Instant.now()
        activityLogger.log(trip, "DECISION_OPTION_ADDED", "给「${decision.title}」加了选项「${request.title.trim()}」")
        entityManager.flush()
        return decision.toResponse(userId, trip.owner.id)
    }

    @Transactional
    fun removeOption(tripId: Long, decisionId: Long, optionId: Long): DecisionResponse {
        val userId = SecurityUtils.currentUserId()
        val trip = accessor.write(tripId, userId)
        val decision = findDecision(tripId, decisionId)
        val option = optionRepository.findByIdAndDecisionId(optionId, decisionId)
            ?: throw NotFoundException("选项不存在")
        if (decision.decidedOptionId == optionId) {
            decision.decidedOptionId = null
            decision.status = DecisionStatus.OPEN.name
        }
        optionRepository.delete(option)
        decision.updatedAt = Instant.now()
        activityLogger.log(trip, "DECISION_OPTION_REMOVED", "删除了「${decision.title}」的选项「${option.title}」")
        entityManager.flush()
        return decision.toResponse(userId, trip.owner.id)
    }

    /** 投票是开关式：已投同一选项则取消，投别的选项会自动改票（每人每项一票）。 */
    @Transactional
    fun vote(tripId: Long, decisionId: Long, optionId: Long): DecisionResponse {
        val userId = SecurityUtils.currentUserId()
        val trip = accessor.write(tripId, userId)
        val decision = findDecision(tripId, decisionId)
        val option = optionRepository.findByIdAndDecisionId(optionId, decisionId)
            ?: throw NotFoundException("选项不存在")

        val alreadyVoted = option.voterIds.contains(userId)
        if (alreadyVoted) {
            option.voterIds.remove(userId)
        } else {
            // 只动「我原本投过的那个选项」和目标选项，减少并发写冲突
            optionRepository.findAllByDecisionId(decisionId)
                .filter { it.id != option.id && it.voterIds.contains(userId) }
                .forEach { it.voterIds.remove(userId) }
            option.voterIds.add(userId)
        }
        decision.updatedAt = Instant.now()
        activityLogger.log(
            trip,
            "DECISION_VOTE",
            "${if (alreadyVoted) "取消了对" else "投票给"}「${decision.title}」的选项「${option.title}」",
        )
        entityManager.flush()
        return decision.toResponse(userId, trip.owner.id)
    }

    @Transactional
    fun setStatus(tripId: Long, decisionId: Long, request: DecisionStatusRequest): DecisionResponse {
        val userId = SecurityUtils.currentUserId()
        val trip = accessor.write(tripId, userId)
        val decision = findDecision(tripId, decisionId)

        val status = request.status.trim().uppercase()
        if (status !in DecisionStatus.entries.map { it.name }) {
            throw IllegalArgumentException("状态只能是 OPEN / DECIDED / CANCELLED")
        }
        if (request.version != null && request.version != decision.version) {
            throw ConflictException("这个决策刚被其他人修改过，已为你刷新")
        }
        if (status == DecisionStatus.DECIDED.name) {
            val optionId = request.decidedOptionId
                ?: throw IllegalArgumentException("请选择最终选定的选项")
            optionRepository.findByIdAndDecisionId(optionId, decisionId)
                ?: throw NotFoundException("选项不存在")
            decision.decidedOptionId = optionId
        } else {
            decision.decidedOptionId = null
        }
        decision.status = status
        decision.updatedAt = Instant.now()
        val label = when (status) {
            DecisionStatus.DECIDED.name -> "已定案"
            DecisionStatus.CANCELLED.name -> "已取消"
            else -> "重新开放"
        }
        activityLogger.log(trip, "DECISION_STATUS", "把「${decision.title}」标记为$label")
        entityManager.flush()
        return decision.toResponse(userId, trip.owner.id)
    }

    /** 只有发起人或行程创建者可以删除决策。 */
    @Transactional
    fun delete(tripId: Long, decisionId: Long) {
        val userId = SecurityUtils.currentUserId()
        val trip = accessor.write(tripId, userId)
        val decision = findDecision(tripId, decisionId)
        if (decision.createdBy?.id != userId && trip.owner.id != userId) {
            throw ForbiddenException("只有发起人或行程创建者可以删除这个决策")
        }
        optionRepository.findAllByDecisionId(decisionId).forEach { optionRepository.delete(it) }
        decisionRepository.delete(decision)
        activityLogger.log(trip, "DECISION_DELETED", "删除了决策「${decision.title}」")
    }

    private fun findDecision(tripId: Long, decisionId: Long): Decision =
        decisionRepository.findByIdAndTripId(decisionId, tripId)
            ?: throw NotFoundException("决策不存在")

    private fun Decision.toResponse(userId: Long, ownerId: Long): DecisionResponse {
        val options = optionRepository.findAllByDecisionIdOrderBySortOrderAscIdAsc(id)
        val voterIds = options.flatMap { it.voterIds }.toSet()
        val names = if (voterIds.isEmpty()) {
            emptyMap()
        } else {
            userRepository.findAllById(voterIds).associate { it.id to it.displayName }
        }
        return DecisionResponse(
            id = id,
            title = title,
            notes = notes,
            status = status,
            decidedOptionId = decidedOptionId,
            options = options.map { option ->
                DecisionOptionResponse(
                    id = option.id,
                    title = option.title,
                    notes = option.notes,
                    sortOrder = option.sortOrder,
                    voteCount = option.voterIds.size,
                    votedByMe = option.voterIds.contains(userId),
                    voterNames = option.voterIds.mapNotNull { names[it] },
                )
            },
            totalVotes = voterIds.size,
            createdByName = createdBy?.displayName,
            canDelete = createdBy?.id == userId || ownerId == userId,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}
