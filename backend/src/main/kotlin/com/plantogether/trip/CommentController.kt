package com.plantogether.trip

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/trips/{tripId}/comments")
class CommentController(
    private val commentService: CommentService,
) {

    @GetMapping
    fun list(@PathVariable tripId: Long): List<CommentResponse> = commentService.list(tripId)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun add(@PathVariable tripId: Long, @Valid @RequestBody request: CommentRequest): CommentResponse =
        commentService.add(tripId, request)

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable tripId: Long, @PathVariable commentId: Long) =
        commentService.delete(tripId, commentId)
}
