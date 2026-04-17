// buildSrc is compiled once before anything else in the build.
// The dependencies added here become available on the classpath of the
// convention plugins under `src/main/kotlin/`.

plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.plugin.kotlin)
    implementation(libs.plugin.kotlin.allopen)
    implementation(libs.plugin.kotlin.noarg)
    implementation(libs.plugin.ktlint)
    implementation(libs.plugin.detekt)
    implementation(libs.plugin.quarkus)
}
