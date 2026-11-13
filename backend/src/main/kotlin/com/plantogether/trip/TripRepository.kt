package com.plantogether.trip

import org.springframework.data.jpa.repository.JpaRepository

interface TripRepository : JpaRepository<Trip, Long> {
    fun findAllByOwnerIdOrderByCreatedAtDesc(ownerId: Long): List<Trip>
    fun findByInviteCode(inviteCode: String): Trip?
}
