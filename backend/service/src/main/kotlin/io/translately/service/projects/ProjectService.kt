package io.translately.service.projects

import io.translately.data.entity.Project
import io.translately.service.orgs.OrgException
import io.translately.service.orgs.OrgService
import io.translately.service.orgs.SlugNormalizer
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.jboss.logging.Logger
import java.time.Instant

/**
 * Use-case entry point for project management.
 *
 * Every operation resolves the caller's org membership first (via
 * [OrgService.requireMembership]); non-members see `NotFound` so we never
 * disclose that an org exists. Scopes (`PROJECTS_READ`, `PROJECTS_WRITE`,
 * `PROJECT_SETTINGS_WRITE`) are enforced at the JAX-RS resource; the
 * service only enforces the membership rule.
 */
@ApplicationScoped
open class ProjectService(
    private val em: EntityManager,
    private val orgService: OrgService,
) {
    private val log = Logger.getLogger(ProjectService::class.java)

    /** Every project in the org. */
    @Transactional
    open fun list(
        callerExternalId: String,
        orgSlugOrId: String,
    ): List<ProjectSummary> {
        orgService.requireMembership(callerExternalId, orgSlugOrId)
        val orgKey = orgSlugOrId.trim().lowercase()
        val rows =
            em
                .createQuery(
                    """
                    SELECT p FROM Project p
                    WHERE (p.organization.slug = :orgKey OR p.organization.externalId = :orgKey)
                      AND p.deletedAt IS NULL
                    ORDER BY p.name
                    """.trimIndent(),
                    Project::class.java,
                ).setParameter("orgKey", orgKey)
                .resultList
        return rows.map(::toSummary)
    }

    /** Fetch a single project; caller must be an org member. */
    @Transactional
    open fun get(
        callerExternalId: String,
        orgSlugOrId: String,
        projectSlugOrId: String,
    ): ProjectSummary = toSummary(resolve(callerExternalId, orgSlugOrId, projectSlugOrId))

    /** Create a project inside an org. */
    @Transactional
    open fun create(
        callerExternalId: String,
        orgSlugOrId: String,
        body: CreateProjectRequest,
    ): ProjectSummary {
        val membership = orgService.requireMembership(callerExternalId, orgSlugOrId)

        val name = body.name.trim()
        val slug = SlugNormalizer.canonicalise(body.slug, name)
        val errors = mutableListOf<OrgException.ValidationFailed.FieldError>()
        if (name.isEmpty() || name.length > 128) {
            val code = if (name.isEmpty()) "REQUIRED" else "TOO_LONG"
            errors += OrgException.ValidationFailed.FieldError("body.name", code)
        }
        if (slug == null) {
            errors += OrgException.ValidationFailed.FieldError("body.slug", "INVALID")
        }
        val baseLang = (body.baseLanguageTag ?: "en").trim()
        if (baseLang.isEmpty() || baseLang.length > 32) {
            errors += OrgException.ValidationFailed.FieldError("body.baseLanguageTag", "INVALID")
        }
        if (errors.isNotEmpty()) throw OrgException.ValidationFailed(errors)

        val finalSlug = slug!!
        if (projectSlugTaken(orgSlugOrId, finalSlug)) {
            throw OrgException.SlugTaken(finalSlug, "project")
        }

        val entity =
            Project().apply {
                this.organization = membership.organization
                this.slug = finalSlug
                this.name = name
                this.description = body.description?.trim()?.takeIf(String::isNotEmpty)
                this.baseLanguageTag = baseLang
            }
        em.persist(entity)
        em.flush()
        log.infov(
            "created project {0} ({1}) in org {2}",
            entity.externalId,
            finalSlug,
            membership.organization.externalId,
        )
        return toSummary(entity)
    }

    /** Rename / edit the description of a project. */
    @Transactional
    open fun update(
        callerExternalId: String,
        orgSlugOrId: String,
        projectSlugOrId: String,
        body: UpdateProjectRequest,
    ): ProjectSummary {
        val entity = resolve(callerExternalId, orgSlugOrId, projectSlugOrId)

        val name = body.name?.trim()
        if (name != null && (name.isEmpty() || name.length > 128)) {
            throw OrgException.ValidationFailed(
                listOf(
                    OrgException.ValidationFailed.FieldError(
                        "body.name",
                        if (name.isEmpty()) "REQUIRED" else "TOO_LONG",
                    ),
                ),
            )
        }
        if (name != null) entity.name = name
        if (body.description != null) {
            entity.description = body.description.trim().takeIf(String::isNotEmpty)
        }
        em.merge(entity)
        log.infov("updated project {0}", entity.externalId)
        return toSummary(entity)
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private fun resolve(
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

    private fun projectSlugTaken(
        orgSlugOrId: String,
        slug: String,
    ): Boolean =
        em
            .createQuery(
                """
                SELECT COUNT(p) FROM Project p
                WHERE (p.organization.slug = :orgKey OR p.organization.externalId = :orgKey)
                  AND p.slug = :slug
                """.trimIndent(),
                java.lang.Long::class.java,
            ).setParameter("orgKey", orgSlugOrId.trim().lowercase())
            .setParameter("slug", slug)
            .singleResult
            .toLong() > 0

    private fun toSummary(entity: Project): ProjectSummary =
        ProjectSummary(
            id = entity.externalId,
            slug = entity.slug,
            name = entity.name,
            description = entity.description,
            baseLanguageTag = entity.baseLanguageTag,
            createdAt = entity.createdAt,
        )
}

// ----------------------------------------------------------------------
// DTOs
// ----------------------------------------------------------------------

data class CreateProjectRequest(
    val name: String,
    val slug: String? = null,
    val description: String? = null,
    val baseLanguageTag: String? = null,
)

data class UpdateProjectRequest(
    val name: String? = null,
    val description: String? = null,
)

data class ProjectSummary(
    val id: String,
    val slug: String,
    val name: String,
    val description: String?,
    val baseLanguageTag: String,
    val createdAt: Instant,
)
