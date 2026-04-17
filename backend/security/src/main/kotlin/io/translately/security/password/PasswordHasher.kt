package io.translately.security.password

import de.mkammerer.argon2.Argon2
import de.mkammerer.argon2.Argon2Factory
import jakarta.enterprise.context.ApplicationScoped

/**
 * Argon2id password hasher.
 *
 * Used for:
 *  - User login password verification (Phase 1 T103 signup).
 *  - API key / PAT secret hashing (T110).
 *  - Password-reset + email-verification token hashing (T103).
 *
 * ### Parameters
 *
 * - **iterations (time cost):** 3
 * - **memory:** 64 MiB (65 536 KiB)
 * - **parallelism:** 4
 *
 * These match the OWASP "m=64 MiB, t=3, p=4" guideline for Argon2id and
 * take ~30-60 ms per hash on a modern server CPU — slow enough to frustrate
 * brute force, fast enough to not matter in a request path.
 *
 * The Argon2 output string is self-describing: it embeds the algorithm,
 * version, parameters, salt, and hash, so we can upgrade parameters later
 * without schema changes — `verify` handles old and new formats.
 *
 * ### Storage
 *
 * Store the full output string in a single `VARCHAR(256)` column. Never
 * store the raw password, not even transiently in the DB.
 */
@ApplicationScoped
open class PasswordHasher(
    private val argon2: Argon2 = defaultArgon2(),
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val memoryKib: Int = DEFAULT_MEMORY_KIB,
    private val parallelism: Int = DEFAULT_PARALLELISM,
) {
    /** Hash `raw`, returning the Argon2 self-describing encoded string. */
    open fun hash(raw: String): String {
        val chars = raw.toCharArray()
        try {
            return argon2.hash(iterations, memoryKib, parallelism, chars)
        } finally {
            argon2.wipeArray(chars)
        }
    }

    /**
     * Verify `raw` against a previously-produced `encodedHash`.
     *
     * Returns `false` for any malformed hash, wrong password, or null-ish
     * input; never throws on input shape. Constant-time comparison inside
     * the library.
     */
    open fun verify(
        raw: String,
        encodedHash: String?,
    ): Boolean {
        if (encodedHash.isNullOrBlank()) return false
        val chars = raw.toCharArray()
        return try {
            argon2.verify(encodedHash, chars)
        } catch (_: RuntimeException) {
            // argon2-jvm throws on malformed inputs; treat as mismatch.
            false
        } finally {
            argon2.wipeArray(chars)
        }
    }

    companion object {
        const val DEFAULT_ITERATIONS: Int = 3
        const val DEFAULT_MEMORY_KIB: Int = 65_536 // 64 MiB
        const val DEFAULT_PARALLELISM: Int = 4

        /** Salt length = 16 bytes; hash length = 32 bytes. argon2-jvm defaults. */
        fun defaultArgon2(): Argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
    }
}
