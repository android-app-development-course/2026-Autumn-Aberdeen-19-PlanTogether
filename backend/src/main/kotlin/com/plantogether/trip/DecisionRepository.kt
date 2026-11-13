package com.plantogether.trip

import org.springframework.data.jpa.repository.JpaRepository

interface DecisionRepository : JpaRepository<Decision, Long> {
    fun findAllByTripIdOrderByCreatedAtDesc(tripId: Long): List<Decision>
    fun findByIdAndTripId(id: Long, tripId: Long): Decision?
    fun deleteAllByTripId(tripId: Long)
}

interface DecisionOptionRepository : JpaRepository<DecisionOption, Long> {
    fun findAllByDecisionIdOrderBySortOrderAscIdAsc(decisionId: Long): List<DecisionOption>
    fun findByIdAndDecisionId(id: Long, decisionId: Long): DecisionOption?
    fun findAllByDecisionId(decisionId: Long): List<DecisionOption>
}
