// backend/service — use-case logic.
// Depends on data + security + every adapter module.
// Must NOT depend on :backend:api.

plugins {
    id("translately.quarkus-module")
}

dependencies {
    implementation(project(":backend:data"))
    implementation(project(":backend:security"))
    implementation(project(":backend:ai"))
    implementation(project(":backend:mt"))
    implementation(project(":backend:storage"))
    implementation(project(":backend:email"))
    implementation(project(":backend:webhooks"))
    implementation(project(":backend:cdn"))
    implementation(project(":backend:audit"))

    // Needed so AuthService can open EntityManager-level queries / @Transactional
    // without dragging the full Panache-Kotlin DSL into this module.
    implementation(libs.quarkus.hibernate.orm.panache.kotlin)

    implementation(libs.icu4j)
    implementation(libs.quarkus.redis.client)

    // Jackson for the i18next JSON importer + exporter (T301 + T302).
    // `jackson-bom` pins all transitive versions to the repo-wide 2.18.1
    // so bumps cascade everywhere in one place.
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.module.kotlin)
}
