package io.translately.app.auth

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Boots Testcontainers Postgres 16 + Mailpit for auth integration tests.
 *
 * Applied to a test class via
 * `@QuarkusTestResource(PostgresAndMailpitResource::class)`. On `start` we
 * expose the JDBC URL and SMTP host/port back to Quarkus as config
 * overrides so Hibernate + Flyway + Mailer wire up without docker-compose.
 *
 * We don't rely on Mailpit's HTTP API here — the assertions in
 * [AuthResourceIT] exercise the service via RestAssured and verify
 * side-effects through the database, so a plain SMTP sink is enough.
 * `mailpit:latest` on port 1025 is the de-facto dev SMTP image used by
 * `docker-compose.yml`; we pin a specific tag so CI is reproducible.
 */
class PostgresAndMailpitResource : QuarkusTestResourceLifecycleManager {
    private val postgres =
        PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("translately_test")
            .withUsername("translately")
            .withPassword("translately")

    private val mailpit =
        GenericContainer(DockerImageName.parse("axllent/mailpit:v1.20"))
            .withExposedPorts(SMTP_PORT, HTTP_PORT)
            .withEnv("MP_SMTP_AUTH_ACCEPT_ANY", "1")
            .withEnv("MP_SMTP_AUTH_ALLOW_INSECURE", "1")

    override fun start(): Map<String, String> {
        postgres.start()
        mailpit.start()
        val mailpitHttp = "http://${mailpit.host}:${mailpit.getMappedPort(HTTP_PORT)}"
        // Broadcast for MailpitClient ctor injection in tests.
        System.setProperty("translately.test.mailpit-url", mailpitHttp)
        return mapOf(
            "quarkus.datasource.db-kind" to "postgresql",
            "quarkus.datasource.jdbc.url" to postgres.jdbcUrl,
            "quarkus.datasource.username" to postgres.username,
            "quarkus.datasource.password" to postgres.password,
            "quarkus.datasource.devservices.enabled" to "false",
            "quarkus.hibernate-orm.active" to "true",
            "quarkus.hibernate-orm.database.generation" to "none",
            "quarkus.flyway.active" to "true",
            "quarkus.flyway.migrate-at-start" to "true",
            "quarkus.flyway.locations" to "classpath:db/migration",
            "quarkus.mailer.from" to "noreply@translately.local",
            "quarkus.mailer.host" to mailpit.host,
            "quarkus.mailer.port" to mailpit.getMappedPort(SMTP_PORT).toString(),
            "quarkus.mailer.start-tls" to "DISABLED",
            "quarkus.mailer.auth-methods" to "",
            "quarkus.mailer.mock" to "false",
            "translately.mail.from" to "noreply@translately.local",
            "translately.mail.base-url" to "http://localhost:5173",
            "translately.test.mailpit-url" to mailpitHttp,
        )
    }

    override fun stop() {
        runCatching { mailpit.stop() }
        runCatching { postgres.stop() }
    }

    companion object {
        const val SMTP_PORT: Int = 1025
        const val HTTP_PORT: Int = 8025
    }
}
