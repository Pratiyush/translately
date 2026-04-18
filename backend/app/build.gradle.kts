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

// ---------------------------------------------------------------------------
// T113 — OpenAPI schema generation + commit
// ---------------------------------------------------------------------------
// `quarkus.smallrye-openapi.store-schema-directory` (set in application.yml)
// writes backend/app/build/openapi/openapi.{json,yaml} on every quarkusBuild.
// We mirror the JSON into the repo-root docs/api/ so it's served by Pages and
// consumed by generated SDK clients (T120). CI enforces it stays up-to-date.
// ---------------------------------------------------------------------------

val openApiSourceFile = layout.buildDirectory.file("openapi/openapi.json")
val openApiCommittedFile = rootProject.layout.projectDirectory.file("docs/api/openapi.json")

tasks.register<Copy>("copyOpenApi") {
    group = "openapi"
    description = "Copy the generated OpenAPI JSON into docs/api/openapi.json."
    dependsOn("quarkusBuild")
    from(openApiSourceFile) {
        rename { "openapi.json" }
    }
    into(openApiCommittedFile.asFile.parentFile)
}

tasks.register("checkOpenApiUpToDate") {
    group = "verification"
    description = "Fail the build if docs/api/openapi.json differs from the generated schema (run copyOpenApi to fix)."
    dependsOn("quarkusBuild")
    inputs.file(openApiSourceFile)
    inputs.file(openApiCommittedFile)
    doLast {
        val generated = openApiSourceFile.get().asFile
        val committed = openApiCommittedFile.asFile
        if (!generated.exists()) {
            throw GradleException(
                "Generated OpenAPI at ${generated.relativeTo(rootDir)} is missing — " +
                    "did quarkusBuild run with quarkus.smallrye-openapi.store-schema-directory set?",
            )
        }
        if (!committed.exists() || generated.readText() != committed.readText()) {
            throw GradleException(
                "docs/api/openapi.json is out of date. Run `./gradlew :backend:app:copyOpenApi` " +
                    "and commit the result.",
            )
        }
    }
}

// Wire the stale-schema check into the standard verification pipeline so
// `./gradlew build` (and therefore CI) surfaces drift without a dedicated step.
tasks.named("check") {
    dependsOn("checkOpenApiUpToDate")
}
