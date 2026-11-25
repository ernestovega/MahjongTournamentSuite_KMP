import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The suite of the mobile apps: the player part and the admin part in one app (SuiteRoot). The Android app
// (androidApp) and the iOS app (iosApp, through the `Shared` framework) both show it. See docs/ios-release.md.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    android {
        namespace = "com.etologic.mahjongtournamentsuite.suite"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // The framework that the Xcode project of iosApp links. Xcode builds it with embedAndSignAppleFrameworkForXcode.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(projects.adminApp)
            implementation(projects.playerApp)
            implementation(projects.ui)
            implementation(projects.domain)
        }
        iosMain.dependencies {
            implementation(projects.data)
        }
    }
}
