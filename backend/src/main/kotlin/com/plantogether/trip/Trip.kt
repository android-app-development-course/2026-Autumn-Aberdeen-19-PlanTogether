package com.plantogether.trip

import com.plantogether.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "trips")
class Trip(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    val owner: User,

    @Column(nullable = false)
    var title: String,

    var destination: String? = null,

    var startDate: LocalDate? = null,

    var endDate: LocalDate? = null,

    var groupSize: Int? = null,

    /** 用户手动填写的预算目标（总预算），仅用于展示进度，不做任何自动计价。 */
    var budgetTarget: Double? = null,

    @Column(nullable = false)
    var visibility: String = "PRIVATE",

    @Column(nullable = false, unique = true)
    var inviteCode: String,

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    /** 乐观锁版本号：并发修改时由 JPA 检测冲突。 */
    @Version
    var version: Int = 0,
)
