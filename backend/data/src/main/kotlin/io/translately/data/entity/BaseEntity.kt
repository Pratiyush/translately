package io.translately.data.entity

import io.translately.data.Ulid
import jakarta.persistence.Column
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import java.time.Instant

/**
 * Common base for every Translately entity.
 *
 * - `id` is an internal bigserial for FK relationships; **never expose to the API**.
 * - `externalId` is a public-facing ULID, unique across the table, safe to return in
 *   URLs and client responses.
 * - `createdAt` / `updatedAt` are maintained by JPA lifecycle callbacks; no trigger
 *   required. We never trust client-supplied timestamps.
 * - Soft-delete (`deletedAt`) is opt-in per entity; not mixed into the base.
 */
@MappedSuperclass
abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    var id: Long? = null
        protected set

    @Column(name = "external_id", nullable = false, updatable = false, length = 26, unique = true)
    var externalId: String = Ulid.generate()
        protected set

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
        protected set

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
        protected set

    @PrePersist
    protected fun onPersist() {
        val now = Instant.now()
        if (createdAt == Instant.EPOCH) createdAt = now
        updatedAt = now
        if (externalId.isBlank()) externalId = Ulid.generate()
    }

    @PreUpdate
    protected fun onUpdate() {
        updatedAt = Instant.now()
    }
}
