package io.translately.data

import java.security.SecureRandom
import java.time.Instant

/**
 * Crockford-base32 ULID generator (RFC draft-eernst-lexical-lineage-01 / ulid/spec).
 *
 * 26 chars total: 10 timestamp (millis since epoch, sortable) + 16 random entropy.
 * Stored as `text` in Postgres with a unique index. We don't store ULIDs as UUIDs
 * because the sort-by-timestamp property is load-bearing for paginated queries.
 *
 * Thread-safe; a single shared [SecureRandom] is fine for our throughput.
 */
object Ulid {
    private const val ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val LENGTH = 26
    private const val TIMESTAMP_CHARS = 10
    private const val RANDOM_CHARS = 16
    private const val TIMESTAMP_MAX_MILLIS = (1L shl 48) - 1L // 2^48 - 1 ≈ year 10889

    private val rng = SecureRandom()

    /** Generate a new ULID based on the current wall clock. */
    fun generate(): String = generate(Instant.now())

    /** Generate a ULID with an explicit timestamp (useful for tests). */
    fun generate(instant: Instant): String {
        val millis = instant.toEpochMilli()
        require(millis in 0..TIMESTAMP_MAX_MILLIS) { "timestamp out of ULID range: $millis" }
        val sb = StringBuilder(LENGTH)
        encodeTimestamp(millis, sb)
        encodeRandom(sb)
        return sb.toString()
    }

    /** Parse a ULID back into its timestamp component; returns null if malformed. */
    fun extractTimestamp(ulid: String): Instant? {
        if (ulid.length != LENGTH) return null
        var millis = 0L
        for (i in 0 until TIMESTAMP_CHARS) {
            val v = ENCODING.indexOf(ulid[i].uppercaseChar())
            if (v < 0) return null
            millis = (millis shl 5) or v.toLong()
        }
        return Instant.ofEpochMilli(millis)
    }

    /** Is `candidate` a syntactically-valid ULID? */
    fun isValid(candidate: String): Boolean {
        if (candidate.length != LENGTH) return false
        return candidate.uppercase().all { it in ENCODING }
    }

    private fun encodeTimestamp(
        millis: Long,
        out: StringBuilder,
    ) {
        var remaining = millis
        val chars = CharArray(TIMESTAMP_CHARS)
        for (i in TIMESTAMP_CHARS - 1 downTo 0) {
            chars[i] = ENCODING[(remaining and 0x1F).toInt()]
            remaining = remaining ushr 5
        }
        out.append(chars)
    }

    private fun encodeRandom(out: StringBuilder) {
        val bytes = ByteArray(RANDOM_CHARS)
        rng.nextBytes(bytes)
        for (b in bytes) {
            out.append(ENCODING[(b.toInt() and 0x1F)])
        }
    }
}
