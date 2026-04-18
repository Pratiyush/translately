package io.translately.app.auth

import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional

/**
 * CDI-scoped helper that wraps direct SQL mutations used by tests.
 *
 * `@Transactional` only activates on calls that cross the CDI proxy, so we
 * put the DB work here instead of inlining it in a test class where
 * self-calls bypass the transaction interceptor.
 */
@ApplicationScoped
open class AuthTestHelpers(
    private val em: EntityManager,
) {
    @Transactional
    open fun markVerified(email: String) {
        em
            .createNativeQuery(
                "UPDATE users SET email_verified_at = NOW() WHERE email = ?1",
            ).setParameter(1, email)
            .executeUpdate()
    }
}
