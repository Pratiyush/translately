package io.translately.data.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.Instant

/**
 * A localization project. The unit of authorization for translations, keys,
 * screenshots, webhooks, glossaries, and the AI provider config.
 *
 * AI fields are all nullable — the platform must function with zero AI
 * configured. When a provider is set, the API key is envelope-encrypted at
 * rest via `security/CryptoService` (T112).
 */
@Entity
@Table(
    name = "projects",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_projects_org_slug", columnNames = ["organization_id", "slug"]),
    ],
    indexes = [
        Index(name = "idx_projects_organization", columnList = "organization_id"),
    ],
)
class Project : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, foreignKey = ForeignKey(name = "fk_projects_organization"))
    lateinit var organization: Organization

    @Column(name = "slug", nullable = false, length = 64)
    var slug: String = ""
        set(value) {
            field = value.trim().lowercase()
        }

    @Column(name = "name", nullable = false, length = 128)
    var name: String = ""

    @Column(name = "description", length = 1024)
    var description: String? = null

    /** BCP 47 tag for the source language of the project (e.g. `en`, `en-US`, `de`). */
    @Column(name = "base_language_tag", nullable = false, length = 32)
    var baseLanguageTag: String = "en"

    // ---- AI / BYOK (all nullable) ----

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_provider", length = 32)
    var aiProvider: AiProvider? = null

    @Column(name = "ai_model", length = 128)
    var aiModel: String? = null

    /** Optional override for OPENAI_COMPATIBLE endpoints (Ollama, vLLM, etc.). */
    @Column(name = "ai_base_url", length = 512)
    var aiBaseUrl: String? = null

    /** Envelope-encrypted API key bytes. Raw key is never persisted. */
    @Column(name = "ai_api_key_encrypted")
    var aiApiKeyEncrypted: ByteArray? = null

    /** Monthly USD cap; when usage meets or exceeds this, the provider auto-disables. */
    @Column(name = "ai_budget_cap_usd_monthly", precision = 12, scale = 2)
    var aiBudgetCapUsdMonthly: BigDecimal? = null

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null

    val hasAi: Boolean get() = aiProvider != null && aiApiKeyEncrypted != null
}
