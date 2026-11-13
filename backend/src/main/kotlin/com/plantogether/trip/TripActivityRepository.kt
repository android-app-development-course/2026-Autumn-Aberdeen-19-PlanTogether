package com.plantogether.trip

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TripActivityRepository : JpaRepository<TripActivity, Long> {
    fun findTop50ByTripIdOrderByCreatedAtDesc(tripId: Long): List<TripActivity>
    fun deleteAllByTripId(tripId: Long)

    /** 最新一条动态的 id 当作行程的变更计数。 */
    @Query("select coalesce(max(a.id), 0) from TripActivity a where a.trip.id = :tripId")
    fun maxIdByTripId(@Param("tripId") tripId: Long): Long
}
