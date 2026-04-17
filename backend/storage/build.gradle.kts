// backend/storage — S3-compatible object storage abstraction (port + adapter).

plugins {
    id("translately.quarkus-module")
}

dependencies {
    implementation(platform(libs.awssdk.bom))
    implementation(libs.quarkus.amazon.s3)
}
