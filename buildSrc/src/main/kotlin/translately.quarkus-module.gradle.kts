// Quarkus module convention — extends base with Kotlin-for-Quarkus plugins
// and the common Quarkus BOM + core extensions every module needs.
//
// Module-specific Quarkus extensions and project deps live in the module's
// own build.gradle.kts, where the type-safe `libs.*` accessor is available.

import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("translately.base")
    id("org.jetbrains.kotlin.plugin.allopen")
    id("org.jetbrains.kotlin.plugin.noarg")
    id("io.quarkus")
}

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    "implementation"(enforcedPlatform(versionCatalog.findLibrary("quarkus-bom").get()))
    "implementation"(versionCatalog.findLibrary("quarkus-core").get())
    "implementation"(versionCatalog.findLibrary("quarkus-arc").get())
    "implementation"(versionCatalog.findLibrary("quarkus-kotlin").get())
    "implementation"(versionCatalog.findLibrary("quarkus-config-yaml").get())
    "implementation"(versionCatalog.findLibrary("quarkus-logging-json").get())

    "testImplementation"(enforcedPlatform(versionCatalog.findLibrary("quarkus-bom").get()))
    "testImplementation"(versionCatalog.findLibrary("quarkus-junit5").get())
    "testImplementation"(versionCatalog.findBundle("kotest").get())
    "testImplementation"(versionCatalog.findLibrary("mockk").get())
}

// CDI beans written as Kotlin classes need the `all-open` plugin to be non-final.
allOpen {
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("jakarta.enterprise.context.RequestScoped")
    annotation("jakarta.inject.Singleton")
}

// @Entity data classes need a no-arg constructor for Hibernate.
noArg {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.Embeddable")
    annotation("jakarta.persistence.MappedSuperclass")
}
