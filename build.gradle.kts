plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.googleServices) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.roborazzi) apply false
    // Code coverage of the JVM tests: ./gradlew koverHtmlReportCoverage (report in build/reports/kover/htmlCoverage).
    alias(libs.plugins.kover)
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        // The generated sources are not ours. Code that existed before the lint check is in the baseline file.
        filter { exclude { it.file.path.contains("/build/") } }
        baseline.set(file("ktlint-baseline.xml"))
    }
}

// The coverage report merges the modules with shared code. The generated sources and the apps' entry points are not counted.
dependencies {
    kover(project(":domain"))
    kover(project(":data"))
    kover(project(":designsystem"))
    kover(project(":ui"))
    kover(project(":playerApp"))
    kover(project(":adminApp"))
}

// Only the JVM tests count: the Android variants need an Android SDK and run no other tests.
// ./gradlew koverHtmlReportCoverage or koverLogCoverage.
subprojects {
    pluginManager.withPlugin("org.jetbrains.kotlinx.kover") {
        extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension> {
            currentProject { createVariant("coverage") { add("jvm") } }
        }
    }
}

kover {
    currentProject { createVariant("coverage") { } }
    reports {
        filters {
            excludes {
                // Code the compiler writes, not us: the resources, the serializers and default arguments of the
                // backend DTOs (data classes without logic), the @Preview functions and the DefaultImpls forwarders of
                // interface methods with a body, which only Java callers use. See docs/test-scenarios-coverage.md.
                packages("*.generated.resources", "*.backend.dto")
                classes("*ComposableSingletons*", "*_Factory", "*MainKt", "*\$DefaultImpls")
                annotatedBy("androidx.compose.ui.tooling.preview.Preview")
                // The windows and the save and open dialogs of the desktop system: the tests run without a screen,
                // where they cannot open. The tests replace each dialog and measure the code around it.
                annotatedBy("com.etologic.mahjongtournamentsuite.presentation.platform.NativeDesktopWindow")
            }
        }
    }
}

// The screenshot tests (Roborazzi, see docs/screenshot-tests.md) draw the system font with a bundled one on Linux, so
// the images are the same in the cloud, in CI and on any Linux machine. macOS draws text in its own way: record there
// only to look at the images, never to commit them.
subprojects {
    pluginManager.withPlugin("io.github.takahirom.roborazzi") {
        tasks.withType<Test>().configureEach {
            if (org.gradle.internal.os.OperatingSystem.current().isLinux) {
                environment("FONTCONFIG_FILE", rootProject.file("screenshotTesting/fonts/fonts.conf").absolutePath)
                // Times on screen, such as "loaded 10:00", use the zone of the machine.
                systemProperty("user.timezone", "UTC")
            }
        }
    }
}
