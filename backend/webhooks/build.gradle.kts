// backend/webhooks — outgoing webhook delivery (HMAC, retries, delivery log).

plugins {
    id("translately.quarkus-module")
}

dependencies {
    implementation(libs.quarkus.rest.jackson)
    implementation(libs.quarkus.redis.client)
}
