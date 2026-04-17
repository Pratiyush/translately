// Quarkus application convention — applied ONLY to `:backend:app`.
// Wires the io.quarkus plugin, enables native-image builds, hosts OpenAPI
// generation, and pulls every backend module's runtime together.
//
// Library modules use `translately.quarkus-module` (no io.quarkus plugin).

plugins {
    id("translately.quarkus-module")
    id("io.quarkus")
}
