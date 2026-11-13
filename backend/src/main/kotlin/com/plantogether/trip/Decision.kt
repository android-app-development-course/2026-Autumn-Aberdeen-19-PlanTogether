package com.plantogether.trip

import com.plantogether.user.User
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
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

/** 群决策状态：进行中 / 已定 / 已取消。 */
enum class DecisionStatus { OPEN, DECIDED, CANCELLED }

/** 一个需要全组拍板的问题，例如「周六晚餐去哪家」。 */
@Entity
@Table(name = "decisions")
class Decision(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    val trip: Trip,

    @Column(nullable = false)
    var title: String,

    @Column(length = 2000)
    var notes: String? = null,

    @Column(nullable = false)
    var status: String = DecisionStatus.OPEN.name,

    /** 最终选中的选项 id；未定案时为 null。 */
    var decidedOptionId: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    val createdBy: User? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Version
    var version: Int = 0,
)

/**
 * 决策的候选选项。投票记录为「投票用户 id 集合」，
 * 因此不需要额外的投票表，删除选项时投票自动一并删除。
 */
@Entity
@Table(name = "decision_options")
class DecisionOption(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decision_id", nullable = false)
    val decision: Decision,

    @Column(nullable = false)
    var title: String,

    @Column(length = 1000)
    var notes: String? = null,

    @Column(nullable = false)
    var sortOrder: Int = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    val createdBy: User? = null,

    @ElementCollection
    @CollectionTable(
        name = "decision_option_votes",
        joinColumns = [JoinColumn(name = "option_id")],
    )
    @Column(name = "user_id", nullable = false)
    var voterIds: MutableSet<Long> = mutableSetOf(),

    /** 投票会并发写同一个选项，加版本号避免两人同时投票时悄悄丢掉一票。 */
    @Version
    var version: Int = 0,
)
