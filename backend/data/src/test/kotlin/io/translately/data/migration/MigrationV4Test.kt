package io.translately.data.migration

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.DriverManager

/**
 * Apply V1..V4 in order against a real Postgres 16 (Testcontainers) and
 * assert the search artefacts land correctly: `pg_trgm` available, the
 * generated `keys.search_vector` column present, GIN indexes on both the
 * vector and `translations.value`. Same forward-compatibility posture as
 * the V1 / V2 / V3 tests.
 */
class MigrationV4Test :
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

        describe("V4 migration metadata") {
            it("records the V4 migration as successful in flyway_schema_history") {
                val success =
                    connect().use { c ->
                        c
                            .createStatement()
                            .executeQuery(
                                """
                                SELECT success FROM flyway_schema_history
                                WHERE version = '4'
                                """.trimIndent(),
                            ).use { rs ->
                                rs.next()
                                rs.getBoolean(1)
                            }
                    }
                success shouldBe true
            }

            it("enables the pg_trgm extension") {
                val extensions = queryList("SELECT extname FROM pg_extension")
                extensions shouldContain "pg_trgm"
            }
        }

        describe("keys.search_vector generated column") {
            it("exists as a stored generated tsvector column") {
                val rows =
                    connect().use { c ->
                        c
                            .createStatement()
                            .executeQuery(
                                """
                                SELECT data_type, is_generated, generation_expression
                                FROM information_schema.columns
                                WHERE table_name = 'keys' AND column_name = 'search_vector'
                                """.trimIndent(),
                            ).use { rs ->
                                val out = mutableListOf<Triple<String, String, String?>>()
                                while (rs.next()) {
                                    out.add(Triple(rs.getString(1), rs.getString(2), rs.getString(3)))
                                }
                                out
                            }
                    }
                rows.size shouldBe 1
                rows[0].first shouldBe "tsvector"
                rows[0].second shouldBe "ALWAYS"
            }

            it("creates a GIN index on search_vector") {
                val indexes =
                    queryList(
                        """
                        SELECT indexname FROM pg_indexes
                        WHERE tablename = 'keys'
                        """.trimIndent(),
                    )
                indexes shouldContain "idx_keys_search_vector"
            }

            it("populates the vector from key_name + description on insert") {
                connect().use { c ->
                    c.createStatement().execute(
                        """
                        INSERT INTO organizations (id, external_id, slug, name)
                        VALUES (9600, '01HT009600ORG000000000FTS0', 'fts-org', 'FTS')
                        """.trimIndent(),
                    )
                    c.createStatement().execute(
                        """
                        INSERT INTO projects (id, external_id, organization_id, slug, name)
                        VALUES (9601, '01HT009601PRJ000000000FTS0', 9600, 'app', 'App')
                        """.trimIndent(),
                    )
                    c.createStatement().execute(
                        """
                        INSERT INTO namespaces (id, external_id, project_id, slug, name)
                        VALUES (9602, '01HT009602NS0000000000FTS0', 9601, 'web', 'Web')
                        """.trimIndent(),
                    )
                    c.createStatement().execute(
                        """
                        INSERT INTO keys (id, external_id, project_id, namespace_id, key_name, description)
                        VALUES (9603, '01HT009603KEY00000000FTS0D', 9601, 9602, 'settings.save', 'Save the settings panel')
                        """.trimIndent(),
                    )
                    val matches =
                        c
                            .createStatement()
                            .executeQuery(
                                """
                                SELECT COUNT(*) FROM keys
                                WHERE id = 9603
                                  AND search_vector @@ plainto_tsquery('simple', 'settings')
                                """.trimIndent(),
                            ).use { rs ->
                                rs.next()
                                rs.getInt(1)
                            }
                    matches shouldBe 1
                }
            }
        }

        describe("translations.value trigram index") {
            it("creates a GIN index on value with gin_trgm_ops") {
                val indexes =
                    queryList(
                        """
                        SELECT indexname FROM pg_indexes
                        WHERE tablename = 'translations'
                        """.trimIndent(),
                    )
                indexes shouldContain "idx_translations_value_trgm"
            }

            it("supports similarity (%) lookups on translation bodies") {
                connect().use { c ->
                    c.createStatement().execute(
                        """
                        INSERT INTO organizations (id, external_id, slug, name)
                        VALUES (9700, '01HT009700ORG000000000TRGM', 'trgm-org', 'TRGM')
                        """.trimIndent(),
                    )
                    c.createStatement().execute(
                        """
                        INSERT INTO projects (id, external_id, organization_id, slug, name)
                        VALUES (9701, '01HT009701PRJ000000000TRGM', 9700, 'app', 'App')
                        """.trimIndent(),
                    )
                    c.createStatement().execute(
                        """
                        INSERT INTO namespaces (id, external_id, project_id, slug, name)
                        VALUES (9702, '01HT009702NS0000000000TRGM', 9701, 'web', 'Web')
                        """.trimIndent(),
                    )
                    c.createStatement().execute(
                        """
                        INSERT INTO keys (id, external_id, project_id, namespace_id, key_name)
                        VALUES (9703, '01HT009703KEY00000000TRGMD', 9701, 9702, 'greeting.welcome')
                        """.trimIndent(),
                    )
                    c.createStatement().execute(
                        """
                        INSERT INTO translations (external_id, key_id, language_tag, value, state)
                        VALUES ('01HT009704TR000000000TRGMD', 9703, 'en', 'Welcome to Translately', 'APPROVED')
                        """.trimIndent(),
                    )
                    // pg_trgm default similarity threshold is 0.3; "Welcome" vs
                    // "Welcome to Translately" matches comfortably above that.
                    c.createStatement().execute("SET pg_trgm.similarity_threshold = 0.1")
                    val matches =
                        c
                            .createStatement()
                            .executeQuery(
                                """
                                SELECT COUNT(*) FROM translations
                                WHERE key_id = 9703
                                  AND value % 'welcom'
                                """.trimIndent(),
                            ).use { rs ->
                                rs.next()
                                rs.getInt(1)
                            }
                    matches shouldBe 1
                }
            }
        }
    })
