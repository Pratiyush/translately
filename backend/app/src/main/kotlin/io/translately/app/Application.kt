package io.translately.app

/**
 * Translately Quarkus application entry point.
 *
 * Quarkus generates the real `main` at build time via its Gradle plugin, so this file
 * exists to pin the package and host wiring code that doesn't belong in a resource
 * or service. Beans (`@ApplicationScoped`, `@Singleton`) discovered via CDI are
 * activated automatically — no explicit registration needed.
 *
 * Runtime config lives in `src/main/resources/application.yml`. Profile-specific
 * overrides (`%dev`, `%test`, `%prod`) live there too.
 */
object Application
