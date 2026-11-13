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
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/** 行程角色：创建者拥有全部权限；编辑者可共同整理内容；只读成员只能查看。 */
enum class TripRole { OWNER, EDITOR, VIEWER }

/**
 * 通过邀请码加入行程的成员。创建者本身不写入该表（由 trips.owner_id 表达），
 * 因此成员数 = 1 + trip_members 行数，避免重复记录。
 */
@Entity
@Table(
    name = "trip_members",
    uniqueConstraints = [UniqueConstraint(name = "uk_trip_member", columnNames = ["trip_id", "user_id"])],
)
class TripMember(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    val trip: Trip,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false)
    var role: String = TripRole.EDITOR.name,

    @Column(nullable = false, updatable = false)
    val joinedAt: Instant = Instant.now(),
)
