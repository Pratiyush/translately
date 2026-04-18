// Base convention plugin — applied by every Translately module.
// Configures Kotlin + Java toolchain + JUnit 5 + ktlint + detekt + JaCoCo.
// Module-specific dependencies (including kotest/mockk) are declared by each
// module's own build.gradle.kts, where the version catalog `libs.*` accessor
// is available directly.

plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
    jacoco
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xcontext-receivers",
        )
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        showStackTraces = true
        showCauses = true
    }
    // Quarkus integration tests + Testcontainers use a lot of heap —
    // CI on GitHub-hosted runners OOM'd `PatAuthenticatorIT` on the
    // default ~1 GB fork limit. 2 GiB is comfortably above measured
    // peak RSS and well within the runner's 7 GiB budget.
    maxHeapSize = "2g"
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.withType<JacocoReport>().configureEach {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

jacoco {
    toolVersion = "0.8.12"
}

ktlint {
    version.set("1.3.1")
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    basePath = rootProject.projectDir.absolutePath
}
