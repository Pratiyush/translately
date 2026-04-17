// backend/jobs — Quartz batch jobs (scheduled + DB-queued tasks).

plugins {
    id("translately.quarkus-module")
}

dependencies {
    implementation(project(":backend:service"))

    implementation(libs.quarkus.quartz)
}
