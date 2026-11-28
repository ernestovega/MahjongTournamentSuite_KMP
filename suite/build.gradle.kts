import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The suite: the player part and the admin part in one app (SuiteRoot). The Android app (androidApp), the iOS app
// (iosApp, through the `Shared` framework) and the desktop app (jvmMain, packaged here) show it.
// See docs/ios-release.md and docs/desktop-release.md.
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

    jvm()

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
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.serialization.json)
            implementation(projects.data)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
        }
        iosMain.dependencies {
            implementation(projects.data)
        }
        iosTest {
            // The end-to-end tests of the whole app against the local test backend (docs/ios-release.md). They do
            // nothing without the backend, so the normal test run skips them.
            kotlin.srcDir("src/e2eTest/kotlin")
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.compose.ui.test)
                implementation(libs.androidx.navigation.compose)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.core)
                implementation(projects.data)
            }
        }
    }
}

// The end-to-end tests of src/e2eTest read the address of the local backend, the wait and the screen on the simulator
// (E2ePlatform). The simulator passes the variables that start with SIMCTL_CHILD_ to the test, without the prefix.
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    listOf("E2E_BACKEND_URL", "E2E_WAIT_MS", "E2E_FORM").forEach { name ->
        providers.environmentVariable(name).orNull?.let { environment("SIMCTL_CHILD_$name", it) }
    }
}

// The desktop app: a DMG for macOS, an MSI for Windows and a DEB for Linux. Each one is built on its own system
// (`./gradlew :suite:packageReleaseDmg`, `packageReleaseMsi`, `packageReleaseDeb`); the workflow Publish · Desktop
// builds the three and puts them in a GitHub release. See docs/desktop-release.md.
compose.desktop {
    application {
        mainClass = "com.etologic.mahjongtournamentsuite.DesktopMainKt"
        jvmArgs += "--enable-native-access=ALL-UNNAMED"

        buildTypes.release.proguard {
            // Koin, Ktor and kotlinx.serialization find classes by reflection. The app is small enough without it.
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "MTS"
            // One version for the Android, iOS and desktop apps: the admin version. Compose desktop requires a major
            // version above zero. See docs/versioning.md.
            packageVersion = libs.versions.admin.versionName.get()
            description = "Mahjong Tournament Suite"
            vendor = "Etologic"
            // jlink keeps only these modules of the JDK (java.base and java.desktop always). The first five are what
            // `./gradlew :suite:suggestRuntimeModules` lists; the rest are found only at run time: TLS with elliptic
            // curves, the dates in every language, DNS and the screen readers.
            modules(
                "java.instrument",
                "java.management",
                "java.net.http",
                "java.prefs",
                "jdk.unsupported",
                "jdk.crypto.ec",
                "jdk.localedata",
                "java.naming",
                "jdk.accessibility",
            )

            macOS {
                bundleID = "com.etologic.mahjongtournamentsuite.desktop"
                appCategory = "public.app-category.productivity"
                iconFile.set(rootProject.file("adminApp/desktop-icons/icon.icns"))
                // CI signs with the Developer ID certificate in its keychain (docs/desktop-release.md).
                System.getenv("MTS_MAC_SIGN_IDENTITY")?.takeIf { it.isNotBlank() }?.let { identity ->
                    signing {
                        sign.set(true)
                        this.identity.set(identity)
                        System.getenv("MTS_MAC_SIGN_KEYCHAIN")?.takeIf { it.isNotBlank() }?.let { keychain.set(it) }
                    }
                }
            }
            windows {
                iconFile.set(rootProject.file("adminApp/desktop-icons/icon.ico"))
                menuGroup = "MTS"
                shortcut = true
                dirChooser = true
                // Installs for the user, so it needs no administrator.
                perUserInstall = true
                // Never change it: Windows finds the installed app by it and replaces it with the new version.
                upgradeUuid = "6C223D9F-2C21-48D8-AE2B-AC081C000B14"
            }
            linux {
                iconFile.set(rootProject.file("adminApp/desktop-icons/icon.png"))
                packageName = "mts"
                menuGroup = "Office"
                shortcut = true
                appCategory = "Office"
            }
        }
    }
}
