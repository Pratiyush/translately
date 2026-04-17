rootProject.name = "translately"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven(url = "https://jitpack.io") { name = "JitPack" }
    }
}

include(
    ":backend:api",
    ":backend:data",
    ":backend:service",
    ":backend:security",
    ":backend:jobs",
    ":backend:ai",
    ":backend:mt",
    ":backend:storage",
    ":backend:email",
    ":backend:webhooks",
    ":backend:cdn",
    ":backend:audit",
    ":backend:app",
)
