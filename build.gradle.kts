plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}

val includedModules = listOf(
    ":app-desktop",
    ":shared:core",
    ":shared:network",
    ":shared:auth",
    ":shared:features:login",
    ":shared:features:shell",
    ":shared:features:sale",
    ":shared:features:cash",
    ":shared:features:credit",
    ":shared:features:catalog",
    ":shared:features:operations",
    ":shared:features:reports",
    ":shared:features:orders",
    ":shared:features:device-setup",
    ":shared:ui",
    ":shared:config",
)

subprojects {
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
            jvmToolchain(21)
        }
    }
}

tasks.register("test") {
    group = "verification"
    description = "Runs tests across all subprojects."
    dependsOn(includedModules.map { "$it:allTests" })
}

tasks.register("build") {
    group = "build"
    description = "Builds all subprojects."
    dependsOn(includedModules.map { "$it:build" })
}
