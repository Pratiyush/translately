package io.translately.app.keys

import io.translately.data.entity.Key
import io.translately.data.entity.KeyState
import io.translately.data.entity.Namespace
import io.translately.data.entity.Organization
import io.translately.data.entity.OrganizationMember
import io.translately.data.entity.OrganizationRole
import io.translately.data.entity.Project
import io.translately.data.entity.Tag
import io.translately.data.entity.Translation
import io.translately.data.entity.TranslationState
import io.translately.data.entity.User
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import java.time.Instant

/**
 * CDI-scoped helper for the keys integration tests.
 *
 * Seeds a realistic Phase 2 fixture in one call — org + project + caller
 * + namespaces + tags + keys + translations — so search tests stay
 * declarative. Reusable across T206 / T207 / T208 work.
 *
 * `@Transactional` only activates across the CDI proxy, so each test-side
 * DB mutation lives as a method here rather than inline in the test class
 * (where a self-call would bypass the interceptor).
 */
@ApplicationScoped
open class KeysTestHelpers(
    private val em: EntityManager,
) {
    /**
     * Seed one org + project + OWNER user + two namespaces + three tags +
     * ten keys. Five of the keys carry `en` translations; two also carry
     * `de` translations. Returns enough identifiers for the test to
     * compose search queries without re-querying.
     */
    @Transactional
    open fun seedFixture(
        orgSlug: String,
        projectSlug: String,
        email: String,
    ): SeededKeysFixture {
        val project = seedOrgProjectOwner(orgSlug, projectSlug, email)
        val namespaces = seedNamespaces(project)
        val tags = seedTags(project)
        val keys = seedKeys(project, namespaces, tags)
        seedTranslations(keys)
        em.flush()

        return SeededKeysFixture(
            orgExternalId = project.organization.externalId,
            projectExternalId = project.externalId,
            projectSlug = projectSlug,
            ownerExternalId = ownerId(project),
            webNamespaceSlug = namespaces.web.slug,
            iosNamespaceSlug = namespaces.ios.slug,
            tagAuthSlug = tags.auth.slug,
            tagImportantSlug = tags.important.slug,
            tagBillingSlug = tags.billing.slug,
            keyExternalIds = keys.map { it.externalId },
        )
    }

    /** Seed a single extra verified user (used to exercise non-member gating). */
    @Transactional
    open fun seedVerifiedUser(email: String): String {
        val user = makeUser(email, "Outsider")
        em.persist(user)
        em.flush()
        return user.externalId
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private fun seedOrgProjectOwner(
        orgSlug: String,
        projectSlug: String,
        email: String,
    ): Project {
        val org =
            Organization().apply {
                this.slug = orgSlug
                this.name = orgSlug
            }
        em.persist(org)
        val project =
            Project().apply {
                this.organization = org
                this.slug = projectSlug
                this.name = projectSlug
            }
        em.persist(project)
        val user = makeUser(email, "Owner")
        em.persist(user)
        em.persist(
            OrganizationMember().apply {
                this.organization = org
                this.user = user
                this.role = OrganizationRole.OWNER
                this.joinedAt = Instant.now()
            },
        )
        return project
    }

    private fun seedNamespaces(project: Project): Namespaces {
        val web =
            Namespace().apply {
                this.project = project
                this.slug = "web"
                this.name = "Web"
            }
        em.persist(web)
        val ios =
            Namespace().apply {
                this.project = project
                this.slug = "ios"
                this.name = "iOS"
            }
        em.persist(ios)
        return Namespaces(web, ios)
    }

    private fun seedTags(project: Project): Tags {
        val important = persistTag(project, "important", "Important")
        val auth = persistTag(project, "auth", "Auth")
        val billing = persistTag(project, "billing", "Billing")
        return Tags(important, auth, billing)
    }

    private fun persistTag(
        project: Project,
        slug: String,
        name: String,
    ): Tag {
        val tag =
            Tag().apply {
                this.project = project
                this.slug = slug
                this.name = name
            }
        em.persist(tag)
        return tag
    }

    private fun seedKeys(
        project: Project,
        ns: Namespaces,
        tags: Tags,
    ): List<Key> {
        val specs =
            listOf(
                // web — auth + important
                KeySpec(ns.web, "login.title", "Sign in heading on the login screen", setOf(tags.auth, tags.important)),
                // web — auth
                KeySpec(ns.web, "login.button", "Primary sign-in button label", setOf(tags.auth)),
                // web — auth
                KeySpec(ns.web, "password.reset", "Password reset email subject", setOf(tags.auth)),
                // web — billing + important
                KeySpec(
                    ns.web,
                    "billing.invoice",
                    "Invoice heading on the billing page",
                    setOf(tags.billing, tags.important),
                ),
                // web — billing
                KeySpec(ns.web, "billing.total", "Grand total label", setOf(tags.billing)),
                // web — untagged
                KeySpec(ns.web, "footer.copyright", "Footer copyright line", emptySet()),
                // ios — untagged
                KeySpec(ns.ios, "settings.save", "Save button on iOS settings panel", emptySet()),
                // ios — billing
                KeySpec(ns.ios, "purchase.confirm", "Confirm purchase dialog body", setOf(tags.billing)),
                // ios — important, REVIEW
                KeySpec(
                    ns.ios,
                    "welcome.greeting",
                    "Welcome screen greeting",
                    setOf(tags.important),
                    state = KeyState.REVIEW,
                ),
                // ios — untagged, DONE
                KeySpec(
                    ns.ios,
                    "logout.confirm",
                    "Log out confirmation",
                    emptySet(),
                    state = KeyState.DONE,
                ),
            )
        return specs.map { persistKey(project, it) }
    }

    private fun persistKey(
        project: Project,
        spec: KeySpec,
    ): Key {
        val key =
            Key().apply {
                this.project = project
                this.namespace = spec.namespace
                this.keyName = spec.keyName
                this.description = spec.description
                this.state = spec.state
            }
        key.tags.addAll(spec.tags)
        em.persist(key)
        return key
    }

    private fun seedTranslations(keys: List<Key>) {
        persistTranslation(keys[0], "en", "Sign in")
        persistTranslation(keys[1], "en", "Log in")
        persistTranslation(keys[3], "en", "Invoice")
        persistTranslation(keys[6], "en", "Save")
        persistTranslation(keys[8], "en", "Welcome to Translately")
        persistTranslation(keys[0], "de", "Anmelden")
        persistTranslation(keys[8], "de", "Willkommen bei Translately")
    }

    private fun persistTranslation(
        key: Key,
        languageTag: String,
        value: String,
    ) {
        em.persist(
            Translation().apply {
                this.key = key
                this.languageTag = languageTag
                this.value = value
                this.state = TranslationState.TRANSLATED
            },
        )
    }

    private fun makeUser(
        email: String,
        fullName: String,
    ): User =
        User().apply {
            this.email = email
            this.fullName = fullName
            this.passwordHash = "\$argon2id\$v=19\$m=65536,t=3,p=4\$notreal"
            this.emailVerifiedAt = Instant.now()
        }

    private fun ownerId(project: Project): String =
        em
            .createQuery(
                """
                SELECT m.user.externalId FROM OrganizationMember m
                WHERE m.organization.id = :orgId AND m.role = io.translately.data.entity.OrganizationRole.OWNER
                """.trimIndent(),
                String::class.java,
            ).setParameter("orgId", project.organization.id)
            .singleResult

    private data class Namespaces(
        val web: Namespace,
        val ios: Namespace,
    )

    private data class Tags(
        val important: Tag,
        val auth: Tag,
        val billing: Tag,
    )

    private data class KeySpec(
        val namespace: Namespace,
        val keyName: String,
        val description: String,
        val tags: Set<Tag>,
        val state: KeyState = KeyState.NEW,
    )
}

/** Tuple returned by [KeysTestHelpers.seedFixture]. */
data class SeededKeysFixture(
    val orgExternalId: String,
    val projectExternalId: String,
    val projectSlug: String,
    val ownerExternalId: String,
    val webNamespaceSlug: String,
    val iosNamespaceSlug: String,
    val tagAuthSlug: String,
    val tagImportantSlug: String,
    val tagBillingSlug: String,
    val keyExternalIds: List<String>,
)
