plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            implementation(project(":shared:auth"))
            implementation(project(":shared:ui"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(libs.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
        }
    }
}
