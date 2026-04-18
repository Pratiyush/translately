# Crypto — envelope encryption

Translately encrypts at-rest secrets using AES-256-GCM envelope encryption. The only secret outside the database is the **Key Encryption Key (KEK)**, injected by the operator at boot via an environment variable. Every secret in the database gets its own **Data Encryption Key (DEK)**, and the DEK itself is stored encrypted alongside the ciphertext.

Introduced by: [T112](https://github.com/Pratiyush/translately/issues/136).
Primary consumer: Phase 4 — per-project BYOK AI API keys (`projects.ai_api_key_encrypted`).

Related: [data-model](data-model.md), [hardening](../self-hosting/hardening.md).

## Why envelope encryption?

A single "encrypt with the master key" scheme forces every row to share a key. Rotating that key requires re-encrypting every row (expensive) and one compromised ciphertext weakens the whole dataset.

Envelope encryption fixes both:

- Each row gets its own DEK → a compromised ciphertext leaks exactly that one secret.
- Rotating the KEK means re-wrapping each DEK, not re-encrypting each payload — O(envelopes) with constant-size work per envelope.
- The KEK never leaves the JVM's memory; it's not in the database.

## Envelope layout

Implemented in [`io.translately.security.crypto.CryptoService`](../../backend/security/src/main/kotlin/io/translately/security/crypto/CryptoService.kt). Every envelope is a single `bytea`:

| Offset | Bytes | Meaning |
|---|---|---|
| 0 | 1 | `version` — currently `0x01` |
| 1 | 12 | IV for DEK encryption (GCM) |
| 13 | 48 | `AES-GCM(KEK, IV_dek, DEK)` — 32-byte DEK + 16-byte auth tag |
| 61 | 12 | IV for data encryption (GCM) |
| 73 | N + 16 | `AES-GCM(DEK, IV_data, plaintext)` — N payload bytes + 16-byte auth tag |

Minimum envelope length (empty plaintext): **89 bytes**. Constants are exposed via `CryptoService.Companion` for test assertions.

## Guarantees

- **Confidentiality** — AES-256-GCM at both layers.
- **Integrity + authentication** — the 128-bit GCM tag catches any single-bit flip, reorder, or truncation. `decrypt` throws `AEADBadTagException` on tampering.
- **Non-determinism** — two calls to `encrypt(plaintext)` with the same input produce different envelopes (fresh DEK and IVs each time). Observers of the `bytea` column cannot deduplicate or compare ciphertexts.
- **Forward version compatibility** — the leading version byte lets us migrate to a different scheme later (key-wrapped, post-quantum, HSM-backed) without breaking old rows. `decrypt` rejects unsupported versions.

## Operator setup

One env var at boot:

```bash
TRANSLATELY_CRYPTO_MASTER_KEY=<base64 of 32 random bytes>
```

Generate with:

```bash
openssl rand -base64 32
```

A wrong-size key fails fast in the CDI producer (`CryptoServiceProducer`) with a clear error — the app refuses to start rather than silently picking up a truncated key.

**Rotate** by:

1. Generate a new KEK.
2. Deploy with both keys available (`TRANSLATELY_CRYPTO_MASTER_KEY_OLD` + `TRANSLATELY_CRYPTO_MASTER_KEY`) — the migration CLI decrypts with old, re-encrypts with new.
3. After the migration finishes, remove `_OLD` and restart.

The rotation tool itself ships alongside Phase 4 once real envelopes exist; Phase 1 only lays the primitive down.

## Usage from service code

```kotlin
class ProjectAiService(
    private val crypto: CryptoService,
    private val projects: ProjectRepository,
) {
    fun storeKey(projectId: Long, apiKey: String) {
        val envelope = crypto.encrypt(apiKey)
        projects.updateAiKeyEncrypted(projectId, envelope)
    }

    fun loadKey(projectId: Long): String? {
        val envelope = projects.findAiKeyEncrypted(projectId) ?: return null
        return crypto.decryptString(envelope)
    }
}
```

The service is the only layer allowed to hold plaintext secrets in memory. JAX-RS resources, controllers, and every module above `:backend:service` receive an opaque `bytea` or a domain object that never exposes plaintext.

## Defensive details

- The in-memory DEK buffer is zero-filled immediately after use. The JVM may have copied it during `ByteBuffer.put`, but we clear the reference we own. This is belt-and-braces; GCM already makes ciphertext-only attacks impossible.
- `CryptoService` is **stateless** and safe for concurrent use. The underlying `Cipher` is per-call (`Cipher.getInstance("AES/GCM/NoPadding")`); JCE cipher instances are not thread-safe and must not be cached across calls.
- The KEK is held as a `SecretKeySpec` built from a defensive `kekBytes.copyOf()` so the operator's caller can zero its own buffer.

## Testing

`CryptoServiceTest` in `:backend:security` asserts:

- Round-trip for empty, short, and large payloads.
- Non-determinism (two encrypts of the same plaintext differ).
- Tamper detection (flip any byte → `AEADBadTagException`).
- Wrong-KEK rejection.
- Version byte rejection for unknown versions.
- Invalid KEK length → `IllegalArgumentException` at construction.

No Quarkus or Docker is required — these are pure JCE unit tests.

## Not in scope

- **KMS integration** (AWS KMS, GCP KMS, Vault Transit) — Phase 7 will add a `KekProvider` port so self-hosters who have a KMS can delegate key material. The envelope format stays the same.
- **Field-level deterministic encryption** — we don't encrypt anything we'd need to index, so a non-deterministic scheme is strictly better.
- **Transport encryption** — handled at the reverse proxy. See [hardening](../self-hosting/hardening.md).
