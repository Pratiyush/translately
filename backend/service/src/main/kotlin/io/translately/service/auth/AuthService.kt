package io.translately.service.auth

import io.translately.data.entity.EmailVerificationToken
import io.translately.data.entity.PasswordResetToken
import io.translately.data.entity.RefreshToken
import io.translately.data.entity.User
import io.translately.email.EmailSender
import io.translately.security.Scope
import io.translately.security.jwt.JwtIssuer
import io.translately.security.jwt.JwtTokens
import io.translately.security.jwt.RefreshTokenParser
import io.translately.security.password.PasswordHasher
import io.translately.security.password.TokenGenerator
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.jboss.logging.Logger
import java.time.Duration
import java.time.Instant

/**
 * Use-case entry point for the email+password auth flow (T103).
 *
 * Everything that mutates state runs inside a transaction. Token
 * consumption is atomic with the state change it guards: verifying an
 * email both flips `emailVerifiedAt` and sets `consumedAt` in the same
 * unit of work, so we can't finish with a user-verified/token-live or
 * user-unverified/token-consumed split.
 */
@ApplicationScoped
open class AuthService(
    private val em: EntityManager,
    private val passwordHasher: PasswordHasher,
    private val jwtIssuer: JwtIssuer,
    private val refreshTokenParser: RefreshTokenParser,
    private val emailSender: EmailSender,
) {
    private val log = Logger.getLogger(AuthService::class.java)

    // ------------------------------------------------------------------
    // signup + verify
    // ------------------------------------------------------------------

    /**
     * Create a new user from a signup request.
     *
     * @return the external ULID of the created user. The caller returns this
     *   in the 201 body; the user CANNOT log in until verify-email is done.
     */
    @Transactional
    open fun signup(
        email: String,
        password: String,
        fullName: String,
    ): String {
        AuthValidator.validateSignup(email, password, fullName)
        val normalizedEmail = email.trim().lowercase()
        if (findUserByEmail(normalizedEmail) != null) {
            throw AuthException.EmailTaken(normalizedEmail)
        }
        val user =
            User().apply {
                this.email = normalizedEmail
                this.fullName = fullName.trim()
                this.passwordHash = passwordHasher.hash(password)
            }
        em.persist(user)
        em.flush()

        val rawToken = TokenGenerator.generate()
        persistEmailVerificationToken(user, rawToken)
        emailSender.sendVerification(normalizedEmail, user.fullName, rawToken)
        log.infov("signed up user {0}", user.externalId)
        return user.externalId
    }

    /** Consume a verification token, flipping [User.emailVerifiedAt]. */
    @Suppress("ThrowsCount")
    @Transactional
    open fun verifyEmail(rawToken: String) {
        AuthValidator.requireToken(rawToken, path = "token")
        val token = findValidEmailVerificationToken(rawToken) ?: throw AuthException.TokenInvalid()
        if (token.consumedAt != null) throw AuthException.TokenConsumed()
        if (!Instant.now().isBefore(token.expiresAt)) throw AuthException.TokenExpired()
        token.consumedAt = Instant.now()
        token.user.emailVerifiedAt = Instant.now()
        em.merge(token)
        em.merge(token.user)
        log.infov("verified email for user {0}", token.user.externalId)
    }

    // ------------------------------------------------------------------
    // login + refresh
    // ------------------------------------------------------------------

    /** Verify credentials, mint a fresh [JwtTokens], and record the jti. */
    @Suppress("ThrowsCount")
    @Transactional
    open fun login(
        email: String,
        password: String,
    ): JwtTokens {
        AuthValidator.validateLogin(email, password)
        val normalizedEmail = email.trim().lowercase()
        val user = findUserByEmail(normalizedEmail) ?: throw AuthException.InvalidCredentials()
        if (!passwordHasher.verify(password, user.passwordHash)) {
            throw AuthException.InvalidCredentials()
        }
        if (!user.isVerified) throw AuthException.EmailNotVerified()
        val tokens = jwtIssuer.issue(user.externalId, user.email, DEFAULT_SCOPES)
        recordRefreshJti(user, tokens)
        log.infov("logged in user {0}", user.externalId)
        return tokens
    }

    /** Rotate refresh tokens: consume the presented jti, mint fresh. */
    @Suppress("ThrowsCount")
    @Transactional
    open fun refresh(rawRefreshJwt: String): JwtTokens {
        AuthValidator.requireToken(rawRefreshJwt, path = "refreshToken")
        val parsed = refreshTokenParser.parse(rawRefreshJwt) ?: throw AuthException.TokenInvalid()
        val row =
            em
                .createQuery(
                    "SELECT r FROM RefreshToken r WHERE r.jti = :jti",
                    RefreshToken::class.java,
                ).setParameter("jti", parsed.jti)
                .resultList
                .firstOrNull() ?: throw AuthException.TokenInvalid()
        if (row.consumedAt != null) throw AuthException.RefreshTokenReused()
        if (!Instant.now().isBefore(row.expiresAt)) throw AuthException.TokenExpired()
        if (row.user.externalId != parsed.subject) throw AuthException.TokenInvalid()
        row.consumedAt = Instant.now()
        em.merge(row)
        val fresh = jwtIssuer.issue(row.user.externalId, row.user.email, DEFAULT_SCOPES)
        recordRefreshJti(row.user, fresh)
        return fresh
    }

    // ------------------------------------------------------------------
    // forgot + reset password
    // ------------------------------------------------------------------

    /**
     * Silently emit a reset email if [email] matches a user. We do NOT
     * reveal the account's existence to the caller — the resource returns
     * 202 in every case.
     */
    @Transactional
    open fun forgotPassword(email: String) {
        AuthValidator.validateEmail(email)
        val normalizedEmail = email.trim().lowercase()
        val user = findUserByEmail(normalizedEmail) ?: return
        val rawToken = TokenGenerator.generate()
        persistPasswordResetToken(user, rawToken)
        emailSender.sendPasswordReset(normalizedEmail, user.fullName, rawToken)
        log.infov("queued password reset for user {0}", user.externalId)
    }

    /** Consume a reset token and update [User.passwordHash]. */
    @Suppress("ThrowsCount")
    @Transactional
    open fun resetPassword(
        rawToken: String,
        newPassword: String,
    ) {
        AuthValidator.validateReset(rawToken, newPassword)
        val token = findValidPasswordResetToken(rawToken) ?: throw AuthException.TokenInvalid()
        if (token.consumedAt != null) throw AuthException.TokenConsumed()
        if (!Instant.now().isBefore(token.expiresAt)) throw AuthException.TokenExpired()
        token.consumedAt = Instant.now()
        token.user.passwordHash = passwordHasher.hash(newPassword)
        em.merge(token)
        em.merge(token.user)
        log.infov("reset password for user {0}", token.user.externalId)
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private fun findUserByEmail(email: String): User? =
        em
            .createQuery("SELECT u FROM User u WHERE u.email = :email", User::class.java)
            .setParameter("email", email)
            .resultList
            .firstOrNull()

    private fun findValidEmailVerificationToken(rawToken: String): EmailVerificationToken? {
        val tokens =
            em
                .createQuery(
                    "SELECT t FROM EmailVerificationToken t WHERE t.consumedAt IS NULL ORDER BY t.id DESC",
                    EmailVerificationToken::class.java,
                ).resultList
        return tokens.firstOrNull { passwordHasher.verify(rawToken, it.tokenHash) }
    }

    private fun findValidPasswordResetToken(rawToken: String): PasswordResetToken? {
        val tokens =
            em
                .createQuery(
                    "SELECT t FROM PasswordResetToken t WHERE t.consumedAt IS NULL ORDER BY t.id DESC",
                    PasswordResetToken::class.java,
                ).resultList
        return tokens.firstOrNull { passwordHasher.verify(rawToken, it.tokenHash) }
    }

    private fun persistEmailVerificationToken(
        user: User,
        rawToken: String,
    ) {
        val row =
            EmailVerificationToken().apply {
                this.user = user
                this.tokenHash = passwordHasher.hash(rawToken)
                this.expiresAt = Instant.now().plus(VERIFY_TOKEN_TTL)
            }
        em.persist(row)
    }

    private fun persistPasswordResetToken(
        user: User,
        rawToken: String,
    ) {
        val row =
            PasswordResetToken().apply {
                this.user = user
                this.tokenHash = passwordHasher.hash(rawToken)
                this.expiresAt = Instant.now().plus(RESET_TOKEN_TTL)
            }
        em.persist(row)
    }

    private fun recordRefreshJti(
        user: User,
        tokens: JwtTokens,
    ) {
        val row =
            RefreshToken().apply {
                this.user = user
                this.jti = tokens.refreshJti
                this.expiresAt = tokens.refreshExpiresAt
            }
        em.persist(row)
    }

    companion object {
        val VERIFY_TOKEN_TTL: Duration = Duration.ofDays(2)
        val RESET_TOKEN_TTL: Duration = Duration.ofHours(1)

        /** Baseline scopes granted to a freshly-logged-in user with no org context. */
        val DEFAULT_SCOPES: Set<Scope> = setOf(Scope.ORG_READ, Scope.PROJECTS_READ)
    }
}
