package com.plantogether.trip

import org.springframework.data.jpa.repository.JpaRepository

interface PlaceRepository : JpaRepository<Place, Long> {
    fun findAllByTripIdOrderBySortOrderAsc(tripId: Long): List<Place>
    fun findByIdAndTripId(id: Long, tripId: Long): Place?
    fun countByTripId(tripId: Long): Int
    fun deleteAllByTripId(tripId: Long)
}
