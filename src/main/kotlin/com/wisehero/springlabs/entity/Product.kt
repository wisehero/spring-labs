package com.wisehero.springlabs.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "product")
class Product(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "name", length = 100, nullable = false)
    val name: String,

    @Column(name = "stock", nullable = false)
    var stock: Int,

    @Version
    @Column(name = "version")
    var version: Long? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
