// backend/api — JAX-RS resources (REST).
// Depends on service + security ONLY. Must NOT reach into :backend:data directly.

plugins {
    id("translately.quarkus-module")
}

dependencies {
    implementation(project(":backend:service"))
    implementation(project(":backend:security"))

    implementation(libs.quarkus.rest)
    implementation(libs.quarkus.rest.jackson)
    implementation(libs.quarkus.smallrye.openapi)
    implementation(libs.quarkus.smallrye.health)
    // For the MicroProfile JsonWebToken injected into JwtSecurityScopesFilter.
    implementation(libs.quarkus.smallrye.jwt)
    // Jackson's Kotlin module — required so Kotlin `data class` request
    // DTOs (e.g. AuthResource.SignupRequest) deserialize without requiring
    // explicit @JsonCreator annotations. Registered via
    // `KotlinObjectMapperCustomizer` in this module.
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.module.kotlin)
}
