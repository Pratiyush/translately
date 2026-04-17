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

    implementation(libs.icu4j)
    implementation(libs.quarkus.redis.client)
}
