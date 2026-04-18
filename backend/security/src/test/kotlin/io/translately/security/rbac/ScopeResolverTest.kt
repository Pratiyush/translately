package io.translately.security.rbac

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import io.translately.security.Scope

class ScopeResolverTest :
    DescribeSpec({

        val resolver = ScopeResolver()

        describe("resolveFromMemberships") {
            it("returns the empty set when given no memberships") {
                resolver.resolveFromMemberships(emptyList()).shouldBeEmpty()
            }

            it("returns every scope for a lone OWNER membership") {
                val result = resolver.resolveFromMemberships(listOf(Membership(1L, OrgRole.OWNER)))
                result shouldBe Scope.entries.toSet()
            }

            it("returns the member scope set for a lone MEMBER membership") {
                val result = resolver.resolveFromMemberships(listOf(Membership(1L, OrgRole.MEMBER)))
                result shouldBe OrgRoleScopes.MEMBER
            }

            it("returns the admin scope set for a lone ADMIN membership") {
                val result = resolver.resolveFromMemberships(listOf(Membership(1L, OrgRole.ADMIN)))
                result shouldBe OrgRoleScopes.ADMIN
            }

            it("unions scopes across two memberships in different orgs") {
                // ADMIN in one org + MEMBER in another → effectively ADMIN
                // (ADMIN is a superset of MEMBER).
                val result =
                    resolver.resolveFromMemberships(
                        listOf(
                            Membership(1L, OrgRole.ADMIN),
                            Membership(2L, OrgRole.MEMBER),
                        ),
                    )
                result shouldBe OrgRoleScopes.ADMIN
            }

            it("a MEMBER plus an OWNER elsewhere yields every scope (OWNER dominates)") {
                val result =
                    resolver.resolveFromMemberships(
                        listOf(
                            Membership(1L, OrgRole.MEMBER),
                            Membership(2L, OrgRole.OWNER),
                        ),
                    )
                result shouldBe Scope.entries.toSet()
            }

            it("collapses duplicate (orgId, role) pairs via Set semantics") {
                val result =
                    resolver.resolveFromMemberships(
                        listOf(
                            Membership(1L, OrgRole.MEMBER),
                            Membership(1L, OrgRole.MEMBER),
                            Membership(1L, OrgRole.MEMBER),
                        ),
                    )
                result shouldBe OrgRoleScopes.MEMBER
            }
        }

        describe("canResolveFor") {
            val memberships =
                listOf(
                    Membership(1L, OrgRole.OWNER),
                    Membership(2L, OrgRole.ADMIN),
                    Membership(3L, OrgRole.MEMBER),
                )

            it("with null orgId returns the cross-org union") {
                // OWNER dominates, so the union is every scope.
                resolver.canResolveFor(userId = 42L, memberships = memberships, orgId = null) shouldBe
                    Scope.entries.toSet()
            }

            it("filters to OWNER scopes when asked about the OWNER org") {
                resolver.canResolveFor(userId = 42L, memberships = memberships, orgId = 1L) shouldBe
                    OrgRoleScopes.OWNER
            }

            it("filters to ADMIN scopes when asked about the ADMIN org") {
                resolver.canResolveFor(userId = 42L, memberships = memberships, orgId = 2L) shouldBe
                    OrgRoleScopes.ADMIN
            }

            it("filters to MEMBER scopes when asked about the MEMBER org") {
                resolver.canResolveFor(userId = 42L, memberships = memberships, orgId = 3L) shouldBe
                    OrgRoleScopes.MEMBER
            }

            it("returns the empty set when the user has no membership in that org") {
                resolver
                    .canResolveFor(userId = 42L, memberships = memberships, orgId = 999L)
                    .shouldBeEmpty()
            }

            it("returns the empty set on null orgId with no memberships at all") {
                resolver
                    .canResolveFor(userId = 42L, memberships = emptyList(), orgId = null)
                    .shouldBeEmpty()
            }

            it("does not branch on userId — same result regardless of principal id") {
                val r1 = resolver.canResolveFor(userId = 1L, memberships = memberships, orgId = 2L)
                val r2 = resolver.canResolveFor(userId = 999L, memberships = memberships, orgId = 2L)
                r1 shouldBe r2
            }
        }

        describe("pure-function property") {
            it("resolveFromMemberships result equals the union of each member's role mapping") {
                checkAll(
                    Arb.list(
                        Arb.bind(Arb.long(0L, 1_000L), Arb.enum<OrgRole>()) { orgId, role ->
                            Membership(orgId, role)
                        },
                        range = 0..8,
                    ),
                ) { memberships ->
                    val expected =
                        memberships
                            .flatMap { OrgRoleScopes.forRole(it.role) }
                            .toSet()
                    resolver.resolveFromMemberships(memberships) shouldBe expected
                }
            }

            it("canResolveFor(orgId=null) equals resolveFromMemberships over the full list") {
                checkAll(
                    Arb.list(
                        Arb.bind(Arb.long(0L, 1_000L), Arb.enum<OrgRole>()) { orgId, role ->
                            Membership(orgId, role)
                        },
                        range = 0..8,
                    ),
                ) { memberships ->
                    resolver.canResolveFor(userId = 7L, memberships = memberships, orgId = null) shouldBe
                        resolver.resolveFromMemberships(memberships)
                }
            }
        }
    })
