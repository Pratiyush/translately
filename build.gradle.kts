// Root build.gradle.kts — intentionally minimal.
// Per-module configuration lives in `buildSrc/src/main/kotlin/translately.*.gradle.kts`
// convention plugins. Each module's `build.gradle.kts` applies the convention
// plugin and declares its module-specific dependencies.

// Surface the project version from gradle.properties. Bumped by the release workflow.
version = providers.gradleProperty("translately.version").getOrElse("0.0.1-SNAPSHOT")
group = "io.translately"

tasks.register("printVersion") {
    doLast { println(project.version) }
}
