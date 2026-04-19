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
 * Apply V1 + V2 + V3 in order against a real Postgres 16 (Testcontainers)
 * and assert the Phase 2 schema (keys, translations, tags, comments,
 * activity) matches expectation. Same forward-compatibility posture as
 * V1/V2 tests — any in-place edit of V3 breaks the build.
 */
class MigrationV3Test :
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

        fun expectSqlFailure(sql: String): Throwable? =
            runCatching { connect().use { c -> c.createStatement().execute(sql) } }.exceptionOrNull()

        describe("schema shape after V3") {
            it("creates the eight new Phase 2 tables") {
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
                        "key_activity",
                        "key_comments",
                        "key_meta",
                        "key_tags",
                        "keys",
                        "namespaces",
                        "tags",
                        "translations",
                    )
            }

            it("records the V3 migration as successful in flyway_schema_history") {
                val success =
                    connect().use { c ->
                        c
                            .createStatement()
                            .executeQuery(
                                """
                                SELECT success FROM flyway_schema_history
                                WHERE version = '3'
                                """.trimIndent(),
                            ).use { rs ->
                                rs.next()
                                rs.getBoolean(1)
                            }
                    }
                success shouldBe true
            }
        }

        describe("keys table") {
            it("has a unique constraint on (project_id, namespace_id, key_name)") {
                val constraints =
                    queryList(
                        """
                        SELECT constraint_name FROM information_schema.table_constraints
                        WHERE table_name = 'keys' AND constraint_type = 'UNIQUE'
                        """.trimIndent(),
                    )
                constraints shouldContainAll
                    listOf("uk_keys_external_id", "uk_keys_project_namespace_name")
            }
        }

        describe("translations table") {
            it("has a unique constraint on (key_id, language_tag)") {
                val constraints =
                    queryList(
                        """
                        SELECT constraint_name FROM information_schema.table_constraints
                        WHERE table_name = 'translations' AND constraint_type = 'UNIQUE'
                        """.trimIndent(),
                    )
                constraints shouldContainAll
                    listOf("uk_translations_external_id", "uk_translations_key_language")
            }
        }

        describe("foreign keys cascade from parents") {
            it("cascade-deletes keys + translations + comments + activity when a project is deleted") {
                connect().use { c ->
                    c.createStatement().execute(
                        """
                        INSERT INTO organizations (id, external_id, slug, name)
                        VALUES (9100, '01HT009100ORG000000000PH20', 'ph2-org', 'Phase2')
                        """.trimIndent(),
                    )
                    c.createStatement().execute(
                        """
                        INSERT INTO projects (id, external_id, organization_id, slug, name)
                        VALUES (9101, '01HT009101PRJ000000000PH20', 9100, 'app', 'App')
                        """.trimIndent(),
                    )
                    c.createStatement().execute(
                        """
                        INSERT INTO namespaces (id, external_id, project_id, slug, name)
                        VALUES (9102, '01HT009102NS0000000000PH20', 9101, 'web', 'Web')
                        """.trimIndent(),
                    )
                    c.createStatement().execute(
                        """
                        INSERT INTO keys (id, external_id, project_id, namespace_id, key_name)
                        VALUES (9103, '01HT009103KEY00000000PH20D', 9101, 9102, 'settings.save')
                        """.trimIndent(),
                    )
                    c.createStatement().execute(
                        """
                        INSERT INTO translations (external_id, key_id, language_tag, value, state)
                        VALUES ('01HT009104TR000000000PH20D', 9103, 'en', 'Save', 'APPROVED')
                        """.trimIndent(),
                    )

                    c.createStatement().execute("DELETE FROM projects WHERE id = 9101")

                    val remainingKeys =
                        c
                            .createStatement()
                            .executeQuery("SELECT COUNT(*) FROM keys WHERE project_id = 9101")
                            .use { rs ->
                                rs.next()
                                rs.getInt(1)
                            }
                    val remainingTranslations =
                        c
                            .createStatement()
                            .executeQuery("SELECT COUNT(*) FROM translations WHERE key_id = 9103")
                            .use { rs ->
                                rs.next()
                                rs.getInt(1)
                            }
                    val remainingNamespaces =
                        c
                            .createStatement()
                            .executeQuery("SELECT COUNT(*) FROM namespaces WHERE project_id = 9101")
                            .use { rs ->
                                rs.next()
                                rs.getInt(1)
                            }
                    remainingKeys shouldBe 0
                    remainingTranslations shouldBe 0
                    remainingNamespaces shouldBe 0
                }
            }
        }

        describe("check constraints") {
            it("rejects an invalid keys.state") {
                val ex =
                    expectSqlFailure(
                        """
                        INSERT INTO organizations (id, external_id, slug, name)
                        VALUES (9200, '01HT009200ORG000000000CHK0', 'chk-ks-org', 'CKS');
                        INSERT INTO projects (id, external_id, organization_id, slug, name)
                        VALUES (9201, '01HT009201PRJ000000000CHK0', 9200, 'app', 'App');
                        INSERT INTO namespaces (id, external_id, project_id, slug, name)
                        VALUES (9202, '01HT009202NS0000000000CHK0', 9201, 'web', 'Web');
                        INSERT INTO keys (external_id, project_id, namespace_id, key_name, state)
                        VALUES ('01HT009203KEY00000000CHK0D', 9201, 9202, 'k', 'BOGUS')
                        """.trimIndent(),
                    )
                (ex != null) shouldBe true
            }

            it("rejects an invalid translations.state") {
                val ex =
                    expectSqlFailure(
                        """
                        INSERT INTO organizations (id, external_id, slug, name)
                        VALUES (9300, '01HT009300ORG000000000TRS0', 'chk-tr-org', 'CTR');
                        INSERT INTO projects (id, external_id, organization_id, slug, name)
                        VALUES (9301, '01HT009301PRJ000000000TRS0', 9300, 'app', 'App');
                        INSERT INTO namespaces (id, external_id, project_id, slug, name)
                        VALUES (9302, '01HT009302NS0000000000TRS0', 9301, 'web', 'Web');
                        INSERT INTO keys (id, external_id, project_id, namespace_id, key_name)
                        VALUES (9303, '01HT009303KEY00000000TRS0D', 9301, 9302, 'k');
                        INSERT INTO translations (external_id, key_id, language_tag, value, state)
                        VALUES ('01HT009304TR000000000TRS0D', 9303, 'en', 'x', 'NONSENSE')
                        """.trimIndent(),
                    )
                (ex != null) shouldBe true
            }

            it("rejects an invalid key_activity.action_type") {
                val ex =
                    expectSqlFailure(
                        """
                        INSERT INTO organizations (id, external_id, slug, name)
                        VALUES (9400, '01HT009400ORG000000000ACT0', 'chk-ac-org', 'CAC');
                        INSERT INTO projects (id, external_id, organization_id, slug, name)
                        VALUES (9401, '01HT009401PRJ000000000ACT0', 9400, 'app', 'App');
                        INSERT INTO namespaces (id, external_id, project_id, slug, name)
                        VALUES (9402, '01HT009402NS0000000000ACT0', 9401, 'web', 'Web');
                        INSERT INTO keys (id, external_id, project_id, namespace_id, key_name)
                        VALUES (9403, '01HT009403KEY00000000ACT0D', 9401, 9402, 'k');
                        INSERT INTO key_activity (external_id, key_id, action_type)
                        VALUES ('01HT009404ACT00000000ACT0D', 9403, 'EXPLODED')
                        """.trimIndent(),
                    )
                (ex != null) shouldBe true
            }
        }

        describe("unique constraints") {
            it("rejects duplicate (project_id, namespace_id, key_name) on keys") {
                connect().use { c ->
                    c.createStatement().execute(
                        """
                        INSERT INTO organizations (id, external_id, slug, name)
                        VALUES (9500, '01HT009500ORG000000000DUP0', 'dup-org', 'Dup')
                        """.trimIndent(),
                    )
                    c.createStatement().execute(
                        """
                        INSERT INTO projects (id, external_id, organization_id, slug, name)
                        VALUES (9501, '01HT009501PRJ000000000DUP0', 9500, 'app', 'App')
                        """.trimIndent(),
                    )
                    c.createStatement().execute(
                        """
                        INSERT INTO namespaces (id, external_id, project_id, slug, name)
                        VALUES (9502, '01HT009502NS0000000000DUP0', 9501, 'web', 'Web')
                        """.trimIndent(),
                    )
                    c.createStatement().execute(
                        """
                        INSERT INTO keys (external_id, project_id, namespace_id, key_name)
                        VALUES ('01HT009503KEY00000000DUP01', 9501, 9502, 'settings.save')
                        """.trimIndent(),
                    )
                }
                val ex =
                    expectSqlFailure(
                        """
                        INSERT INTO keys (external_id, project_id, namespace_id, key_name)
                        VALUES ('01HT009504KEY00000000DUP02', 9501, 9502, 'settings.save')
                        """.trimIndent(),
                    )
                (ex != null) shouldBe true
            }
        }
    })
