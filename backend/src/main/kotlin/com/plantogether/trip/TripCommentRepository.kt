package com.plantogether.trip

import org.springframework.data.jpa.repository.JpaRepository

interface TripCommentRepository : JpaRepository<TripComment, Long> {
    fun findAllByTripIdOrderByCreatedAtDesc(tripId: Long): List<TripComment>
    fun findByIdAndTripId(id: Long, tripId: Long): TripComment?
    fun countByTripId(tripId: Long): Int
    fun deleteAllByTripId(tripId: Long)
    fun deleteAllByPlaceId(placeId: Long)
}
