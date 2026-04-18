package io.translately.service.orgs

import io.translately.data.entity.OrganizationMember
import io.translately.data.entity.OrganizationRole
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.jboss.logging.Logger
import java.time.Instant

/**
 * Organization-member management (list / update-role / remove).
 *
 * Invite-by-email + pending acceptance is explicitly deferred to Phase 7
 * (SSO / SAML / LDAP). For v0.1.0 membership grows through self-serve
 * org creation; the admin surface here lets an OWNER / ADMIN observe the
 * roster, promote/demote, or remove a collaborator.
 */
@ApplicationScoped
open class OrgMemberService(
    private val em: EntityManager,
    private val orgService: OrgService,
) {
    private val log = Logger.getLogger(OrgMemberService::class.java)

    /** List every member of the org. Caller must hold any role in the org (MEMBERS_READ scope at the resource). */
    @Transactional
    open fun list(
        callerExternalId: String,
        orgSlugOrId: String,
    ): List<MemberSummary> {
        val callerMembership = orgService.requireMembership(callerExternalId, orgSlugOrId)
        val rows =
            em
                .createQuery(
                    """
                    SELECT m FROM OrganizationMember m
                    WHERE m.organization.id = :oid
                    ORDER BY m.role, m.user.email
                    """.trimIndent(),
                    OrganizationMember::class.java,
                ).setParameter("oid", callerMembership.organization.id)
                .resultList
        return rows.map(::toSummary)
    }

    /** Change a member's role. OWNER/ADMIN only (MEMBERS_WRITE at the resource). */
    @Transactional
    open fun updateRole(
        callerExternalId: String,
        orgSlugOrId: String,
        memberUserExternalId: String,
        newRoleRaw: String,
    ): MemberSummary {
        val newRole = parseRole(newRoleRaw)
        val callerMembership = orgService.requireMembership(callerExternalId, orgSlugOrId)
        val target =
            em
                .createQuery(
                    """
                    SELECT m FROM OrganizationMember m
                    WHERE m.organization.id = :oid AND m.user.externalId = :uid
                    """.trimIndent(),
                    OrganizationMember::class.java,
                ).setParameter("oid", callerMembership.organization.id)
                .setParameter("uid", memberUserExternalId)
                .resultList
                .firstOrNull() ?: throw OrgException.NotFound("Member")

        if (target.role == OrganizationRole.OWNER && newRole != OrganizationRole.OWNER) {
            requireAnotherOwnerExists(callerMembership.organization.id!!, target.id!!)
        }
        target.role = newRole
        em.merge(target)
        log.infov(
            "changed role for member {0} in org {1} to {2}",
            memberUserExternalId,
            callerMembership.organization.externalId,
            newRole,
        )
        return toSummary(target)
    }

    /** Remove a member. OWNER/ADMIN only (MEMBERS_WRITE at the resource). */
    @Transactional
    open fun remove(
        callerExternalId: String,
        orgSlugOrId: String,
        memberUserExternalId: String,
    ) {
        val callerMembership = orgService.requireMembership(callerExternalId, orgSlugOrId)
        val target =
            em
                .createQuery(
                    """
                    SELECT m FROM OrganizationMember m
                    WHERE m.organization.id = :oid AND m.user.externalId = :uid
                    """.trimIndent(),
                    OrganizationMember::class.java,
                ).setParameter("oid", callerMembership.organization.id)
                .setParameter("uid", memberUserExternalId)
                .resultList
                .firstOrNull() ?: throw OrgException.NotFound("Member")
        if (target.role == OrganizationRole.OWNER) {
            requireAnotherOwnerExists(callerMembership.organization.id!!, target.id!!)
        }
        em.remove(target)
        log.infov(
            "removed member {0} from org {1}",
            memberUserExternalId,
            callerMembership.organization.externalId,
        )
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private fun parseRole(raw: String): OrganizationRole {
        val normalized = raw.trim().uppercase()
        return OrganizationRole.entries.firstOrNull { it.name == normalized }
            ?: throw OrgException.ValidationFailed(
                listOf(OrgException.ValidationFailed.FieldError("body.role", "INVALID")),
            )
    }

    private fun requireAnotherOwnerExists(
        orgId: Long,
        excludingMemberId: Long,
    ) {
        val otherOwners =
            em
                .createQuery(
                    """
                    SELECT COUNT(m) FROM OrganizationMember m
                    WHERE m.organization.id = :oid AND m.role = :role AND m.id <> :mid
                    """.trimIndent(),
                    java.lang.Long::class.java,
                ).setParameter("oid", orgId)
                .setParameter("role", OrganizationRole.OWNER)
                .setParameter("mid", excludingMemberId)
                .singleResult
                .toLong()
        if (otherOwners == 0L) throw OrgException.LastOwner()
    }

    private fun toSummary(entity: OrganizationMember): MemberSummary =
        MemberSummary(
            userId = entity.user.externalId,
            email = entity.user.email,
            fullName = entity.user.fullName,
            role = entity.role.name,
            invitedAt = entity.invitedAt,
            joinedAt = entity.joinedAt,
        )
}

// ----------------------------------------------------------------------
// DTOs
// ----------------------------------------------------------------------

data class UpdateMemberRoleRequest(
    val role: String,
)

data class MemberSummary(
    /** User external ID (ULID). */
    val userId: String,
    val email: String,
    val fullName: String,
    val role: String,
    val invitedAt: Instant,
    /** null for pending invitations (deferred to Phase 7 — always set today). */
    val joinedAt: Instant?,
)
