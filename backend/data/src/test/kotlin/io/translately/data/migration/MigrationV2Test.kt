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
 * Apply V1 + V2 against a real Postgres 16 (Testcontainers) and assert the
 * three token tables exist with the expected columns, unique constraints,
 * FKs, and cascade semantics.
 *
 * Mirrors [MigrationV1Test] in shape: the `flyway-core` dependency is
 * loaded at the raw-JVM test classpath by the `:backend:data` module's
 * build script, so we can drive Flyway without spinning Quarkus up.
 */
class MigrationV2Test :
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

        describe("schema shape after V2") {
            it("creates the three token tables on top of V1") {
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
                        "email_verification_tokens",
                        "password_reset_tokens",
                        "refresh_tokens",
                    )
            }

            it("records V2 as applied with success=true") {
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
                rows.map { it.first } shouldContainAll listOf("1", "2")
                rows.all { it.second } shouldBe true
            }
        }

        describe("refresh_tokens table") {
            it("enforces unique jti and unique external_id") {
                val constraints =
                    queryList(
                        """
                        SELECT constraint_name FROM information_schema.table_constraints
                        WHERE table_name = 'refresh_tokens' AND constraint_type = 'UNIQUE'
                        """.trimIndent(),
                    )
                constraints shouldContainAll listOf("uk_refresh_tokens_jti", "uk_refresh_tokens_external_id")
            }

            it("cascade-deletes refresh tokens when the owning user is deleted") {
                connect().use { c ->
                    c.createStatement().execute(
                        """
                        INSERT INTO users (id, external_id, email, full_name)
                        VALUES (7001, '01HT700100USER0000000REFR0', 'refresh@example.com', 'Refresh')
                        """.trimIndent(),
                    )
                    c.createStatement().execute(
                        """
                        INSERT INTO refresh_tokens (external_id, user_id, jti, expires_at)
                        VALUES ('01HT700100REFR0000000REFR0', 7001, 'jti-a', NOW() + INTERVAL '1 day')
                        """.trimIndent(),
                    )
                    c.createStatement().execute("DELETE FROM users WHERE id = 7001")
                    val remaining =
                        c
                            .createStatement()
                            .executeQuery("SELECT COUNT(*) FROM refresh_tokens WHERE user_id = 7001")
                            .use { rs ->
                                rs.next()
                                rs.getInt(1)
                            }
                    remaining shouldBe 0
                }
            }

            it("rejects duplicate jti insertions") {
                val ex =
                    runCatching {
                        connect().use { c ->
                            c.createStatement().execute(
                                """
                                INSERT INTO users (id, external_id, email, full_name)
                                VALUES (7002, '01HT700200USER0000000REFR1', 'dup@example.com', 'Dup')
                                """.trimIndent(),
                            )
                            c.createStatement().execute(
                                """
                                INSERT INTO refresh_tokens (external_id, user_id, jti, expires_at)
                                VALUES ('01HT700200REFR0000000REFR1', 7002, 'jti-dup', NOW() + INTERVAL '1 day')
                                """.trimIndent(),
                            )
                            c.createStatement().execute(
                                """
                                INSERT INTO refresh_tokens (external_id, user_id, jti, expires_at)
                                VALUES ('01HT700200REFR0000000REFR2', 7002, 'jti-dup', NOW() + INTERVAL '1 day')
                                """.trimIndent(),
                            )
                        }
                    }.exceptionOrNull()
                (ex != null) shouldBe true
            }
        }

        describe("email_verification_tokens table") {
            it("enforces unique token_hash and unique external_id") {
                val constraints =
                    queryList(
                        """
                        SELECT constraint_name FROM information_schema.table_constraints
                        WHERE table_name = 'email_verification_tokens' AND constraint_type = 'UNIQUE'
                        """.trimIndent(),
                    )
                constraints shouldContainAll
                    listOf("uk_email_verif_tokens_token_hash", "uk_email_verif_tokens_external_id")
            }

            it("indexes user_id for lookups") {
                val indexes =
                    queryList(
                        """
                        SELECT indexname FROM pg_indexes
                        WHERE tablename = 'email_verification_tokens'
                        """.trimIndent(),
                    )
                indexes shouldContainAll listOf("idx_email_verif_tokens_user")
            }
        }

        describe("password_reset_tokens table") {
            it("enforces unique token_hash and unique external_id") {
                val constraints =
                    queryList(
                        """
                        SELECT constraint_name FROM information_schema.table_constraints
                        WHERE table_name = 'password_reset_tokens' AND constraint_type = 'UNIQUE'
                        """.trimIndent(),
                    )
                constraints shouldContainAll
                    listOf(
                        "uk_password_reset_tokens_token_hash",
                        "uk_password_reset_tokens_external_id",
                    )
            }

            it("indexes user_id for lookups") {
                val indexes =
                    queryList(
                        """
                        SELECT indexname FROM pg_indexes
                        WHERE tablename = 'password_reset_tokens'
                        """.trimIndent(),
                    )
                indexes shouldContainAll listOf("idx_password_reset_tokens_user")
            }
        }
    })
