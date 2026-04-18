// backend/app — Quarkus application entry point.
// Depends on every other backend module to wire the final runtime.
// Hosts OpenAPI generation + ArchUnit tests that enforce module boundaries.

plugins {
    id("translately.quarkus-app")
}

dependencies {
    implementation(project(":backend:api"))
    implementation(project(":backend:service"))
    implementation(project(":backend:data"))
    implementation(project(":backend:security"))
    implementation(project(":backend:jobs"))
    implementation(project(":backend:ai"))
    implementation(project(":backend:mt"))
    implementation(project(":backend:storage"))
    implementation(project(":backend:email"))
    implementation(project(":backend:webhooks"))
    implementation(project(":backend:cdn"))
    implementation(project(":backend:audit"))

    // App-only cross-cutting extensions
    implementation(libs.quarkus.rest)
    implementation(libs.quarkus.rest.jackson)
    implementation(libs.quarkus.smallrye.health)
    implementation(libs.quarkus.smallrye.openapi)
    implementation(libs.quarkus.micrometer.prometheus)
    implementation(libs.quarkus.opentelemetry)

    // Hibernate ORM + Panache Kotlin is needed on the app classpath so
    // EntityManager is available to tests (AuthServiceIT uses @Inject
    // EntityManager to seed rows before exercising the service).
    implementation(libs.quarkus.hibernate.orm.panache.kotlin)
    implementation(libs.quarkus.jdbc.postgresql)
    implementation(libs.quarkus.flyway)
    implementation(libs.quarkus.mailer)
    implementation(libs.quarkus.qute)

    testImplementation(libs.archunit.junit5)
    testImplementation(libs.quarkus.junit5.mockito)
    testImplementation(libs.quarkus.test.security)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)

    // RestAssured + Hamcrest — Quarkus BOM manages the versions.
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("org.hamcrest:hamcrest")
}
