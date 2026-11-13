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
            api(compose.desktop.currentOs)
            api(libs.compose.ui.test.junit4.desktop)
            api(libs.roborazzi.compose.desktop)
            implementation(libs.kotlin.test)
        }
    }
}
