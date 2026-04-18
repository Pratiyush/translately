package io.translately.app.credentials

import io.translately.data.entity.Organization
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
}
