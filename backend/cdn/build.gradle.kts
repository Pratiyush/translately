// backend/cdn — per-project JSON bundle export → S3 signed URLs, content-hash versioned.

plugins {
    id("translately.quarkus-module")
}

dependencies {
    implementation(platform(libs.awssdk.bom))
    implementation(libs.quarkus.amazon.s3)
    implementation(libs.quarkus.rest.jackson)
}
