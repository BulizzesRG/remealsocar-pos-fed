plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
}

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":shared:core"))
            implementation(project(":shared:config"))
            implementation(project(":shared:network"))
            implementation(project(":shared:auth"))
            implementation(project(":shared:features:login"))
            implementation(project(":shared:features:shell"))
            implementation(project(":shared:features:sale"))
            implementation(project(":shared:features:cash"))
            implementation(project(":shared:features:catalog"))
            implementation(project(":shared:features:operations"))
            implementation(project(":shared:features:reports"))
            implementation(project(":shared:ui"))
            implementation(compose.desktop.currentOs)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(libs.koin.core)
            implementation(libs.serialization.json)
            implementation(libs.coroutines.core)
            implementation(libs.ktor.client.cio)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlin.test.junit)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.posfab.app.MainKt"
    }
}
