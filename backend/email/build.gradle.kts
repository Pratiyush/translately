// backend/email — Quarkus Mailer + Qute templates (port + adapter).

plugins {
    id("translately.quarkus-module")
}

dependencies {
    implementation(libs.quarkus.mailer)
    implementation(libs.quarkus.qute)
}
