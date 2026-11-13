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
                packages("*.generated.resources")
                classes("*ComposableSingletons*", "*_Factory", "*MainKt")
            }
        }
    }
}
