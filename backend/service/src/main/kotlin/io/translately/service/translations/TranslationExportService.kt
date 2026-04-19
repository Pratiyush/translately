package io.translately.service.translations

import io.translately.data.entity.Key
import io.translately.data.entity.Project
import io.translately.data.entity.TranslationState
import io.translately.service.orgs.OrgException
import io.translately.service.orgs.OrgService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.jboss.logging.Logger

/**
 * i18next-compatible JSON export of a project's translations for one
 * language (T302).
 *
 * The caller picks the serialisation shape (`FLAT` or `NESTED`), an
 * optional namespace slug to scope the dump, an optional tag
 * intersection (keys that carry every listed tag), and an optional
 * minimum translation state (e.g. only `APPROVED` rows get exported
 * into a release channel).
 *
 * Returns the JSON string directly; the REST layer wraps it in a
 * response body with `Content-Type: application/json` and a suggested
 * filename via `Content-Disposition`.
 *
 * MVP-scope: synchronous, in-memory dump. Async Quartz + SSE streaming
 * for very large projects is the Phase 4 T303 follow-up.
 */
@ApplicationScoped
open class TranslationExportService {
    private val log = Logger.getLogger(TranslationExportService::class.java)

    @Inject
    lateinit var em: EntityManager

    @Inject
    lateinit var orgService: OrgService

    @Inject
    lateinit var jsonIo: JsonTranslationsIO

    @Transactional
    open fun exportJson(
        callerExternalId: String,
        target: ExportTarget,
        request: ExportJsonRequest,
    ): ExportResult {
        val project = resolveProject(callerExternalId, target)
        val entries = collectEntries(project, request)
        val body = jsonIo.write(entries, request.shape)
        log.infov(
            "exported {0} translations from project {1} lang={2} shape={3}",
            entries.size,
            project.externalId,
            request.languageTag,
            request.shape,
        )
        return ExportResult(
            languageTag = request.languageTag,
            shape = request.shape,
            keyCount = entries.size,
            body = body,
        )
    }

    // ------------------------------------------------------------------
    // entry collection
    // ------------------------------------------------------------------

    private fun collectEntries(
        project: Project,
        request: ExportJsonRequest,
    ): List<JsonTranslationsIO.Entry> {
        val tag = request.languageTag.trim()
        if (tag.isEmpty()) {
            throw OrgException.ValidationFailed(
                listOf(OrgException.ValidationFailed.FieldError(path = "languageTag", code = "REQUIRED")),
            )
        }
        val nsFilter =
            request.namespaceSlug
                ?.trim()
                ?.lowercase()
                ?.takeIf(String::isNotEmpty)
        val tagFilter = request.tags.mapNotNull { it.trim().lowercase().takeIf(String::isNotEmpty) }
        val minState =
            request.minStateName?.trim()?.uppercase()?.takeIf(String::isNotEmpty)?.let {
                try {
                    TranslationState.valueOf(it)
                } catch (_: IllegalArgumentException) {
                    throw OrgException.ValidationFailed(
                        listOf(OrgException.ValidationFailed.FieldError(path = "minState", code = "INVALID")),
                    )
                }
            }

        val jpql =
            buildString {
                append(
                    """
                    SELECT k, t FROM Key k
                    LEFT JOIN Translation t
                      ON t.key = k AND t.languageTag = :tag
                    WHERE k.project = :project
                      AND k.softDeletedAt IS NULL
                    """.trimIndent(),
                )
                if (nsFilter != null) append(" AND k.namespace.slug = :ns")
                if (minState != null) append(" AND t.state IS NOT NULL AND t.state IN :states")
                append(" ORDER BY k.keyName")
            }
        val q =
            em
                .createQuery(jpql, Array<Any?>::class.java)
                .setParameter("project", project)
                .setParameter("tag", tag)
        if (nsFilter != null) q.setParameter("ns", nsFilter)
        if (minState != null) q.setParameter("states", statesAtOrAbove(minState))

        val rows = q.resultList

        val filtered =
            if (tagFilter.isEmpty()) {
                rows
            } else {
                rows.filter { row ->
                    val key = row[0] as Key
                    val slugs = key.tags.map { it.slug }
                    tagFilter.all { it in slugs }
                }
            }

        return filtered.map { row ->
            val key = row[0] as Key
            val value = (row[1] as? io.translately.data.entity.Translation)?.value.orEmpty()
            JsonTranslationsIO.Entry(keyName = key.keyName, value = value)
        }
    }

    /**
     * Translation lifecycle is totally ordered: EMPTY < DRAFT <
     * TRANSLATED < REVIEW < APPROVED. "At or above" means every value
     * from [min] through APPROVED.
     */
    private fun statesAtOrAbove(min: TranslationState): List<TranslationState> {
        val all = TranslationState.entries
        val idx = all.indexOf(min)
        return if (idx < 0) all else all.drop(idx)
    }

    // ------------------------------------------------------------------
    // resolvers
    // ------------------------------------------------------------------

    private fun resolveProject(
        callerExternalId: String,
        target: ExportTarget,
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

    // ------------------------------------------------------------------
    // DTOs
    // ------------------------------------------------------------------

    data class ExportTarget(
        val orgSlugOrId: String,
        val projectSlugOrId: String,
    )

    data class ExportJsonRequest(
        val languageTag: String,
        val namespaceSlug: String?,
        val tags: List<String>,
        /**
         * Opaque state name — service parses to [TranslationState]. The
         * API layer doesn't depend on `:backend:data` so we accept a
         * string at the boundary and validate here.
         */
        val minStateName: String?,
        val shape: JsonTranslationsIO.Shape,
    )

    data class ExportResult(
        val languageTag: String,
        val shape: JsonTranslationsIO.Shape,
        val keyCount: Int,
        val body: String,
    )
}
