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
@Table(name = "places")
class Place(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    val trip: Trip,

    @Column(nullable = false)
    var name: String,

    var address: String? = null,

    var latitude: Double? = null,

    var longitude: Double? = null,

    var date: LocalDate? = null,

    var startTime: String? = null,

    var endTime: String? = null,

    var budget: Double? = null,

    var stayLength: String? = null,

    @Column(length = 4000)
    var notes: String? = null,

    var backupOption: String? = null,

    /** 用户提供的地图链接，点击后交由外部地图 App 打开。 */
    @Column(length = 2000)
    var mapLink: String? = null,

    @Column(nullable = false)
    var sortOrder: Int = 0,

    /** 谁添加的这个地点（协作时显示来源）。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    val createdBy: User? = null,

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Version
    var version: Int = 0,
)
