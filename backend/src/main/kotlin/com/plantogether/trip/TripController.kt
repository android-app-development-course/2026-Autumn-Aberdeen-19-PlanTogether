package com.plantogether.trip

import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/trips")
class TripController(
    private val tripService: TripService,
    private val tripExportService: TripExportService,
) {

    // ---- 行程 ----

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createTrip(@Valid @RequestBody request: TripRequest): TripResponse =
        tripService.createTrip(request)

    @GetMapping
    fun listTrips(): List<TripResponse> = tripService.listTrips()

    /** 用邀请码加入他人的行程。 */
    @PostMapping("/join")
    fun joinTrip(@Valid @RequestBody request: JoinTripRequest): TripResponse =
        tripService.joinByInviteCode(request.inviteCode)

    @GetMapping("/{tripId}")
    fun getTrip(@PathVariable tripId: Long): TripResponse = tripService.getTrip(tripId)

    @PutMapping("/{tripId}")
    fun updateTrip(@PathVariable tripId: Long, @Valid @RequestBody request: TripRequest): TripResponse =
        tripService.updateTrip(tripId, request)

    @DeleteMapping("/{tripId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteTrip(@PathVariable tripId: Long) = tripService.deleteTrip(tripId)

    @PostMapping("/{tripId}/invite-code/rotate")
    fun rotateInviteCode(@PathVariable tripId: Long): TripResponse =
        tripService.rotateInviteCode(tripId)

    // ---- 成员 ----

    @GetMapping("/{tripId}/members")
    fun listMembers(@PathVariable tripId: Long): List<MemberResponse> = tripService.listMembers(tripId)

    @PutMapping("/{tripId}/members/{memberId}")
    fun updateMemberRole(
        @PathVariable tripId: Long,
        @PathVariable memberId: Long,
        @Valid @RequestBody request: MemberRoleRequest,
    ): MemberResponse = tripService.updateMemberRole(tripId, memberId, request.role)

    @DeleteMapping("/{tripId}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeMember(@PathVariable tripId: Long, @PathVariable memberId: Long) =
        tripService.removeMember(tripId, memberId)

    /** 成员主动退出（创建者不允许）。 */
    @DeleteMapping("/{tripId}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun leaveTrip(@PathVariable tripId: Long) = tripService.leaveTrip(tripId)

    /** 轻量变更计数，供 App 轮询判断是否需要刷新。 */
    @GetMapping("/{tripId}/revision")
    fun revision(@PathVariable tripId: Long): RevisionResponse =
        RevisionResponse(tripService.getRevision(tripId))

    /** SSE 长连接：有人改动这个行程时立刻推一条 revision，App 收到后自己拉数据。 */
    @GetMapping("/{tripId}/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(@PathVariable tripId: Long): SseEmitter = tripService.subscribe(tripId)

    // ---- 动态（轻量编辑历史） ----

    @GetMapping("/{tripId}/activity")
    fun listActivity(@PathVariable tripId: Long): List<ActivityResponse> = tripService.listActivity(tripId)

    // ---- 地点 ----

    @PostMapping("/{tripId}/places")
    @ResponseStatus(HttpStatus.CREATED)
    fun addPlace(@PathVariable tripId: Long, @Valid @RequestBody request: PlaceRequest): PlaceResponse =
        tripService.addPlace(tripId, request)

    @GetMapping("/{tripId}/places")
    fun listPlaces(@PathVariable tripId: Long): List<PlaceResponse> = tripService.listPlaces(tripId)

    @PutMapping("/{tripId}/places/reorder")
    fun reorderPlaces(@PathVariable tripId: Long, @RequestBody orderedIds: List<Long>): List<PlaceResponse> =
        tripService.reorderPlaces(tripId, orderedIds)

    @PutMapping("/{tripId}/places/{placeId}")
    fun updatePlace(
        @PathVariable tripId: Long,
        @PathVariable placeId: Long,
        @Valid @RequestBody request: PlaceRequest,
    ): PlaceResponse = tripService.updatePlace(tripId, placeId, request)

    @DeleteMapping("/{tripId}/places/{placeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePlace(@PathVariable tripId: Long, @PathVariable placeId: Long) =
        tripService.deletePlace(tripId, placeId)

    // ---- 导出 ----

    @GetMapping("/{tripId}/export", produces = ["text/markdown;charset=UTF-8"])
    fun exportTrip(@PathVariable tripId: Long): ResponseEntity<String> {
        val markdown = tripExportService.exportMarkdown(tripId)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"plantogether-trip-$tripId.md\"")
            .body(markdown)
    }
}
