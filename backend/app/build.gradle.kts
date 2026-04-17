// backend/app — Quarkus application entry point.
// Depends on every other backend module to wire the final runtime.
// Hosts OpenAPI generation + ArchUnit tests that enforce module boundaries.

plugins {
    id("translately.quarkus-module")
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

    testImplementation(libs.archunit.junit5)
    testImplementation(libs.quarkus.junit5.mockito)
    testImplementation(libs.quarkus.test.security)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
}
