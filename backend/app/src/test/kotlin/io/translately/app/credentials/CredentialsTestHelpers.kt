package io.translately.app.credentials

import io.translately.data.entity.Organization
import io.translately.data.entity.OrganizationMember
import io.translately.data.entity.OrganizationRole
import io.translately.data.entity.Project
import io.translately.data.entity.User
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional

/**
 * CDI-scoped helper for the credentials integration tests.
 *
 * `@Transactional` only activates across the CDI proxy, so each
 * test-side DB mutation lives as a method here rather than inline in
 * the test class (where a self-call would bypass the interceptor).
 */
@ApplicationScoped
open class CredentialsTestHelpers(
    private val em: EntityManager,
) {
    /** Create a fresh org + project for a credential test. Returns the project's ULID. */
    @Transactional
    open fun seedOrgAndProject(
        orgSlug: String,
        projectSlug: String,
    ): String {
        val org =
            Organization().apply {
                this.slug = orgSlug
                this.name = orgSlug
            }
        em.persist(org)
        val project =
            Project().apply {
                this.organization = org
                this.slug = projectSlug
                this.name = projectSlug
            }
        em.persist(project)
        em.flush()
        return project.externalId
    }

    /**
     * Seed an org + project + owning user + OWNER membership in one call.
     * Returns (orgId, projectId, userId) so the test can hit both the
     * project API and the users-me PAT endpoints as the same principal.
     */
    @Transactional
    open fun seedOrgProjectAndOwner(
        orgSlug: String,
        projectSlug: String,
        email: String,
    ): SeededOrgProject {
        val org =
            Organization().apply {
                this.slug = orgSlug
                this.name = orgSlug
            }
        em.persist(org)
        val project =
            Project().apply {
                this.organization = org
                this.slug = projectSlug
                this.name = projectSlug
            }
        em.persist(project)
        val user =
            User().apply {
                this.email = email
                this.fullName = "Owner"
                this.passwordHash = "\$argon2id\$v=19\$m=65536,t=3,p=4\$notreal"
                this.emailVerifiedAt = java.time.Instant.now()
            }
        em.persist(user)
        val member =
            OrganizationMember().apply {
                this.organization = org
                this.user = user
                this.role = OrganizationRole.OWNER
                this.joinedAt = java.time.Instant.now()
            }
        em.persist(member)
        em.flush()
        return SeededOrgProject(
            orgExternalId = org.externalId,
            projectExternalId = project.externalId,
            userExternalId = user.externalId,
        )
    }

    /** Seed an org + membership for an existing user at [role]. Returns the org's ULID. */
    @Transactional
    open fun seedOrgWithMember(
        orgSlug: String,
        userExternalId: String,
        role: OrganizationRole,
    ): String {
        val org =
            Organization().apply {
                this.slug = orgSlug
                this.name = orgSlug
            }
        em.persist(org)
        val user =
            em
                .createQuery("SELECT u FROM User u WHERE u.externalId = :id", User::class.java)
                .setParameter("id", userExternalId)
                .singleResult
        val member =
            OrganizationMember().apply {
                this.organization = org
                this.user = user
                this.role = role
                this.joinedAt = java.time.Instant.now()
            }
        em.persist(member)
        em.flush()
        return org.externalId
    }

    /** Create a verified user for PAT tests. Returns the user's ULID. */
    @Transactional
    open fun seedVerifiedUser(
        email: String,
        fullName: String = "Test User",
    ): String {
        val user =
            User().apply {
                this.email = email
                this.fullName = fullName
                this.passwordHash = "\$argon2id\$v=19\$m=65536,t=3,p=4\$notreal" // placeholder; never verified
                this.emailVerifiedAt = java.time.Instant.now()
            }
        em.persist(user)
        em.flush()
        return user.externalId
    }

    /** Stamp `revoked_at` on the given API key. Used for revoked-credential tests. */
    @Transactional
    open fun revokeApiKey(prefix: String) {
        em
            .createQuery("UPDATE ApiKey a SET a.revokedAt = :now WHERE a.prefix = :prefix")
            .setParameter("now", java.time.Instant.now())
            .setParameter("prefix", prefix)
            .executeUpdate()
    }

    /** Set `expires_at` on the given API key to a past timestamp. */
    @Transactional
    open fun expireApiKey(prefix: String) {
        em
            .createQuery("UPDATE ApiKey a SET a.expiresAt = :past WHERE a.prefix = :prefix")
            .setParameter(
                "past",
                java.time.Instant
                    .now()
                    .minusSeconds(60),
            ).setParameter("prefix", prefix)
            .executeUpdate()
    }

    /** Stamp `revoked_at` on the given PAT. */
    @Transactional
    open fun revokePat(prefix: String) {
        em
            .createQuery("UPDATE Pat p SET p.revokedAt = :now WHERE p.prefix = :prefix")
            .setParameter("now", java.time.Instant.now())
            .setParameter("prefix", prefix)
            .executeUpdate()
    }

    /** Set `expires_at` on the given PAT to a past timestamp. */
    @Transactional
    open fun expirePat(prefix: String) {
        em
            .createQuery("UPDATE Pat p SET p.expiresAt = :past WHERE p.prefix = :prefix")
            .setParameter(
                "past",
                java.time.Instant
                    .now()
                    .minusSeconds(60),
            ).setParameter("prefix", prefix)
            .executeUpdate()
    }
}

/** Tuple returned by [CredentialsTestHelpers.seedOrgProjectAndOwner]. */
data class SeededOrgProject(
    val orgExternalId: String,
    val projectExternalId: String,
    val userExternalId: String,
)
