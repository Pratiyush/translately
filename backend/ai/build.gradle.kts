// backend/ai — AiTranslator port + vendor adapters (Anthropic, OpenAI, OpenAI-compatible).
// Leaf module; BYOK, entirely optional.

plugins {
    id("translately.quarkus-module")
}

dependencies {
    implementation(libs.quarkus.rest.jackson)
}
