plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            implementation(project(":shared:network"))
            implementation(libs.serialization.json)
            implementation(libs.coroutines.core)
            implementation(libs.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(project(":shared:config"))
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.serialization.json)
        }
    }
}
