package io.translately.service.orgs

import io.translately.data.entity.Organization
import io.translately.data.entity.OrganizationMember
import io.translately.data.entity.OrganizationRole
import io.translately.data.entity.User
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.jboss.logging.Logger
import java.time.Instant

/**
 * Use-case entry point for organization management.
 *
 * Every read path accepts a `callerExternalId` and asserts membership;
 * non-members see `NotFound` so the server never discloses whether an org
 * exists. The org listing endpoint returns only orgs the caller belongs
 * to.
 */
@ApplicationScoped
open class OrgService(
    private val em: EntityManager,
) {
    private val log = Logger.getLogger(OrgService::class.java)

    /** Orgs the caller belongs to (sorted alphabetically by name). */
    @Transactional
    open fun listForCaller(callerExternalId: String): List<OrgSummary> {
        val rows =
            em
                .createQuery(
                    """
                    SELECT m FROM OrganizationMember m
                    WHERE m.user.externalId = :uid
                    ORDER BY m.organization.name
                    """.trimIndent(),
                    OrganizationMember::class.java,
                ).setParameter("uid", callerExternalId)
                .resultList
        return rows.map { toSummary(it.organization, it.role) }
    }

    /**
     * Create a new organization and add [callerExternalId] as its OWNER
     * member. Org creation is self-serve — no platform-level gate — and
     * the caller is always the first OWNER.
     */
    @Transactional
    open fun create(
        callerExternalId: String,
        body: CreateOrgRequest,
    ): OrgSummary {
        val caller =
            findUserByExternalId(callerExternalId)
                ?: throw OrgException.NotFound("User")
        val finalSlug = validateAndNormaliseCreate(body)
        if (slugExists(finalSlug)) throw OrgException.SlugTaken(finalSlug, "org")

        val org =
            Organization().apply {
                this.slug = finalSlug
                this.name = body.name.trim()
            }
        em.persist(org)

        val member =
            OrganizationMember().apply {
                this.organization = org
                this.user = caller
                this.role = OrganizationRole.OWNER
                this.joinedAt = Instant.now()
            }
        em.persist(member)
        em.flush()

        log.infov("created org {0} ({1}) with owner {2}", org.externalId, finalSlug, callerExternalId)
        return toSummary(org, OrganizationRole.OWNER)
    }

    /** Get a single org the caller is a member of. */
    @Transactional
    open fun getForCaller(
        callerExternalId: String,
        orgSlugOrId: String,
    ): OrgSummary {
        val membership = requireMembership(callerExternalId, orgSlugOrId)
        return toSummary(membership.organization, membership.role)
    }

    /** Rename an org. Requires ORG_WRITE scope (checked at the resource). */
    @Transactional
    open fun updateName(
        callerExternalId: String,
        orgSlugOrId: String,
        body: UpdateOrgRequest,
    ): OrgSummary {
        val membership = requireMembership(callerExternalId, orgSlugOrId)
        val org = membership.organization
        val newName = body.name.trim()
        if (newName.isEmpty() || newName.length > 128) {
            throw OrgException.ValidationFailed(
                listOf(
                    OrgException.ValidationFailed.FieldError(
                        "body.name",
                        if (newName.isEmpty()) "REQUIRED" else "TOO_LONG",
                    ),
                ),
            )
        }
        org.name = newName
        em.merge(org)
        log.infov("renamed org {0} to '{1}'", org.externalId, newName)
        return toSummary(org, membership.role)
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    /** Validate the create-request fields and return a canonical slug. */
    private fun validateAndNormaliseCreate(body: CreateOrgRequest): String {
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
        if (errors.isNotEmpty()) throw OrgException.ValidationFailed(errors)
        return slug!!
    }

    internal fun requireMembership(
        callerExternalId: String,
        orgSlugOrId: String,
    ): OrganizationMember {
        val normalized = orgSlugOrId.trim().lowercase()
        val row =
            em
                .createQuery(
                    """
                    SELECT m FROM OrganizationMember m
                    WHERE m.user.externalId = :uid
                      AND (m.organization.slug = :key OR m.organization.externalId = :key)
                    """.trimIndent(),
                    OrganizationMember::class.java,
                ).setParameter("uid", callerExternalId)
                .setParameter("key", normalized)
                .resultList
                .firstOrNull() ?: throw OrgException.NotMember()
        return row
    }

    private fun findUserByExternalId(externalId: String): User? =
        em
            .createQuery("SELECT u FROM User u WHERE u.externalId = :id", User::class.java)
            .setParameter("id", externalId)
            .resultList
            .firstOrNull()

    private fun slugExists(slug: String): Boolean =
        em
            .createQuery("SELECT COUNT(o) FROM Organization o WHERE o.slug = :slug", java.lang.Long::class.java)
            .setParameter("slug", slug)
            .singleResult
            .toLong() > 0

    private fun toSummary(
        org: Organization,
        callerRole: OrganizationRole,
    ): OrgSummary =
        OrgSummary(
            id = org.externalId,
            slug = org.slug,
            name = org.name,
            callerRole = callerRole.name,
            createdAt = org.createdAt,
        )
}

// ----------------------------------------------------------------------
// DTOs
// ----------------------------------------------------------------------

data class CreateOrgRequest(
    val name: String,
    val slug: String? = null,
)

data class UpdateOrgRequest(
    val name: String,
)

data class OrgSummary(
    val id: String,
    val slug: String,
    val name: String,
    /** Role the caller holds in this org — OWNER / ADMIN / MEMBER. */
    val callerRole: String,
    val createdAt: Instant,
)
