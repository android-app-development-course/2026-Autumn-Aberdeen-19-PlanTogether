package com.plantogether.trip

import com.plantogether.common.ConflictException
import com.plantogether.common.ForbiddenException
import com.plantogether.common.NotFoundException
import com.plantogether.security.SecurityUtils
import com.plantogether.user.UserRepository
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

@Service
class TripService(
    private val tripRepository: TripRepository,
    private val placeRepository: PlaceRepository,
    private val commentRepository: TripCommentRepository,
    private val memberRepository: TripMemberRepository,
    private val tripActivityRepository: TripActivityRepository,
    private val userRepository: UserRepository,
    private val accessor: TripAccessor,
    private val cleanup: TripCleanup,
    private val activityLogger: ActivityLogger,
    private val entityManager: EntityManager,
    private val tripEventBroker: TripEventBroker,
) {

    // ---- 行程 ----

    @Transactional
    fun createTrip(request: TripRequest): TripResponse {
        val userId = SecurityUtils.currentUserId()
        val owner = userRepository.findById(userId).orElseThrow { IllegalStateException("用户不存在") }
        val trip = Trip(
            owner = owner,
            title = request.title.trim(),
            destination = request.destination?.trim()?.ifBlank { null },
            startDate = request.startDate,
            endDate = request.endDate,
            groupSize = request.groupSize,
            budgetTarget = request.budgetTarget,
            visibility = request.visibility,
            inviteCode = newInviteCode(),
        )
        validateDates(trip.startDate, trip.endDate)
        val saved = tripRepository.save(trip)
        activityLogger.log(saved, "TRIP_CREATED", "创建了行程「${saved.title}」")
        return saved.toResponse(userId)
    }

    /** 我参与的行程：自己创建的 + 用邀请码加入的。 */
    @Transactional(readOnly = true)
    fun listTrips(): List<TripResponse> {
        val userId = SecurityUtils.currentUserId()
        val owned = tripRepository.findAllByOwnerIdOrderByCreatedAtDesc(userId)
        val joined = memberRepository.findAllByUserId(userId).map { it.trip }
        return (owned + joined)
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt }
            .map { it.toResponse(userId) }
    }

    @Transactional(readOnly = true)
    fun getTrip(tripId: Long): TripResponse {
        val userId = SecurityUtils.currentUserId()
        return accessor.read(tripId, userId).toResponse(userId)
    }

    @Transactional
    fun updateTrip(tripId: Long, request: TripRequest): TripResponse {
        val userId = SecurityUtils.currentUserId()
        val trip = accessor.ownerOnly(tripId, userId)
        ensureVersion(request.version, trip.version, "这个行程")
        validateDates(request.startDate, request.endDate)
        trip.title = request.title.trim()
        trip.destination = request.destination?.trim()?.ifBlank { null }
        trip.startDate = request.startDate
        trip.endDate = request.endDate
        trip.groupSize = request.groupSize
        trip.budgetTarget = request.budgetTarget
        trip.visibility = request.visibility
        activityLogger.log(trip, "TRIP_UPDATED", "更新了行程基本信息")
        // 先 flush 让 JPA 自增版本号，响应里带出去的就是最新版本
        entityManager.flush()
        return trip.toResponse(userId)
    }

    @Transactional
    fun deleteTrip(tripId: Long) {
        val trip = accessor.ownerOnly(tripId)
        cleanup.purge(tripId)
        tripRepository.delete(trip)
    }

    // ---- 邀请与成员 ----

    /** 用邀请码加入行程。已加入过则直接返回行程，方便重复点击。 */
    @Transactional
    fun joinByInviteCode(rawCode: String): TripResponse {
        val userId = SecurityUtils.currentUserId()
        val code = rawCode.trim().uppercase()
        val trip = tripRepository.findByInviteCode(code)
            ?: throw NotFoundException("邀请码无效，请向行程创建者确认")
        if (trip.owner.id == userId) {
            throw ForbiddenException("你已经是这个行程的创建者")
        }
        val existing = memberRepository.findByTripIdAndUserId(trip.id, userId)
        if (existing == null) {
            val user = userRepository.findById(userId).orElseThrow { IllegalStateException("用户不存在") }
            memberRepository.save(
                TripMember(trip = trip, user = user, role = TripRole.EDITOR.name),
            )
            activityLogger.log(trip, "MEMBER_JOINED", "${user.displayName} 加入了行程")
        }
        return trip.toResponse(userId)
    }

    /** 成员主动退出行程；创建者不能退出（需删除行程）。 */
    @Transactional
    fun leaveTrip(tripId: Long) {
        val userId = SecurityUtils.currentUserId()
        val trip = accessor.findTrip(tripId)
        if (trip.owner.id == userId) {
            throw ForbiddenException("你是这个行程的创建者，不能退出；可以删除行程")
        }
        val member = memberRepository.findByTripIdAndUserId(tripId, userId)
            ?: throw NotFoundException("你不是这个行程的成员")
        val name = member.user.displayName
        memberRepository.delete(member)
        activityLogger.log(trip, "MEMBER_LEFT", "$name 退出了行程")
    }

    /** 行程的变更计数：等于最新一条动态的 id，App 轮询它决定要不要重新拉数据。 */
    @Transactional(readOnly = true)
    fun getRevision(tripId: Long): Long {
        accessor.read(tripId)
        return tripActivityRepository.maxIdByTripId(tripId)
    }

    /** 建立 SSE 订阅（先校验成员身份，再挂连接）。 */
    @Transactional(readOnly = true)
    fun subscribe(tripId: Long): SseEmitter {
        accessor.read(tripId)
        return tripEventBroker.subscribe(tripId)
    }

    @Transactional(readOnly = true)
    fun listMembers(tripId: Long): List<MemberResponse> {
        val userId = SecurityUtils.currentUserId()
        val trip = accessor.read(tripId, userId)
        val viewerIsOwner = trip.owner.id == userId
        val owner = MemberResponse(
            id = 0,
            userId = trip.owner.id,
            displayName = trip.owner.displayName,
            email = trip.owner.email,
            role = TripRole.OWNER.name,
            isOwner = true,
            joinedAt = trip.createdAt,
            canManage = false,
        )
        val members = memberRepository.findAllByTripIdOrderByJoinedAtAsc(tripId).map { member ->
            MemberResponse(
                id = member.id,
                userId = member.user.id,
                displayName = member.user.displayName,
                email = member.user.email,
                role = member.role,
                isOwner = false,
                joinedAt = member.joinedAt,
                canManage = viewerIsOwner,
            )
        }
        return listOf(owner) + members
    }

    @Transactional
    fun updateMemberRole(tripId: Long, memberId: Long, role: String): MemberResponse {
        val userId = SecurityUtils.currentUserId()
        val trip = accessor.ownerOnly(tripId, userId)
        val member = memberRepository.findByIdAndTripId(memberId, tripId)
            ?: throw NotFoundException("成员不存在")
        val normalized = role.trim().uppercase()
        if (normalized != TripRole.EDITOR.name && normalized != TripRole.VIEWER.name) {
            throw IllegalArgumentException("角色只能是 EDITOR 或 VIEWER")
        }
        member.role = normalized
        activityLogger.log(trip, "MEMBER_ROLE", "把 ${member.user.displayName} 设为${if (normalized == TripRole.EDITOR.name) "可编辑" else "只读"}")
        return MemberResponse(
            id = member.id,
            userId = member.user.id,
            displayName = member.user.displayName,
            email = member.user.email,
            role = member.role,
            isOwner = false,
            joinedAt = member.joinedAt,
            canManage = true,
        )
    }

    @Transactional
    fun removeMember(tripId: Long, memberId: Long) {
        val userId = SecurityUtils.currentUserId()
        val trip = accessor.ownerOnly(tripId, userId)
        val member = memberRepository.findByIdAndTripId(memberId, tripId)
            ?: throw NotFoundException("成员不存在")
        val name = member.user.displayName
        memberRepository.delete(member)
        activityLogger.log(trip, "MEMBER_REMOVED", "移除了成员 $name")
    }

    /** 轮换邀请码：旧邀请码立即失效，用于收回未使用的邀请。 */
    @Transactional
    fun rotateInviteCode(tripId: Long): TripResponse {
        val userId = SecurityUtils.currentUserId()
        val trip = accessor.ownerOnly(tripId, userId)
        trip.inviteCode = newInviteCode()
        activityLogger.log(trip, "INVITE_ROTATED", "更新了邀请码，旧邀请码已失效")
        return trip.toResponse(userId)
    }

    @Transactional(readOnly = true)
    fun listActivity(tripId: Long): List<ActivityResponse> {
        accessor.read(tripId)
        return tripActivityRepository.findTop50ByTripIdOrderByCreatedAtDesc(tripId)
            .map { activityLogger.toResponse(it) }
    }

    // ---- 地点 ----

    @Transactional
    fun addPlace(tripId: Long, request: PlaceRequest): PlaceResponse {
        val userId = SecurityUtils.currentUserId()
        val trip = accessor.write(tripId, userId)
        val user = userRepository.findById(userId).orElseThrow { IllegalStateException("用户不存在") }
        val nextOrder = (placeRepository.findAllByTripIdOrderBySortOrderAsc(tripId).lastOrNull()?.sortOrder ?: -1) + 1
        val place = Place(
            trip = trip,
            name = request.name.trim(),
            address = request.address?.trim()?.ifBlank { null },
            latitude = request.latitude,
            longitude = request.longitude,
            date = request.date,
            startTime = request.startTime?.trim()?.ifBlank { null },
            endTime = request.endTime?.trim()?.ifBlank { null },
            budget = request.budget,
            stayLength = request.stayLength?.trim()?.ifBlank { null },
            notes = request.notes?.trim()?.ifBlank { null },
            backupOption = request.backupOption?.trim()?.ifBlank { null },
            mapLink = request.mapLink?.trim()?.ifBlank { null },
            rating = request.rating?.trim()?.ifBlank { null },
            avgCost = request.avgCost?.trim()?.ifBlank { null },
            openTime = request.openTime?.trim()?.ifBlank { null },
            sortOrder = if (request.sortOrder > 0) request.sortOrder else nextOrder,
            createdBy = user,
        )
        val saved = placeRepository.save(place)
        activityLogger.log(trip, "PLACE_ADDED", "添加地点「${saved.name}」")
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    fun listPlaces(tripId: Long): List<PlaceResponse> {
        accessor.read(tripId)
        return placeRepository.findAllByTripIdOrderBySortOrderAsc(tripId).map { it.toResponse() }
    }

    @Transactional
    fun updatePlace(tripId: Long, placeId: Long, request: PlaceRequest): PlaceResponse {
        val trip = accessor.write(tripId)
        val place = placeRepository.findByIdAndTripId(placeId, tripId)
            ?: throw NotFoundException("地点不存在")
        ensureVersion(request.version, place.version, "这个地点")
        place.name = request.name.trim()
        place.address = request.address?.trim()?.ifBlank { null }
        place.latitude = request.latitude
        place.longitude = request.longitude
        place.date = request.date
        place.startTime = request.startTime?.trim()?.ifBlank { null }
        place.endTime = request.endTime?.trim()?.ifBlank { null }
        place.budget = request.budget
        place.stayLength = request.stayLength?.trim()?.ifBlank { null }
        place.notes = request.notes?.trim()?.ifBlank { null }
        place.backupOption = request.backupOption?.trim()?.ifBlank { null }
        place.mapLink = request.mapLink?.trim()?.ifBlank { null }
        place.rating = request.rating?.trim()?.ifBlank { null }
        place.avgCost = request.avgCost?.trim()?.ifBlank { null }
        place.openTime = request.openTime?.trim()?.ifBlank { null }
        activityLogger.log(trip, "PLACE_UPDATED", "修改了地点「${place.name}」")
        entityManager.flush()
        return place.toResponse()
    }

    @Transactional
    fun deletePlace(tripId: Long, placeId: Long) {
        val trip = accessor.write(tripId)
        val place = placeRepository.findByIdAndTripId(placeId, tripId)
            ?: throw NotFoundException("地点不存在")
        commentRepository.deleteAllByPlaceId(placeId)
        placeRepository.delete(place)
        activityLogger.log(trip, "PLACE_DELETED", "删除了地点「${place.name}」")
    }

    @Transactional
    fun reorderPlaces(tripId: Long, orderedIds: List<Long>): List<PlaceResponse> {
        val trip = accessor.write(tripId)
        var changed = false
        orderedIds.forEachIndexed { index, id ->
            val place = placeRepository.findByIdAndTripId(id, tripId)
                ?: throw NotFoundException("地点不存在")
            if (place.sortOrder != index) {
                place.sortOrder = index
                changed = true
            }
        }
        if (changed) activityLogger.log(trip, "PLACE_REORDERED", "调整了行程的先后顺序")
        return placeRepository.findAllByTripIdOrderBySortOrderAsc(tripId).map { it.toResponse() }
    }

    // ---- helpers ----

    /** 客户端带上版本号时，检查是否已被人先改过。 */
    private fun ensureVersion(expected: Int?, actual: Int, what: String) {
        if (expected != null && expected != actual) {
            throw ConflictException("$what 刚被其他人修改过，已为你刷新，请确认后再操作")
        }
    }

    private fun validateDates(start: java.time.LocalDate?, end: java.time.LocalDate?) {
        if (start != null && end != null && end.isBefore(start)) {
            throw IllegalArgumentException("结束日期不能早于开始日期")
        }
    }

    private fun newInviteCode(): String = UUID.randomUUID().toString().replace("-", "").substring(0, 8).uppercase()

    private fun Trip.toResponse(userId: Long): TripResponse {
        val role = accessor.roleOf(this, userId)
        return TripResponse(
            id = id,
            title = title,
            destination = destination,
            startDate = startDate,
            endDate = endDate,
            groupSize = groupSize,
            budgetTarget = budgetTarget,
            visibility = visibility,
            inviteCode = if (role == TripRole.OWNER.name) inviteCode else null,
            ownerName = owner.displayName,
            role = role,
            memberCount = accessor.memberCount(id),
            version = version,
            revision = tripActivityRepository.maxIdByTripId(id),
            createdAt = createdAt,
        )
    }

    private fun Place.toResponse() = PlaceResponse(
        id = id,
        name = name,
        address = address,
        latitude = latitude,
        longitude = longitude,
        date = date,
        startTime = startTime,
        endTime = endTime,
        budget = budget,
        stayLength = stayLength,
        notes = notes,
        backupOption = backupOption,
        mapLink = mapLink,
        rating = rating,
        avgCost = avgCost,
        openTime = openTime,
        sortOrder = sortOrder,
        createdById = createdBy?.id,
        createdByName = createdBy?.displayName,
        version = version,
    )
}
