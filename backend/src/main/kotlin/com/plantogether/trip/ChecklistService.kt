package com.plantogether.trip

import com.plantogether.common.ConflictException
import com.plantogether.common.NotFoundException
import com.plantogether.security.SecurityUtils
import com.plantogether.user.UserRepository
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ChecklistService(
    private val checklistRepository: ChecklistItemRepository,
    private val userRepository: UserRepository,
    private val accessor: TripAccessor,
    private val activityLogger: ActivityLogger,
    private val entityManager: EntityManager,
) {

    @Transactional(readOnly = true)
    fun list(tripId: Long): List<ChecklistResponse> {
        accessor.read(tripId)
        return checklistRepository.findAllByTripIdOrderBySortOrderAscIdAsc(tripId).map { it.toResponse() }
    }

    @Transactional
    fun add(tripId: Long, request: ChecklistRequest): ChecklistResponse {
        val userId = SecurityUtils.currentUserId()
        val trip = accessor.write(tripId, userId)
        val user = userRepository.findById(userId).orElseThrow { IllegalStateException("用户不存在") }
        val nextOrder = (checklistRepository.findAllByTripIdOrderBySortOrderAscIdAsc(tripId).lastOrNull()?.sortOrder ?: -1) + 1
        val item = ChecklistItem(
            trip = trip,
            title = request.title.trim(),
            done = request.done ?: false,
            assignee = request.assignee?.trim()?.ifBlank { null },
            sortOrder = nextOrder,
            createdBy = user,
        )
        val saved = checklistRepository.save(item)
        activityLogger.log(trip, "CHECKLIST_ADDED", "清单新增「${saved.title}」")
        return saved.toResponse()
    }

    @Transactional
    fun update(tripId: Long, itemId: Long, request: ChecklistRequest): ChecklistResponse {
        val trip = accessor.write(tripId)
        val item = checklistRepository.findByIdAndTripId(itemId, tripId)
            ?: throw NotFoundException("清单条目不存在")
        if (request.version != null && request.version != item.version) {
            throw ConflictException("这条清单项刚被其他人修改过，已为你刷新")
        }

        val newTitle = request.title.trim()
        val newAssignee = request.assignee?.trim()?.ifBlank { null }
        val textChanged = newTitle != item.title || newAssignee != item.assignee
        item.title = newTitle
        item.assignee = newAssignee

        request.done?.let { done ->
            if (done != item.done) {
                activityLogger.log(
                    trip,
                    if (done) "CHECKLIST_DONE" else "CHECKLIST_REOPEN",
                    "${if (done) "完成" else "重新打开"}清单项「${item.title}」",
                )
            }
            item.done = done
        }
        if (textChanged) {
            activityLogger.log(trip, "CHECKLIST_UPDATED", "更新了清单项「${item.title}」")
        }
        entityManager.flush()
        return item.toResponse()
    }

    @Transactional
    fun delete(tripId: Long, itemId: Long) {
        val trip = accessor.write(tripId)
        val item = checklistRepository.findByIdAndTripId(itemId, tripId)
            ?: throw NotFoundException("清单条目不存在")
        checklistRepository.delete(item)
        activityLogger.log(trip, "CHECKLIST_DELETED", "删除了清单项「${item.title}」")
    }

    private fun ChecklistItem.toResponse() = ChecklistResponse(
        id = id,
        title = title,
        done = done,
        assignee = assignee,
        createdByName = createdBy?.displayName,
        sortOrder = sortOrder,
        version = version,
        createdAt = createdAt,
    )
}
