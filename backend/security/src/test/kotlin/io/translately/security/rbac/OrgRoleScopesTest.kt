package io.translately.security.rbac

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.translately.security.Scope

class OrgRoleScopesTest :
    DescribeSpec({

        describe("OWNER") {
            it("grants every Scope in the enum") {
                OrgRoleScopes.OWNER shouldBe Scope.entries.toSet()
            }

            it("matches forRole(OWNER)") {
                OrgRoleScopes.forRole(OrgRole.OWNER) shouldBe OrgRoleScopes.OWNER
            }

            it("includes the three admin-excluded scopes") {
                OrgRoleScopes.OWNER shouldContainAll OrgRoleScopes.ADMIN_EXCLUSIONS
            }
        }

        describe("ADMIN") {
            it("contains every Scope except the admin-excluded levers") {
                OrgRoleScopes.ADMIN shouldBe (Scope.entries.toSet() - OrgRoleScopes.ADMIN_EXCLUSIONS)
            }

            it("excludes PROJECT_SETTINGS_WRITE, AI_CONFIG_WRITE, API_KEYS_WRITE") {
                OrgRoleScopes.ADMIN shouldNotContain Scope.PROJECT_SETTINGS_WRITE
                OrgRoleScopes.ADMIN shouldNotContain Scope.AI_CONFIG_WRITE
                OrgRoleScopes.ADMIN shouldNotContain Scope.API_KEYS_WRITE
            }

            it("retains AUDIT_READ so admins can investigate without hiding tracks") {
                OrgRoleScopes.ADMIN shouldContain Scope.AUDIT_READ
            }

            it("retains API_KEYS_READ (list) even though WRITE is withheld") {
                OrgRoleScopes.ADMIN shouldContain Scope.API_KEYS_READ
            }

            it("matches forRole(ADMIN)") {
                OrgRoleScopes.forRole(OrgRole.ADMIN) shouldBe OrgRoleScopes.ADMIN
            }
        }

        describe("MEMBER") {
            it("contains every .read Scope") {
                OrgRoleScopes.MEMBER shouldContainAll OrgRoleScopes.READ_SCOPES
            }

            it("contains the four MEMBER_WRITES: keys, translations, imports, ai.suggest") {
                OrgRoleScopes.MEMBER shouldContainAll OrgRoleScopes.MEMBER_WRITES
                OrgRoleScopes.MEMBER_WRITES shouldBe
                    setOf(
                        Scope.KEYS_WRITE,
                        Scope.TRANSLATIONS_WRITE,
                        Scope.IMPORTS_WRITE,
                        Scope.AI_SUGGEST,
                    )
            }

            it("does not grant admin-only scopes") {
                OrgRoleScopes.MEMBER shouldNotContain Scope.PROJECT_SETTINGS_WRITE
                OrgRoleScopes.MEMBER shouldNotContain Scope.AI_CONFIG_WRITE
                OrgRoleScopes.MEMBER shouldNotContain Scope.API_KEYS_WRITE
                OrgRoleScopes.MEMBER shouldNotContain Scope.MEMBERS_WRITE
                OrgRoleScopes.MEMBER shouldNotContain Scope.ORG_WRITE
                OrgRoleScopes.MEMBER shouldNotContain Scope.PROJECTS_WRITE
                OrgRoleScopes.MEMBER shouldNotContain Scope.WEBHOOKS_WRITE
            }

            it("matches forRole(MEMBER)") {
                OrgRoleScopes.forRole(OrgRole.MEMBER) shouldBe OrgRoleScopes.MEMBER
            }
        }

        describe("role hierarchy") {
            it("OWNER is a strict superset of ADMIN") {
                OrgRoleScopes.OWNER shouldContainAll OrgRoleScopes.ADMIN
                (OrgRoleScopes.OWNER.size > OrgRoleScopes.ADMIN.size) shouldBe true
            }

            it("ADMIN is a strict superset of MEMBER") {
                OrgRoleScopes.ADMIN shouldContainAll OrgRoleScopes.MEMBER
                (OrgRoleScopes.ADMIN.size > OrgRoleScopes.MEMBER.size) shouldBe true
            }

            it("OWNER is a strict superset of MEMBER (transitivity)") {
                OrgRoleScopes.OWNER shouldContainAll OrgRoleScopes.MEMBER
                (OrgRoleScopes.OWNER.size > OrgRoleScopes.MEMBER.size) shouldBe true
            }
        }

        describe("exhaustiveness") {
            it("every Scope is reachable by at least one role (no orphans)") {
                val unionOfAllRoles =
                    OrgRoleScopes.OWNER + OrgRoleScopes.ADMIN + OrgRoleScopes.MEMBER
                unionOfAllRoles shouldBe Scope.entries.toSet()
            }

            it("every OrgRole maps to a non-empty scope set") {
                OrgRole.entries.forEach { role ->
                    (OrgRoleScopes.forRole(role).isNotEmpty()) shouldBe true
                }
            }

            it("READ_SCOPES is exactly the set of .read-suffixed tokens") {
                OrgRoleScopes.READ_SCOPES shouldBe
                    Scope.entries.filter { it.token.endsWith(".read") }.toSet()
            }

            it("ADMIN_EXCLUSIONS contains exactly the three documented levers") {
                OrgRoleScopes.ADMIN_EXCLUSIONS shouldBe
                    setOf(
                        Scope.PROJECT_SETTINGS_WRITE,
                        Scope.AI_CONFIG_WRITE,
                        Scope.API_KEYS_WRITE,
                    )
            }
        }
    })
