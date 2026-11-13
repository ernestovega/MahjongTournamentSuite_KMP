// Helpers for the screenshot tests (Roborazzi on the JVM). The modules add it to their jvmTest dependencies.
// See docs/screenshot-tests.md.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            api(project(":designsystem"))
            api(project(":domain"))
            api(compose.desktop.currentOs)
            api(libs.compose.ui.test.junit4.desktop)
            api(libs.roborazzi.compose.desktop)
            // Dispatchers.Main for the screens that switch to it (sign in, navigation after a request).
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotlin.test)
        }
    }
}
