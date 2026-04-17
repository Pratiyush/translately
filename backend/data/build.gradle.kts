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
}
