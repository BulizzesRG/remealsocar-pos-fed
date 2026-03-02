plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.coroutines.core)
            implementation(libs.datetime)
            implementation(libs.napier)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
