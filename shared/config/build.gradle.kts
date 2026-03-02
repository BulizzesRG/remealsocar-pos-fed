plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
