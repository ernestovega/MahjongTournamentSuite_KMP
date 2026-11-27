import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The app for players: Android library plus the web application. It has no desktop application.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kover)
    alias(libs.plugins.roborazzi)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "com.etologic.mahjongtournamentsuite.player"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        androidResources {
            enable = true
        }
    }

    // The JVM target only runs the tests of the shared code.
    jvm()

    iosArm64()
    iosSimulatorArm64()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        // The UI tests that run on the desktop and on the iOS simulator alike (docs/ios-release.md).
        jvmTest { kotlin.srcDir("src/uiTest/kotlin") }
        iosTest {
            kotlin.srcDir("src/uiTest/kotlin")
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.compose.ui.test)
            }
        }
        commonMain.dependencies {
            implementation(libs.androidx.navigation.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.koin.compose)
            implementation(libs.koin.core)
            implementation(libs.kermit)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(projects.domain)
            implementation(projects.data)
            implementation(projects.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(projects.screenshotTesting)
            implementation(libs.ktor.client.mock)
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.ui.test.junit4.desktop)
            implementation(libs.koin.test)
        }
        wasmJsMain.dependencies {
            implementation(npm("@js-joda/timezone", "3.1.0"))
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}

// The UI tests look for English texts, so they run in English on every machine.
tasks.withType<Test>().configureEach {
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
}
