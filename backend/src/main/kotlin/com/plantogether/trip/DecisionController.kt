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
@RequestMapping("/api/trips/{tripId}/decisions")
class DecisionController(
    private val decisionService: DecisionService,
) {

    @GetMapping
    fun list(@PathVariable tripId: Long): List<DecisionResponse> = decisionService.list(tripId)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@PathVariable tripId: Long, @Valid @RequestBody request: DecisionRequest): DecisionResponse =
        decisionService.create(tripId, request)

    @PutMapping("/{decisionId}/status")
    fun setStatus(
        @PathVariable tripId: Long,
        @PathVariable decisionId: Long,
        @Valid @RequestBody request: DecisionStatusRequest,
    ): DecisionResponse = decisionService.setStatus(tripId, decisionId, request)

    @DeleteMapping("/{decisionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable tripId: Long, @PathVariable decisionId: Long) =
        decisionService.delete(tripId, decisionId)

    @PostMapping("/{decisionId}/options")
    @ResponseStatus(HttpStatus.CREATED)
    fun addOption(
        @PathVariable tripId: Long,
        @PathVariable decisionId: Long,
        @Valid @RequestBody request: OptionRequest,
    ): DecisionResponse = decisionService.addOption(tripId, decisionId, request)

    @DeleteMapping("/{decisionId}/options/{optionId}")
    fun removeOption(
        @PathVariable tripId: Long,
        @PathVariable decisionId: Long,
        @PathVariable optionId: Long,
    ): DecisionResponse = decisionService.removeOption(tripId, decisionId, optionId)

    @PostMapping("/{decisionId}/options/{optionId}/vote")
    fun vote(
        @PathVariable tripId: Long,
        @PathVariable decisionId: Long,
        @PathVariable optionId: Long,
    ): DecisionResponse = decisionService.vote(tripId, decisionId, optionId)
}
