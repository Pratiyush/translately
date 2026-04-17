// backend/audit — immutable append-only audit log (port + adapter).

plugins {
    id("translately.quarkus-module")
}

dependencies {
    implementation(libs.quarkus.hibernate.orm.panache.kotlin)
    implementation(libs.quarkus.jdbc.postgresql)
}
