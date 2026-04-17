// backend/security — JWT, OIDC, LDAP, CryptoService envelope encryption.
// Leaf module; no dependencies on other Translately modules.

plugins {
    id("translately.quarkus-module")
}

dependencies {
    implementation(libs.quarkus.smallrye.jwt)
    implementation(libs.quarkus.smallrye.jwt.build)
    implementation(libs.quarkus.oidc)
    implementation(libs.quarkus.elytron.security.ldap)
    implementation(libs.argon2)
}
