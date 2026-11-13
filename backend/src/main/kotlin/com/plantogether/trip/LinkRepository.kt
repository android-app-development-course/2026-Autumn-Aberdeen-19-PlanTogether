package com.plantogether.trip

import org.springframework.data.jpa.repository.JpaRepository

interface LinkRepository : JpaRepository<LinkItem, Long> {
    fun findAllByTripIdOrderByCreatedAtDesc(tripId: Long): List<LinkItem>
    fun findByIdAndTripId(id: Long, tripId: Long): LinkItem?
    fun deleteAllByTripId(tripId: Long)
}
