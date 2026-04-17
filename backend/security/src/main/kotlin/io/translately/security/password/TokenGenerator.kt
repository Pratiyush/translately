package io.translately.security.password

import java.security.SecureRandom
import java.util.Base64

/**
 * Generator for cryptographically-random, URL-safe, single-use tokens.
 *
 * Used for:
 *  - Email-verification tokens (Phase 1 T103)
 *  - Password-reset tokens (Phase 1 T103)
 *  - The secret half of API keys and PATs (T110)
 *
 * Output is base64url-without-padding so the full token fits in any URL
 * query parameter or `Authorization:` header without escaping. Default 32
 * raw bytes → 43 encoded characters.
 *
 * **Always store only the Argon2id hash of the token**, never the raw
 * value. Show the raw value to the user once (on creation) and throw it
 * away after.
 */
object TokenGenerator {
    private val rng = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    /** Generate a token backed by `bytes` random bytes (default 32 → 43 chars). */
    fun generate(bytes: Int = DEFAULT_BYTES): String {
        require(bytes in MIN_BYTES..MAX_BYTES) {
            "Token entropy must be between $MIN_BYTES and $MAX_BYTES bytes, got $bytes"
        }
        val buf = ByteArray(bytes)
        rng.nextBytes(buf)
        return encoder.encodeToString(buf)
    }

    /**
     * Generate a token with an optional human-readable prefix.
     *
     * Used by API keys and PATs to signal provenance in logs and UIs:
     *   "tr_apikey_abc123…" so an API key is visually distinct from a PAT.
     * The prefix is NOT cryptographic — it's just a display convention.
     */
    fun generatePrefixed(
        prefix: String,
        bytes: Int = DEFAULT_BYTES,
    ): String = "$prefix${generate(bytes)}"

    const val DEFAULT_BYTES: Int = 32
    const val MIN_BYTES: Int = 16
    const val MAX_BYTES: Int = 128
}
