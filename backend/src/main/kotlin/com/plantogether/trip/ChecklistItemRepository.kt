package com.plantogether.trip

import org.springframework.data.jpa.repository.JpaRepository

interface ChecklistItemRepository : JpaRepository<ChecklistItem, Long> {
    fun findAllByTripIdOrderBySortOrderAscIdAsc(tripId: Long): List<ChecklistItem>
    fun findByIdAndTripId(id: Long, tripId: Long): ChecklistItem?
    fun countByTripIdAndDoneTrue(tripId: Long): Int
    fun deleteAllByTripId(tripId: Long)
}
