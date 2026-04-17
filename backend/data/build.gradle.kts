// backend/data — JPA entities, Panache repositories, Flyway migrations.
// Leaf module; no dependencies on other Translately modules.

plugins {
    id("translately.quarkus-module")
}

dependencies {
    implementation(libs.quarkus.hibernate.orm.panache.kotlin)
    implementation(libs.quarkus.jdbc.postgresql)
    implementation(libs.quarkus.flyway)

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    // Needed on the plain-JVM test classpath so DriverManager can resolve
    // jdbc:postgresql URLs for the Flyway migration test. Version via Quarkus BOM.
    testImplementation("org.postgresql:postgresql")
    // Flyway core for the raw JVM migration test (quarkus-flyway exposes it at
    // runtime but keep it explicit for the test classpath).
    testImplementation("org.flywaydb:flyway-core")
    testImplementation("org.flywaydb:flyway-database-postgresql")
}
