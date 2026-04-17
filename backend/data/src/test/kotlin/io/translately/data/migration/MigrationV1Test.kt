package io.translately.data.migration

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.DriverManager

/**
 * Apply V1__auth_and_orgs.sql against a real Postgres 16 (Testcontainers)
 * and assert the schema matches expectation. This doubles as a
 * forward-compatibility guard: whoever edits this migration in-place breaks
 * the build, forcing them to add V2+ instead (the correct pattern).
 *
 * Pulls `postgres:16-alpine` on first run; subsequent runs reuse the cached
 * image so the suite runs in ~5s after warm-up.
 */
class MigrationV1Test :
    DescribeSpec({

        val postgres =
            PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("translately_test")
                .withUsername("translately")
                .withPassword("translately")
        lateinit var jdbcUrl: String

        beforeSpec {
            postgres.start()
            jdbcUrl = postgres.jdbcUrl
            Flyway
                .configure()
                .dataSource(jdbcUrl, postgres.username, postgres.password)
                .locations("classpath:db/migration")
                .load()
                .migrate()
        }

        afterSpec {
            postgres.stop()
        }

        fun connect(): Connection = DriverManager.getConnection(jdbcUrl, postgres.username, postgres.password)

        fun queryList(sql: String): List<String> =
            connect().use { c ->
                c.createStatement().executeQuery(sql).use { rs ->
                    val out = mutableListOf<String>()
                    while (rs.next()) out.add(rs.getString(1))
                    out
                }
            }

        describe("schema shape after V1") {
            it("creates the seven expected business tables") {
                val tables =
                    queryList(
                        """
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_name NOT LIKE 'flyway_%'
                        ORDER BY table_name
                        """.trimIndent(),
                    )
                tables shouldContainAll
                    listOf(
                        "api_keys",
                        "organization_members",
                        "organizations",
                        "personal_access_tokens",
                        "project_languages",
                        "projects",
                        "users",
                    )
            }

            it("records the V1 migration in flyway_schema_history with a success flag") {
                val rows =
                    connect().use { c ->
                        c
                            .createStatement()
                            .executeQuery(
                                "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank",
                            ).use { rs ->
                                val out = mutableListOf<Pair<String?, Boolean>>()
                                while (rs.next()) out.add(rs.getString(1) to rs.getBoolean(2))
                                out
                            }
                    }
                rows.first() shouldBe ("1" to true)
            }
        }

        describe("users table") {
            it("has unique indexes on external_id and email") {
                val constraints =
                    queryList(
                        """
                        SELECT constraint_name FROM information_schema.table_constraints
                        WHERE table_name = 'users' AND constraint_type = 'UNIQUE'
                        """.trimIndent(),
                    )
                constraints shouldContainAll listOf("uk_users_external_id", "uk_users_email")
            }

            it("supports email verification via null/non-null email_verified_at") {
                connect().use { c ->
                    c.createStatement().execute(
                        """
                        INSERT INTO users (external_id, email, full_name)
                        VALUES ('01HT000000USER0000000VERIF', 'a@example.com', 'A')
                        """.trimIndent(),
                    )
                    val verified =
                        c
                            .createStatement()
                            .executeQuery("SELECT email_verified_at FROM users WHERE email = 'a@example.com'")
                            .use { rs ->
                                rs.next()
                                rs.getTimestamp(1)
                            }
                    verified shouldBe null
                }
            }
        }

        describe("foreign keys are declared ON DELETE CASCADE") {
            it("cascade-deletes memberships when an organization is deleted") {
                connect().use { c ->
                    c.createStatement().execute(
                        """
                        INSERT INTO users (id, external_id, email, full_name)
                        VALUES (1001, '01HT001000USER0000000CASCD', 'cascade@example.com', 'C')
                        """.trimIndent(),
                    )
                    c.createStatement().execute(
                        """
                        INSERT INTO organizations (id, external_id, slug, name)
                        VALUES (2001, '01HT001000ORG00000000CASCD', 'cascade-org', 'Cascade')
                        """.trimIndent(),
                    )
                    c.createStatement().execute(
                        """
                        INSERT INTO organization_members (external_id, organization_id, user_id, role)
                        VALUES ('01HT001000MEM00000000CASCD', 2001, 1001, 'OWNER')
                        """.trimIndent(),
                    )
                    c.createStatement().execute("DELETE FROM organizations WHERE id = 2001")
                    val remaining =
                        c
                            .createStatement()
                            .executeQuery("SELECT COUNT(*) FROM organization_members WHERE organization_id = 2001")
                            .use { rs ->
                                rs.next()
                                rs.getInt(1)
                            }
                    remaining shouldBe 0
                }
            }
        }

        describe("check constraints") {
            it("rejects an invalid organization_members.role") {
                val ex =
                    runCatching {
                        connect().use { c ->
                            c.createStatement().execute(
                                """
                                INSERT INTO users (id, external_id, email, full_name)
                                VALUES (3001, '01HT003000USER00000000CHK0', 'chk@example.com', 'C')
                                """.trimIndent(),
                            )
                            c.createStatement().execute(
                                """
                                INSERT INTO organizations (id, external_id, slug, name)
                                VALUES (3002, '01HT003000ORG000000000CHK0', 'chk-org', 'CheckOrg')
                                """.trimIndent(),
                            )
                            c.createStatement().execute(
                                """
                                INSERT INTO organization_members (external_id, organization_id, user_id, role)
                                VALUES ('01HT003000MEM000000000CHK0', 3002, 3001, 'BOGUS')
                                """.trimIndent(),
                            )
                        }
                    }.exceptionOrNull()
                (ex != null) shouldBe true
            }

            it("rejects an invalid projects.ai_provider") {
                val ex =
                    runCatching {
                        connect().use { c ->
                            c.createStatement().execute(
                                """
                                INSERT INTO organizations (id, external_id, slug, name)
                                VALUES (4001, '01HT004000ORG000000000AIP0', 'ai-org', 'AiOrg')
                                """.trimIndent(),
                            )
                            c.createStatement().execute(
                                """
                                INSERT INTO projects (external_id, organization_id, slug, name, ai_provider)
                                VALUES ('01HT004000PRJ000000000AIP0', 4001, 'app', 'App', 'MADE_UP')
                                """.trimIndent(),
                            )
                        }
                    }.exceptionOrNull()
                (ex != null) shouldBe true
            }

            it("rejects a negative ai_budget_cap_usd_monthly") {
                val ex =
                    runCatching {
                        connect().use { c ->
                            c.createStatement().execute(
                                """
                                INSERT INTO organizations (id, external_id, slug, name)
                                VALUES (5001, '01HT005000ORG000000000BUD0', 'bud-org', 'BudOrg')
                                """.trimIndent(),
                            )
                            c.createStatement().execute(
                                """
                                INSERT INTO projects (external_id, organization_id, slug, name, ai_budget_cap_usd_monthly)
                                VALUES ('01HT005000PRJ000000000BUD0', 5001, 'app', 'App', -1.00)
                                """.trimIndent(),
                            )
                        }
                    }.exceptionOrNull()
                (ex != null) shouldBe true
            }
        }
    })
