rootProject.name = "pos-fab-fed"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

include(
    ":app-desktop",
    ":shared:core",
    ":shared:network",
    ":shared:auth",
    ":shared:features:login",
    ":shared:features:shell",
    ":shared:ui",
    ":shared:config"
)
