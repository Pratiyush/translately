package io.translately.security.crypto

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.security.SecureRandom
import javax.crypto.AEADBadTagException

class CryptoServiceTest :
    DescribeSpec({

        // Deterministic test KEK: 0x00..0x1F. Real prod keys come from env var.
        fun kek(seed: Byte = 0): ByteArray = ByteArray(CryptoService.KEK_SIZE) { (it + seed).toByte() }

        describe("constructor input validation") {
            it("accepts exactly 32 bytes") {
                CryptoService(kek())
            }

            it("rejects keys shorter than 32 bytes") {
                shouldThrow<IllegalArgumentException> { CryptoService(ByteArray(16)) }
            }

            it("rejects keys longer than 32 bytes") {
                shouldThrow<IllegalArgumentException> { CryptoService(ByteArray(64)) }
            }

            it("rejects an empty key") {
                shouldThrow<IllegalArgumentException> { CryptoService(ByteArray(0)) }
            }
        }

        describe("encrypt/decrypt round-trip (bytes)") {
            val sut = CryptoService(kek())

            it("preserves empty byte arrays") {
                sut.decrypt(sut.encrypt(ByteArray(0))) shouldBe ByteArray(0)
            }

            it("preserves a single byte") {
                val pt = byteArrayOf(42)
                sut.decrypt(sut.encrypt(pt)) shouldBe pt
            }

            it("preserves 100 bytes of cryptographically-random payload") {
                val pt = ByteArray(100).also { SecureRandom().nextBytes(it) }
                sut.decrypt(sut.encrypt(pt)) shouldBe pt
            }

            it("preserves 1 MiB of random payload") {
                val pt = ByteArray(1024 * 1024).also { SecureRandom().nextBytes(it) }
                sut.decrypt(sut.encrypt(pt)) shouldBe pt
            }
        }

        describe("encrypt/decrypt round-trip (strings)") {
            val sut = CryptoService(kek())

            it("preserves ASCII") {
                sut.decryptString(sut.encrypt("sk-anthropic-123456")) shouldBe "sk-anthropic-123456"
            }

            it("preserves Unicode including emoji") {
                val pt = "Hëllö 🌍 Translately — 日本語 العربية"
                sut.decryptString(sut.encrypt(pt)) shouldBe pt
            }

            it("preserves an empty string") {
                sut.decryptString(sut.encrypt("")) shouldBe ""
            }
        }

        describe("non-determinism") {
            val sut = CryptoService(kek())

            it("produces different ciphertexts for the same plaintext (fresh DEK + IVs)") {
                val pt = "secret".toByteArray()
                val ct1 = sut.encrypt(pt)
                val ct2 = sut.encrypt(pt)
                ct1 shouldNotBe ct2
            }

            it("generates 100 unique ciphertexts for the same plaintext") {
                val pt = "same".toByteArray()
                val cts = (1..100).map { sut.encrypt(pt) }.map { it.toList() }.toSet()
                cts shouldHaveSize 100
            }
        }

        describe("envelope structure") {
            val sut = CryptoService(kek())

            it("starts with version byte 0x01") {
                val ct = sut.encrypt("hello".toByteArray())
                ct[0] shouldBe 1.toByte()
            }

            it("is at least the minimum envelope size for empty plaintext") {
                val ct = sut.encrypt(ByteArray(0))
                ct.size shouldBeGreaterThanOrEqual CryptoService.MIN_ENVELOPE_SIZE
            }

            it("has expected fixed overhead (89 bytes) for any plaintext size") {
                val ct = sut.encrypt("hello".toByteArray()) // 5-byte plaintext
                // overhead = 1 (version) + 12 (dek_iv) + 48 (enc_dek) + 12 (data_iv) + 16 (gcm tag)
                ct.size shouldBe 5 + 89
            }
        }

        describe("tamper resistance (AEAD integrity)") {
            val sut = CryptoService(kek())

            it("rejects a bit flip in the encrypted DEK region") {
                val ct = sut.encrypt("hello".toByteArray())
                val tampered = ct.copyOf().also { it[20] = (it[20].toInt() xor 1).toByte() }
                shouldThrow<AEADBadTagException> { sut.decrypt(tampered) }
            }

            it("rejects a bit flip in the encrypted data region") {
                val ct = sut.encrypt("hello".toByteArray())
                val tampered =
                    ct.copyOf().also {
                        it[ct.size - 5] = (it[ct.size - 5].toInt() xor 1).toByte()
                    }
                shouldThrow<AEADBadTagException> { sut.decrypt(tampered) }
            }

            it("rejects a bit flip in the DEK IV") {
                val ct = sut.encrypt("hello".toByteArray())
                // dek_iv starts at byte 1 (after the version byte)
                val tampered = ct.copyOf().also { it[5] = (it[5].toInt() xor 0x80).toByte() }
                shouldThrow<AEADBadTagException> { sut.decrypt(tampered) }
            }

            it("rejects a bit flip in the data IV") {
                val ct = sut.encrypt("hello".toByteArray())
                // data_iv starts at byte 61 (1 + 12 + 48)
                val tampered = ct.copyOf().also { it[65] = (it[65].toInt() xor 0x80).toByte() }
                shouldThrow<AEADBadTagException> { sut.decrypt(tampered) }
            }

            it("rejects truncation (removing the last byte of the GCM tag)") {
                val ct = sut.encrypt("hello".toByteArray())
                val truncated = ct.copyOf(ct.size - 1)
                shouldThrow<AEADBadTagException> { sut.decrypt(truncated) }
            }

            it("rejects decryption with a wrong KEK") {
                val ct = sut.encrypt("hello".toByteArray())
                val other = CryptoService(kek(seed = 1))
                shouldThrow<AEADBadTagException> { other.decrypt(ct) }
            }
        }

        describe("envelope versioning") {
            val sut = CryptoService(kek())

            it("rejects an unknown version byte") {
                val ct = sut.encrypt("hello".toByteArray())
                val bad = ct.copyOf().also { it[0] = 99.toByte() }
                shouldThrow<IllegalArgumentException> { sut.decrypt(bad) }
            }

            it("rejects ciphertext shorter than the minimum envelope size") {
                shouldThrow<IllegalArgumentException> {
                    CryptoService(kek()).decrypt(ByteArray(CryptoService.MIN_ENVELOPE_SIZE - 1))
                }
            }

            it("rejects an empty ciphertext") {
                shouldThrow<IllegalArgumentException> { sut.decrypt(ByteArray(0)) }
            }
        }

        describe("key material isolation") {
            it("does not leak plaintext bytes into the ciphertext at obvious offsets") {
                val sut = CryptoService(kek())
                val pt = "SECRET_PLAINTEXT_MARKER".toByteArray()
                val ct = sut.encrypt(pt)
                // Convert ct to string for a naive substring search. Real attack
                // would use statistical analysis, but this catches the "oops,
                // forgot to encrypt" regression.
                val ctAscii = String(ct.map { if (it in 32..126) it else '.'.code.toByte() }.toByteArray())
                ctAscii.contains(String(pt)) shouldBe false
            }
        }
    })
