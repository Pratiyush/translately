package io.translately.security.crypto

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Envelope-encryption primitive for at-rest secrets (per-project AI API keys
 * in Phase 4, potentially more later).
 *
 * ### How it works
 *
 * 1. A single **Key Encryption Key** (KEK, 32 bytes) is provided at boot by
 *    the operator via the `TRANSLATELY_CRYPTO_MASTER_KEY` env var (base64).
 *    This is the *only* secret outside the database.
 * 2. For every piece of plaintext we encrypt, a fresh **Data Encryption Key**
 *    (DEK, 32 bytes) is generated.
 * 3. The plaintext is encrypted with the DEK using AES-256-GCM.
 * 4. The DEK is encrypted with the KEK using AES-256-GCM (separate IV).
 * 5. The resulting envelope (version + dek_iv + enc_dek + data_iv + enc_data)
 *    is persisted as a single opaque `bytea` column.
 *
 * Rotating the KEK means re-encrypting every envelope (decrypt with old KEK,
 * encrypt with new KEK). DEKs never need rotation because each secret has
 * its own.
 *
 * ### Envelope layout
 *
 * ```
 *  offset  bytes  meaning
 *  ------  -----  --------------------------------------------
 *       0      1  version (currently 0x01)
 *       1     12  IV for DEK encryption
 *      13     48  AES-GCM(KEK, IV_dek, DEK)  (32-byte DEK + 16-byte tag)
 *      61     12  IV for data encryption
 *      73  N+16  AES-GCM(DEK, IV_data, plaintext)  (N bytes + 16-byte tag)
 * ```
 *
 * Minimum envelope length for an empty plaintext: 73 + 16 = **89 bytes**.
 *
 * ### Guarantees
 *
 * - Confidentiality: AES-256-GCM at both layers.
 * - Integrity + authentication: GCM tag catches any single-bit flip or
 *   truncation; [decrypt] throws `AEADBadTagException`.
 * - Non-determinism: two calls to [encrypt] with the same plaintext produce
 *   different ciphertexts (fresh DEK + IVs each time).
 * - Forward version compatibility: the version byte lets us migrate to a
 *   different scheme later without breaking old rows.
 */
class CryptoService(
    kekBytes: ByteArray,
) {
    init {
        require(kekBytes.size == KEK_SIZE) {
            "Master key (KEK) must be $KEK_SIZE bytes for AES-256-GCM, got ${kekBytes.size}"
        }
    }

    private val kek = SecretKeySpec(kekBytes.copyOf(), "AES")
    private val rng = SecureRandom()

    /** Encrypt a byte array, returning a self-describing envelope. */
    fun encrypt(plaintext: ByteArray): ByteArray {
        val dekBytes = ByteArray(DEK_SIZE).also(rng::nextBytes)
        val dek = SecretKeySpec(dekBytes, "AES")
        val dekIv = ByteArray(IV_SIZE).also(rng::nextBytes)
        val dataIv = ByteArray(IV_SIZE).also(rng::nextBytes)

        val encryptedDek = aesGcm(Cipher.ENCRYPT_MODE, kek, dekIv, dekBytes)
        val encryptedData = aesGcm(Cipher.ENCRYPT_MODE, dek, dataIv, plaintext)

        val envelope =
            ByteBuffer
                .allocate(1 + IV_SIZE + encryptedDek.size + IV_SIZE + encryptedData.size)
                .put(ENVELOPE_VERSION)
                .put(dekIv)
                .put(encryptedDek)
                .put(dataIv)
                .put(encryptedData)
                .array()

        // Best-effort wipe of the in-memory DEK; the JVM may have copied it but
        // we still clear the one we hold.
        dekBytes.fill(0)

        return envelope
    }

    /** Convenience overload: encrypt a UTF-8 string. */
    fun encrypt(plaintext: String): ByteArray = encrypt(plaintext.toByteArray(Charsets.UTF_8))

    /**
     * Decrypt an envelope produced by [encrypt]. Throws on any tampering,
     * truncation, wrong KEK, or unsupported version.
     */
    fun decrypt(ciphertext: ByteArray): ByteArray {
        require(ciphertext.size >= MIN_ENVELOPE_SIZE) {
            "Ciphertext too short (${ciphertext.size} bytes) to be a valid envelope"
        }
        val buf = ByteBuffer.wrap(ciphertext)

        val version = buf.get()
        require(version == ENVELOPE_VERSION) {
            "Unsupported envelope version: $version (expected $ENVELOPE_VERSION)"
        }

        val dekIv = ByteArray(IV_SIZE).also { buf.get(it) }
        val encryptedDek = ByteArray(ENCRYPTED_DEK_SIZE).also { buf.get(it) }
        val dataIv = ByteArray(IV_SIZE).also { buf.get(it) }
        val encryptedData = ByteArray(buf.remaining()).also { buf.get(it) }

        val dekBytes = aesGcm(Cipher.DECRYPT_MODE, kek, dekIv, encryptedDek)
        try {
            val dek = SecretKeySpec(dekBytes, "AES")
            return aesGcm(Cipher.DECRYPT_MODE, dek, dataIv, encryptedData)
        } finally {
            dekBytes.fill(0)
        }
    }

    /** Convenience overload: decrypt into a UTF-8 string. */
    fun decryptString(ciphertext: ByteArray): String = String(decrypt(ciphertext), Charsets.UTF_8)

    private fun aesGcm(
        mode: Int,
        key: SecretKeySpec,
        iv: ByteArray,
        data: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, key, GCMParameterSpec(TAG_SIZE_BITS, iv))
        return cipher.doFinal(data)
    }

    companion object {
        const val ENVELOPE_VERSION: Byte = 1
        const val KEK_SIZE: Int = 32
        const val DEK_SIZE: Int = 32
        const val IV_SIZE: Int = 12
        const val TAG_SIZE_BITS: Int = 128
        const val TAG_SIZE_BYTES: Int = TAG_SIZE_BITS / 8
        const val ENCRYPTED_DEK_SIZE: Int = DEK_SIZE + TAG_SIZE_BYTES
        const val MIN_ENVELOPE_SIZE: Int = 1 + IV_SIZE + ENCRYPTED_DEK_SIZE + IV_SIZE + TAG_SIZE_BYTES
    }
}
