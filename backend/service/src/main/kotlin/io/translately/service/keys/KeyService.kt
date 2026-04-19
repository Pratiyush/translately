package io.translately.service.keys

import io.translately.data.entity.ActivityType
import io.translately.data.entity.Key
import io.translately.data.entity.KeyState
import io.translately.data.entity.Namespace
import io.translately.data.entity.Project
import io.translately.data.entity.Translation
import io.translately.data.entity.TranslationState
import io.translately.data.entity.User
import io.translately.service.orgs.OrgException
import io.translately.service.orgs.OrgService
import io.translately.service.orgs.SlugNormalizer
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.jboss.logging.Logger
import java.time.Instant

/**
 * Use-case entry point for translation-key and namespace management
 * (T208). Follows the same shape as `ProjectService`: every operation
 * resolves the caller's org membership first (via
 * `OrgService.requireMembership`) so non-members surface `NotFound`.
 *
 * The ICU validator (T203) plugs into `upsertTranslation` once landed;
 * for now the service accepts any string and flips the per-cell state.
 *
 * Namespaces ship alongside keys because a key can't exist without a
 * namespace. Tag CRUD will land in a follow-up; see the PR body.
 */
@ApplicationScoped
open class KeyService(
    private val em: EntityManager,
    private val orgService: OrgService,
) {
    private val log = Logger.getLogger(KeyService::class.java)

    // ------------------------------------------------------------------
    // Keys
    // ------------------------------------------------------------------

    /** Paged list of keys in a project, optionally filtered by namespace slug. */
    @Transactional
    open fun list(
        callerExternalId: String,
        orgSlugOrId: String,
        projectSlugOrId: String,
        filter: ListKeysFilter = ListKeysFilter(),
    ): List<KeySummary> {
        val project = resolveProject(callerExternalId, orgSlugOrId, projectSlugOrId)
        val nsFilter =
            filter.namespaceSlug
                ?.trim()
                ?.lowercase()
                ?.takeIf(String::isNotEmpty)
        val jpql =
            buildString {
                append("SELECT k FROM Key k WHERE k.project = :project AND k.softDeletedAt IS NULL ")
                if (nsFilter != null) append("AND k.namespace.slug = :ns ")
                append("ORDER BY k.keyName")
            }
        val q =
            em
                .createQuery(jpql, Key::class.java)
                .setParameter("project", project)
                .setFirstResult(filter.offset.coerceAtLeast(0))
                .setMaxResults(filter.limit.coerceIn(1, MAX_LIMIT))
        if (nsFilter != null) q.setParameter("ns", nsFilter)
        return q.resultList.map(::toSummary)
    }

    /** One key with its translations, tags, and meta. */
    @Transactional
    open fun get(
        callerExternalId: String,
        orgSlugOrId: String,
        projectSlugOrId: String,
        keyIdOrName: String,
    ): KeyDetails {
        val key = resolveKey(callerExternalId, orgSlugOrId, projectSlugOrId, keyIdOrName)
        val translations =
            em
                .createQuery(
                    "SELECT t FROM Translation t WHERE t.key = :k ORDER BY t.languageTag",
                    Translation::class.java,
                ).setParameter("k", key)
                .resultList
                .map(::toTranslationSummary)
        return KeyDetails(
            key = toSummary(key),
            translations = translations,
        )
    }

    /** Create a key inside a namespace. */
    @Transactional
    open fun create(
        callerExternalId: String,
        orgSlugOrId: String,
        projectSlugOrId: String,
        body: CreateKeyRequest,
    ): KeySummary {
        val project = resolveProject(callerExternalId, orgSlugOrId, projectSlugOrId)
        val name = body.keyName.trim()
        val nsSlug =
            body.namespaceSlug
                ?.trim()
                ?.lowercase()
                .orEmpty()
        validateCreate(name, nsSlug)
        val ns = findNamespace(project, nsSlug) ?: throw OrgException.NotFound("Namespace")
        return persistKey(project, ns, name, body.description, callerExternalId)
    }

    private fun persistKey(
        project: Project,
        ns: Namespace,
        name: String,
        rawDescription: String?,
        callerExternalId: String,
    ): KeySummary {
        if (keyNameExists(project, ns, name)) throw OrgException.SlugTaken(name, "key")
        val key =
            Key().apply {
                this.project = project
                this.namespace = ns
                this.keyName = name
                this.description = rawDescription?.trim()?.takeIf(String::isNotEmpty)
            }
        em.persist(key)
        em.flush()
        writeActivity(key, callerExternalId, ActivityType.CREATED)
        log.infov("created key {0} ({1}) in project {2}", key.externalId, name, project.externalId)
        return toSummary(key)
    }

    /** Rename, reclassify, change state, edit description. */
    @Transactional
    open fun update(
        callerExternalId: String,
        orgSlugOrId: String,
        projectSlugOrId: String,
        keyIdOrName: String,
        body: UpdateKeyRequest,
    ): KeySummary {
        val key = resolveKey(callerExternalId, orgSlugOrId, projectSlugOrId, keyIdOrName)
        val newName = body.keyName?.trim()?.also(::validateKeyName)
        val parsedState = body.stateName?.let(::parseKeyState)
        val structuralChange =
            (newName != null && newName != key.keyName) ||
                body.namespaceSlug != null ||
                body.description != null

        val stateChanged = applyUpdate(key, newName, body, parsedState)
        em.merge(key)
        if (structuralChange) writeActivity(key, callerExternalId, ActivityType.UPDATED)
        if (stateChanged) writeActivity(key, callerExternalId, ActivityType.STATE_CHANGED)
        return toSummary(key)
    }

    /** Mutate [key] in place; returns `true` when the state field changed. */
    private fun applyUpdate(
        key: Key,
        newName: String?,
        body: UpdateKeyRequest,
        parsedState: KeyState?,
    ): Boolean {
        if (body.namespaceSlug != null) {
            val ns =
                findNamespace(key.project, body.namespaceSlug.trim().lowercase())
                    ?: throw OrgException.NotFound("Namespace")
            key.namespace = ns
        }
        if (newName != null) key.keyName = newName
        if (body.description != null) {
            key.description = body.description.trim().takeIf(String::isNotEmpty)
        }
        val stateChanged = parsedState != null && parsedState != key.state
        if (parsedState != null) key.state = parsedState
        return stateChanged
    }

    /** Soft-delete. Idempotent. */
    @Transactional
    open fun softDelete(
        callerExternalId: String,
        orgSlugOrId: String,
        projectSlugOrId: String,
        keyIdOrName: String,
    ) {
        val key = resolveKey(callerExternalId, orgSlugOrId, projectSlugOrId, keyIdOrName)
        if (key.softDeletedAt != null) return
        key.softDeletedAt = Instant.now()
        em.merge(key)
        writeActivity(key, callerExternalId, ActivityType.DELETED)
        log.infov("soft-deleted key {0}", key.externalId)
    }

    /**
     * Upsert one translation cell. No ICU validation yet — T203's validator
     * plugs in here once landed. The per-cell state flips to DRAFT on a
     * non-empty value, EMPTY on a blank one, unless the caller explicitly
     * supplies a state.
     */
    @Transactional
    open fun upsertTranslation(
        callerExternalId: String,
        target: TranslationTarget,
        body: UpdateTranslationRequest,
    ): TranslationSummary {
        val key = resolveKey(callerExternalId, target.orgSlugOrId, target.projectSlugOrId, target.keyIdOrName)
        val tag = target.languageTag.trim()
        if (tag.isEmpty()) {
            throw OrgException.ValidationFailed(
                listOf(OrgException.ValidationFailed.FieldError("languageTag", "REQUIRED")),
            )
        }
        if (!isLanguageConfigured(key.project, tag)) throw OrgException.NotFound("Language")

        val derivedState =
            body.stateName?.let(::parseTranslationState)
                ?: if (body.value.isBlank()) TranslationState.EMPTY else TranslationState.DRAFT
        val row = persistTranslation(key, callerExternalId, tag, body.value, derivedState)

        // Advisory: move key out of NEW once a first non-empty translation arrives.
        if (key.state == KeyState.NEW && body.value.isNotBlank()) {
            key.state = KeyState.TRANSLATING
            em.merge(key)
            writeActivity(key, callerExternalId, ActivityType.STATE_CHANGED)
        }
        writeActivity(key, callerExternalId, ActivityType.TRANSLATED)
        em.flush()
        return toTranslationSummary(row)
    }

    private fun persistTranslation(
        key: Key,
        callerExternalId: String,
        tag: String,
        value: String,
        state: TranslationState,
    ): Translation {
        val existing =
            em
                .createQuery(
                    """
                    SELECT t FROM Translation t
                    WHERE t.key = :k AND t.languageTag = :tag
                    """.trimIndent(),
                    Translation::class.java,
                ).setParameter("k", key)
                .setParameter("tag", tag)
                .resultList
                .firstOrNull()
        if (existing != null) {
            existing.value = value
            existing.state = state
            existing.author = findUser(callerExternalId)
            return em.merge(existing)
        }
        val fresh =
            Translation().apply {
                this.key = key
                this.languageTag = tag
                this.value = value
                this.state = state
                this.author = findUser(callerExternalId)
            }
        em.persist(fresh)
        return fresh
    }

    // ------------------------------------------------------------------
    // Namespaces
    // ------------------------------------------------------------------

    @Transactional
    open fun listNamespaces(
        callerExternalId: String,
        orgSlugOrId: String,
        projectSlugOrId: String,
    ): List<NamespaceSummary> {
        val project = resolveProject(callerExternalId, orgSlugOrId, projectSlugOrId)
        return em
            .createQuery(
                "SELECT n FROM Namespace n WHERE n.project = :p ORDER BY n.name",
                Namespace::class.java,
            ).setParameter("p", project)
            .resultList
            .map(::toNamespaceSummary)
    }

    @Transactional
    open fun createNamespace(
        callerExternalId: String,
        orgSlugOrId: String,
        projectSlugOrId: String,
        body: CreateNamespaceRequest,
    ): NamespaceSummary {
        val project = resolveProject(callerExternalId, orgSlugOrId, projectSlugOrId)
        val name = body.name.trim()
        val slug = validateNamespace(name, body.slug)
        if (findNamespace(project, slug) != null) throw OrgException.SlugTaken(slug, "namespace")
        val ns =
            Namespace().apply {
                this.project = project
                this.slug = slug
                this.name = name
                this.description = body.description?.trim()?.takeIf(String::isNotEmpty)
            }
        em.persist(ns)
        em.flush()
        log.infov("created namespace {0} ({1}) in project {2}", ns.externalId, slug, project.externalId)
        return toNamespaceSummary(ns)
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private fun resolveProject(
        callerExternalId: String,
        orgSlugOrId: String,
        projectSlugOrId: String,
    ): Project {
        orgService.requireMembership(callerExternalId, orgSlugOrId)
        val orgKey = orgSlugOrId.trim().lowercase()
        val projKey = projectSlugOrId.trim().lowercase()
        return em
            .createQuery(
                """
                SELECT p FROM Project p
                WHERE (p.organization.slug = :orgKey OR p.organization.externalId = :orgKey)
                  AND (p.slug = :projKey OR p.externalId = :projKey)
                  AND p.deletedAt IS NULL
                """.trimIndent(),
                Project::class.java,
            ).setParameter("orgKey", orgKey)
            .setParameter("projKey", projKey)
            .resultList
            .firstOrNull() ?: throw OrgException.NotFound("Project")
    }

    private fun resolveKey(
        callerExternalId: String,
        orgSlugOrId: String,
        projectSlugOrId: String,
        keyIdOrName: String,
    ): Key {
        val project = resolveProject(callerExternalId, orgSlugOrId, projectSlugOrId)
        val needle = keyIdOrName.trim()
        return em
            .createQuery(
                """
                SELECT k FROM Key k
                WHERE k.project = :project
                  AND (k.externalId = :needle OR k.keyName = :needle)
                  AND k.softDeletedAt IS NULL
                """.trimIndent(),
                Key::class.java,
            ).setParameter("project", project)
            .setParameter("needle", needle)
            .resultList
            .firstOrNull() ?: throw OrgException.NotFound("Key")
    }

    private fun findNamespace(
        project: Project,
        slug: String,
    ): Namespace? =
        em
            .createQuery(
                "SELECT n FROM Namespace n WHERE n.project = :p AND n.slug = :slug",
                Namespace::class.java,
            ).setParameter("p", project)
            .setParameter("slug", slug)
            .resultList
            .firstOrNull()

    private fun keyNameExists(
        project: Project,
        namespace: Namespace,
        keyName: String,
    ): Boolean =
        em
            .createQuery(
                """
                SELECT COUNT(k) FROM Key k
                WHERE k.project = :p AND k.namespace = :ns AND k.keyName = :name
                  AND k.softDeletedAt IS NULL
                """.trimIndent(),
                java.lang.Long::class.java,
            ).setParameter("p", project)
            .setParameter("ns", namespace)
            .setParameter("name", keyName)
            .singleResult
            .toLong() > 0

    private fun isLanguageConfigured(
        project: Project,
        tag: String,
    ): Boolean {
        if (tag.equals(project.baseLanguageTag, ignoreCase = true)) return true
        return em
            .createQuery(
                """
                SELECT COUNT(l) FROM ProjectLanguage l
                WHERE l.project = :p AND LOWER(l.languageTag) = LOWER(:tag)
                """.trimIndent(),
                java.lang.Long::class.java,
            ).setParameter("p", project)
            .setParameter("tag", tag)
            .singleResult
            .toLong() > 0
    }

    private fun findUser(externalId: String): User? =
        em
            .createQuery("SELECT u FROM User u WHERE u.externalId = :id", User::class.java)
            .setParameter("id", externalId)
            .resultList
            .firstOrNull()

    private fun writeActivity(
        key: Key,
        callerExternalId: String,
        type: ActivityType,
    ) {
        // Native INSERT avoids the JPA `String`→JSONB mismatch on `diff_json`.
        // Phase 7's audit log (T706) will back-fill structured payloads; until
        // then we write NULL explicitly so the column type stays happy.
        val actor = findUser(callerExternalId)
        val externalId =
            io.translately.data.Ulid
                .generate()
        em
            .createNativeQuery(
                """
                INSERT INTO key_activity
                    (external_id, key_id, actor_user_id, action_type, diff_json, created_at, updated_at)
                VALUES
                    (?1, ?2, ?3, ?4, NULL, NOW(), NOW())
                """.trimIndent(),
            ).setParameter(1, externalId)
            .setParameter(2, key.id)
            .setParameter(3, actor?.id)
            .setParameter(4, type.name)
            .executeUpdate()
    }

    companion object {
        internal const val DEFAULT_LIMIT = 50
        internal const val MAX_LIMIT = 200
        internal const val MAX_KEY_NAME = 256
        internal const val MAX_NAME = 128
    }
}

// ----------------------------------------------------------------------
// Stateless helpers (file-level so they don't bloat the class surface)
// ----------------------------------------------------------------------

private fun parseKeyState(raw: String): KeyState =
    runCatching { KeyState.valueOf(raw.trim().uppercase()) }
        .getOrElse { throw invalidState() }

private fun parseTranslationState(raw: String): TranslationState =
    runCatching { TranslationState.valueOf(raw.trim().uppercase()) }
        .getOrElse { throw invalidState() }

private fun invalidState(): OrgException.ValidationFailed =
    OrgException.ValidationFailed(
        listOf(OrgException.ValidationFailed.FieldError("body.state", "INVALID")),
    )

private fun toSummary(entity: Key): KeySummary =
    KeySummary(
        id = entity.externalId,
        keyName = entity.keyName,
        namespaceSlug = entity.namespace.slug,
        description = entity.description,
        stateName = entity.state.name,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )

private fun toTranslationSummary(entity: Translation): TranslationSummary =
    TranslationSummary(
        id = entity.externalId,
        languageTag = entity.languageTag,
        value = entity.value,
        stateName = entity.state.name,
        updatedAt = entity.updatedAt,
    )

private fun toNamespaceSummary(entity: Namespace): NamespaceSummary =
    NamespaceSummary(
        id = entity.externalId,
        slug = entity.slug,
        name = entity.name,
        description = entity.description,
    )

private fun validateKeyName(newName: String) {
    if (newName.isEmpty() || newName.length > KeyService.MAX_KEY_NAME) {
        throw OrgException.ValidationFailed(
            listOf(
                OrgException.ValidationFailed.FieldError(
                    "body.keyName",
                    if (newName.isEmpty()) "REQUIRED" else "TOO_LONG",
                ),
            ),
        )
    }
}

private fun validateCreate(
    name: String,
    nsSlug: String,
) {
    val errors = mutableListOf<OrgException.ValidationFailed.FieldError>()
    if (name.isEmpty() || name.length > KeyService.MAX_KEY_NAME) {
        errors +=
            OrgException.ValidationFailed.FieldError(
                "body.keyName",
                if (name.isEmpty()) "REQUIRED" else "TOO_LONG",
            )
    }
    if (nsSlug.isEmpty()) {
        errors += OrgException.ValidationFailed.FieldError("body.namespaceSlug", "REQUIRED")
    }
    if (errors.isNotEmpty()) throw OrgException.ValidationFailed(errors)
}

/** Returns the normalised slug or throws `ValidationFailed`. */
private fun validateNamespace(
    name: String,
    rawSlug: String?,
): String {
    val slug = SlugNormalizer.canonicalise(rawSlug, name)
    val errors = mutableListOf<OrgException.ValidationFailed.FieldError>()
    if (name.isEmpty() || name.length > KeyService.MAX_NAME) {
        errors +=
            OrgException.ValidationFailed.FieldError(
                "body.name",
                if (name.isEmpty()) "REQUIRED" else "TOO_LONG",
            )
    }
    if (slug == null) {
        errors += OrgException.ValidationFailed.FieldError("body.slug", "INVALID")
    }
    if (errors.isNotEmpty()) throw OrgException.ValidationFailed(errors)
    return slug!!
}

// ----------------------------------------------------------------------
// DTOs
// ----------------------------------------------------------------------

data class ListKeysFilter(
    val namespaceSlug: String? = null,
    val limit: Int = KeyService.DEFAULT_LIMIT,
    val offset: Int = 0,
)

data class TranslationTarget(
    val orgSlugOrId: String,
    val projectSlugOrId: String,
    val keyIdOrName: String,
    val languageTag: String,
)

data class CreateKeyRequest(
    val keyName: String,
    val namespaceSlug: String?,
    val description: String? = null,
)

data class UpdateKeyRequest(
    val keyName: String? = null,
    val namespaceSlug: String? = null,
    val description: String? = null,
    /** Optional lifecycle state name — `NEW`, `TRANSLATING`, `REVIEW`, `DONE`. */
    val stateName: String? = null,
)

data class UpdateTranslationRequest(
    val value: String,
    /** Optional per-cell state name — `EMPTY`, `DRAFT`, `TRANSLATED`, `REVIEW`, `APPROVED`. */
    val stateName: String? = null,
)

data class CreateNamespaceRequest(
    val name: String,
    val slug: String? = null,
    val description: String? = null,
)

data class KeySummary(
    val id: String,
    val keyName: String,
    val namespaceSlug: String,
    val description: String?,
    /** Lifecycle state name — see `KeyState`. */
    val stateName: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class KeyDetails(
    val key: KeySummary,
    val translations: List<TranslationSummary>,
)

data class TranslationSummary(
    val id: String,
    val languageTag: String,
    val value: String,
    /** Per-cell state name — see `TranslationState`. */
    val stateName: String,
    val updatedAt: Instant,
)

data class NamespaceSummary(
    val id: String,
    val slug: String,
    val name: String,
    val description: String?,
)
