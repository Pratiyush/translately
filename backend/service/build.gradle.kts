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
}
