package io.translately.security.crypto

import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.util.Base64

/**
 * CDI producer for [CryptoService]. Reads the base64-encoded master key from
 * `translately.crypto.master-key` (settable via `TRANSLATELY_CRYPTO_MASTER_KEY`
 * env var). Ships a dev/test default so local work doesn't need setup, but
 * **production profiles must override this with a real 32-byte key**.
 *
 * Generate a fresh master key with:
 * ```
 * openssl rand -base64 32
 * ```
 */
@ApplicationScoped
class CryptoServiceProducer {
    @Produces
    @ApplicationScoped
    fun cryptoService(
        @ConfigProperty(name = "translately.crypto.master-key") keyBase64: String,
    ): CryptoService = CryptoService(Base64.getDecoder().decode(keyBase64))
}
