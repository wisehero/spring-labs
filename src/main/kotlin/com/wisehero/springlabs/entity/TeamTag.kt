package com.wisehero.springlabs.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "team_tag")
class TeamTag(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "tag_name", length = 100, nullable = false)
    val tagName: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    val team: Team,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
