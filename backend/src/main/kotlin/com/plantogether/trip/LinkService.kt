package com.plantogether.trip

import com.plantogether.common.ConflictException
import com.plantogether.common.NotFoundException
import com.plantogether.security.SecurityUtils
import com.plantogether.user.UserRepository
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LinkService(
    private val linkRepository: LinkRepository,
    private val userRepository: UserRepository,
    private val accessor: TripAccessor,
    private val activityLogger: ActivityLogger,
    private val entityManager: EntityManager,
) {

    @Transactional
    fun addLink(tripId: Long, request: LinkRequest): LinkResponse {
        val userId = SecurityUtils.currentUserId()
        val trip = accessor.write(tripId, userId)
        val user = userRepository.findById(userId).orElseThrow { IllegalStateException("用户不存在") }
        val link = LinkItem(
            trip = trip,
            url = request.url.trim(),
            title = request.title?.trim()?.ifBlank { null },
            source = request.source?.trim()?.ifBlank { null },
            tags = request.tags?.trim()?.ifBlank { null },
            notes = request.notes?.trim()?.ifBlank { null },
            createdBy = user,
        )
        val saved = linkRepository.save(link)
        activityLogger.log(trip, "LINK_ADDED", "收藏了链接「${saved.title ?: saved.url.take(40)}」")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    fun listLinks(tripId: Long): List<LinkResponse> {
        accessor.read(tripId)
        return linkRepository.findAllByTripIdOrderByCreatedAtDesc(tripId).map { it.toResponse() }
    }

    @Transactional
    fun updateLink(tripId: Long, linkId: Long, request: LinkRequest): LinkResponse {
        val trip = accessor.write(tripId)
        val link = linkRepository.findByIdAndTripId(linkId, tripId)
            ?: throw NotFoundException("链接不存在")
        ensureVersion(request.version, link.version)
        link.url = request.url.trim()
        link.title = request.title?.trim()?.ifBlank { null }
        link.source = request.source?.trim()?.ifBlank { null }
        link.tags = request.tags?.trim()?.ifBlank { null }
        link.notes = request.notes?.trim()?.ifBlank { null }
        activityLogger.log(trip, "LINK_UPDATED", "更新了链接「${link.title ?: link.url.take(40)}」")
        entityManager.flush()
        return link.toResponse()
    }

    @Transactional
    fun deleteLink(tripId: Long, linkId: Long) {
        val trip = accessor.write(tripId)
        val link = linkRepository.findByIdAndTripId(linkId, tripId)
            ?: throw NotFoundException("链接不存在")
        linkRepository.delete(link)
        activityLogger.log(trip, "LINK_DELETED", "删除了链接「${link.title ?: link.url.take(40)}」")
    }

    private fun LinkItem.toResponse() = LinkResponse(
        id = id,
        url = url,
        title = title,
        source = source,
        tags = tags,
        notes = notes,
        createdById = createdBy?.id,
        createdByName = createdBy?.displayName,
        version = version,
        createdAt = createdAt,
    )

    private fun ensureVersion(expected: Int?, actual: Int) {
        if (expected != null && expected != actual) {
            throw ConflictException("这条链接刚被其他人修改过，已为你刷新，请确认后再操作")
        }
    }
}
