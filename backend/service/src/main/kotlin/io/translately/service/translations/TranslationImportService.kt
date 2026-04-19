package io.translately.service.translations

import io.translately.data.entity.Activity
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
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.jboss.logging.Logger
import java.util.Locale

/**
 * i18next-compatible JSON import into a project (T301).
 *
 * Accepts either flat or nested JSON (auto-detected by [JsonTranslationsIO])
 * and upserts translations for a single language tag in one transaction,
 * respecting the per-call conflict mode:
 *
 *  - **KEEP** — existing translations win; imported values are discarded
 *    when the cell already exists. Missing cells are created.
 *  - **OVERWRITE** — every incoming value replaces the existing one.
 *  - **MERGE** — existing values win only when they are non-blank;
 *    blank cells get the imported value.
 *
 * Keys that don't exist yet in the project are created on the fly under
 * the specified namespace (auto-generated if absent). ICU validation
 * runs per-cell via [IcuValidator]; rows that fail validation land in
 * [ImportResult.errors] with their key path and aggregated counts roll
 * up for the UI.
 *
 * The full import runs inside a single transaction — partial failures
 * roll back the whole call, matching the "preview then commit" UX we
 * want for the import wizard (T304). Async Quartz + SSE shipping is the
 * Phase 4 T303 follow-up, so this MVP call is synchronous.
 */
@ApplicationScoped
open class TranslationImportService {
    private val log = Logger.getLogger(TranslationImportService::class.java)

    @Inject
    lateinit var em: EntityManager

    @Inject
    lateinit var orgService: OrgService

    @Inject
    lateinit var jsonIo: JsonTranslationsIO

    @Inject
    lateinit var icu: IcuValidator

    /** Parse + validate + conflict-resolve + persist — all in one transaction. */
    @Transactional
    open fun importJson(
        callerExternalId: String,
        target: ImportTarget,
        request: ImportJsonRequest,
    ): ImportResult {
        val project = resolveProject(callerExternalId, target)
        val langTag = validateLanguageTag(project, request.languageTag)
        val namespace = ensureNamespace(project, request.namespaceSlug, callerExternalId)
        val parsed = parse(request.body)
        val caller = findUserOrNull(callerExternalId)
        val ctx =
            EntryContext(
                project = project,
                namespace = namespace,
                languageTag = langTag,
                mode = request.mode,
                caller = caller,
            )
        val counts = processAllEntries(parsed, ctx)
        em.flush()
        log.infov(
            "imported {0} entries into project {1} lang={2} (created={3} updated={4} skipped={5} errors={6})",
            parsed.size,
            project.externalId,
            langTag,
            counts.created,
            counts.updated,
            counts.skipped,
            counts.errors.size,
        )
        return ImportResult(
            total = parsed.size,
            created = counts.created,
            updated = counts.updated,
            skipped = counts.skipped,
            failed = counts.errors.size,
            errors = counts.errors,
        )
    }

    private data class Counts(
        var created: Int = 0,
        var updated: Int = 0,
        var skipped: Int = 0,
        val errors: MutableList<ImportError> = mutableListOf(),
    )

    private fun processAllEntries(
        entries: List<JsonTranslationsIO.Entry>,
        ctx: EntryContext,
    ): Counts {
        val counts = Counts()
        for (entry in entries) {
            val outcome =
                try {
                    processEntry(entry, ctx)
                } catch (ex: OrgException.ValidationFailed) {
                    counts.errors += ImportError(entry.keyName, "VALIDATION_FAILED", ex.message.orEmpty())
                    continue
                }
            when (outcome.result) {
                Resolution.CREATED -> counts.created += 1
                Resolution.UPDATED -> counts.updated += 1
                Resolution.SKIPPED -> counts.skipped += 1
                Resolution.INVALID ->
                    counts.errors +=
                        ImportError(
                            keyName = entry.keyName,
                            code = "INVALID_ICU_TEMPLATE",
                            message = outcome.message.orEmpty(),
                        )
            }
        }
        return counts
    }

    // ------------------------------------------------------------------
    // entry processing
    // ------------------------------------------------------------------

    private fun processEntry(
        entry: JsonTranslationsIO.Entry,
        ctx: EntryContext,
    ): EntryOutcome {
        // Validate ICU first — reject invalid values outright regardless
        // of conflict mode, so the UI can fix and retry without the DB
        // seeing a half-applied import.
        val valid = icu.validate(entry.value, Locale.forLanguageTag(ctx.languageTag))
        if (!valid.ok) {
            return EntryOutcome(
                entry = entry,
                result = Resolution.INVALID,
                message = valid.errors.first().message,
            )
        }

        val existingKey = findKey(ctx.project, entry.keyName)
        val key = existingKey ?: createKey(ctx.project, ctx.namespace, entry.keyName, ctx.caller)
        val existingTranslation = existingKey?.let { findTranslation(it, ctx.languageTag) }

        val action =
            when (ctx.mode) {
                ConflictMode.KEEP -> if (existingTranslation == null) Action.WRITE else Action.SKIP
                ConflictMode.OVERWRITE -> Action.WRITE
                ConflictMode.MERGE -> {
                    if (existingTranslation == null || existingTranslation.value.isBlank()) {
                        Action.WRITE
                    } else {
                        Action.SKIP
                    }
                }
            }

        return when (action) {
            Action.SKIP -> EntryOutcome(entry = entry, result = Resolution.SKIPPED)
            Action.WRITE -> {
                writeTranslation(key, ctx.languageTag, entry.value, ctx.caller)
                val result = if (existingTranslation == null) Resolution.CREATED else Resolution.UPDATED
                EntryOutcome(entry = entry, result = result)
            }
        }
    }

    // ------------------------------------------------------------------
    // persistence helpers
    // ------------------------------------------------------------------

    private fun createKey(
        project: Project,
        namespace: Namespace,
        keyName: String,
        caller: User?,
    ): Key {
        val k =
            Key().apply {
                this.project = project
                this.namespace = namespace
                this.keyName = keyName
                this.state = KeyState.NEW
            }
        em.persist(k)
        writeActivity(k, caller, ActivityType.CREATED)
        return k
    }

    private fun writeTranslation(
        key: Key,
        languageTag: String,
        value: String,
        caller: User?,
    ): Translation {
        val existing = findTranslation(key, languageTag)
        val derivedState = if (value.isBlank()) TranslationState.EMPTY else TranslationState.DRAFT
        val t =
            if (existing != null) {
                existing.value = value
                existing.state = derivedState
                existing.author = caller
                em.merge(existing)
            } else {
                val fresh =
                    Translation().apply {
                        this.key = key
                        this.languageTag = languageTag
                        this.value = value
                        this.state = derivedState
                        this.author = caller
                    }
                em.persist(fresh)
                fresh
            }
        // Nudge the key out of NEW on the first non-blank translation.
        if (key.state == KeyState.NEW && value.isNotBlank()) {
            key.state = KeyState.TRANSLATING
            em.merge(key)
            writeActivity(key, caller, ActivityType.STATE_CHANGED)
        }
        writeActivity(key, caller, ActivityType.TRANSLATED)
        return t
    }

    private fun writeActivity(
        key: Key,
        caller: User?,
        type: ActivityType,
    ) {
        val activity =
            Activity().apply {
                this.key = key
                this.actor = caller
                this.actionType = type
            }
        em.persist(activity)
    }

    private fun findKey(
        project: Project,
        keyName: String,
    ): Key? =
        em
            .createQuery(
                "SELECT k FROM Key k WHERE k.project = :p AND k.keyName = :name AND k.softDeletedAt IS NULL",
                Key::class.java,
            ).setParameter("p", project)
            .setParameter("name", keyName)
            .resultList
            .firstOrNull()

    private fun findTranslation(
        key: Key,
        languageTag: String,
    ): Translation? =
        em
            .createQuery(
                "SELECT t FROM Translation t WHERE t.key = :k AND t.languageTag = :tag",
                Translation::class.java,
            ).setParameter("k", key)
            .setParameter("tag", languageTag)
            .resultList
            .firstOrNull()

    private fun findUserOrNull(externalId: String): User? =
        em
            .createQuery("SELECT u FROM User u WHERE u.externalId = :id", User::class.java)
            .setParameter("id", externalId)
            .resultList
            .firstOrNull()

    // ------------------------------------------------------------------
    // resolvers
    // ------------------------------------------------------------------

    private fun resolveProject(
        callerExternalId: String,
        target: ImportTarget,
    ): Project {
        orgService.requireMembership(callerExternalId, target.orgSlugOrId)
        val orgKey = target.orgSlugOrId.trim().lowercase()
        val projKey = target.projectSlugOrId.trim().lowercase()
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

    private fun ensureNamespace(
        project: Project,
        rawSlug: String?,
        callerExternalId: String,
    ): Namespace {
        val slug = (rawSlug?.trim()?.lowercase()?.takeIf(String::isNotEmpty)) ?: "default"
        val existing =
            em
                .createQuery(
                    "SELECT n FROM Namespace n WHERE n.project = :p AND n.slug = :slug",
                    Namespace::class.java,
                ).setParameter("p", project)
                .setParameter("slug", slug)
                .resultList
                .firstOrNull()
        if (existing != null) return existing

        val ns =
            Namespace().apply {
                this.project = project
                this.slug = slug
                this.name = slug.replaceFirstChar { it.titlecase() }
            }
        em.persist(ns)
        log.infov(
            "auto-created namespace {0} for import in project {1} (caller={2})",
            slug,
            project.externalId,
            callerExternalId,
        )
        return ns
    }

    private fun validateLanguageTag(
        project: Project,
        raw: String,
    ): String {
        val tag = raw.trim()
        if (tag.isEmpty()) {
            throw OrgException.ValidationFailed(
                listOf(OrgException.ValidationFailed.FieldError("languageTag", "REQUIRED")),
            )
        }
        // Fall back to the project's base language when the caller asks
        // for a tag that isn't configured — the importer should not be
        // a backdoor for registering languages.
        val configured =
            em
                .createQuery(
                    "SELECT pl.languageTag FROM ProjectLanguage pl WHERE pl.project = :p",
                    String::class.java,
                ).setParameter("p", project)
                .resultList
        if (configured.isNotEmpty() && tag !in configured) {
            throw OrgException.NotFound("Language")
        }
        return tag
    }

    @Suppress("SwallowedException") // Cause preserved via log.debugv(ex, ...) before the rethrow.
    private fun parse(body: String): List<JsonTranslationsIO.Entry> =
        try {
            jsonIo.read(body)
        } catch (ex: JsonShapeException) {
            log.debugv(ex, "json import shape rejected: path={0} code={1}", ex.error.path, ex.error.code)
            throw OrgException.ValidationFailed(
                listOf(
                    OrgException.ValidationFailed.FieldError(
                        path = ex.error.path,
                        code = ex.error.code,
                    ),
                ),
            )
        }

    // ------------------------------------------------------------------
    // DTOs
    // ------------------------------------------------------------------

    /**
     * Per-call context threaded through [processEntry] — keeps the
     * per-entry method to two params (the entry + the context) so
     * detekt's `LongParameterList` stays happy while the import-level
     * invariants remain colocated.
     */
    private data class EntryContext(
        val project: Project,
        val namespace: Namespace,
        val languageTag: String,
        val mode: ConflictMode,
        val caller: User?,
    )

    enum class ConflictMode { KEEP, OVERWRITE, MERGE }

    private enum class Action { WRITE, SKIP }

    enum class Resolution { CREATED, UPDATED, SKIPPED, INVALID }

    private data class EntryOutcome(
        val entry: JsonTranslationsIO.Entry,
        val result: Resolution,
        val message: String? = null,
    )

    data class ImportTarget(
        val orgSlugOrId: String,
        val projectSlugOrId: String,
    )

    data class ImportJsonRequest(
        val languageTag: String,
        val namespaceSlug: String?,
        val mode: ConflictMode,
        val body: String,
    )

    data class ImportError(
        val keyName: String,
        val code: String,
        val message: String,
    )

    data class ImportResult(
        val total: Int,
        val created: Int,
        val updated: Int,
        val skipped: Int,
        val failed: Int,
        val errors: List<ImportError>,
    )
}
