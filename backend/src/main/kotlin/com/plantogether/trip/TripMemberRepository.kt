package com.plantogether.trip

import org.springframework.data.jpa.repository.JpaRepository

interface TripMemberRepository : JpaRepository<TripMember, Long> {
    fun findByTripIdAndUserId(tripId: Long, userId: Long): TripMember?
    fun findByIdAndTripId(id: Long, tripId: Long): TripMember?
    fun findAllByTripIdOrderByJoinedAtAsc(tripId: Long): List<TripMember>
    fun findAllByUserId(userId: Long): List<TripMember>
    fun countByTripId(tripId: Long): Int
    fun deleteAllByTripId(tripId: Long)
}
