// backend/mt — MachineTranslator port + vendor adapters (DeepL, Google, AWS).
// Leaf module; BYOK, entirely optional.

plugins {
    id("translately.quarkus-module")
}

dependencies {
    implementation(libs.quarkus.rest.jackson)
}
