package com.plantogether.trip

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/trips/{tripId}/checklist")
class ChecklistController(
    private val checklistService: ChecklistService,
) {

    @GetMapping
    fun list(@PathVariable tripId: Long): List<ChecklistResponse> = checklistService.list(tripId)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun add(@PathVariable tripId: Long, @Valid @RequestBody request: ChecklistRequest): ChecklistResponse =
        checklistService.add(tripId, request)

    @PutMapping("/{itemId}")
    fun update(
        @PathVariable tripId: Long,
        @PathVariable itemId: Long,
        @Valid @RequestBody request: ChecklistRequest,
    ): ChecklistResponse = checklistService.update(tripId, itemId, request)

    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable tripId: Long, @PathVariable itemId: Long) =
        checklistService.delete(tripId, itemId)
}
