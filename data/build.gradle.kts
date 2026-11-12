import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kover)
}

// The API secret of the GA4 Measurement Protocol for the desktop app. The repository is public, so the secret is not
// in the code: it comes from `mts.ga4.apiSecret` in local.properties, or from the variable MTS_GA4_API_SECRET.
// Without it the desktop app sends no Analytics. See docs/telemetry.md.
val desktopTelemetryApiSecret: Provider<String> = providers.environmentVariable("MTS_GA4_API_SECRET")
    .orElse(
        providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText
            .map { text -> Properties().apply { load(text.reader()) }.getProperty("mts.ga4.apiSecret").orEmpty() },
    )
    .orElse("")

val generateDesktopTelemetryResources = tasks.register("generateDesktopTelemetryResources") {
    val apiSecret = desktopTelemetryApiSecret
    val outputDir = layout.buildDirectory.dir("generated/desktopTelemetry")
    inputs.property("apiSecret", apiSecret)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("mts-desktop-telemetry.properties").asFile
        file.parentFile.mkdirs()
        file.writer().use { writer ->
            Properties().apply { setProperty("apiSecret", apiSecret.get().trim()) }.store(writer, null)
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "com.etologic.mahjongtournamentsuite.data"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.domain)
            implementation(libs.kermit)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.serialization.kotlinxJson)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.appcheck)
            implementation(libs.firebase.appcheck.playintegrity)
            implementation(libs.firebase.appcheck.debug)
            implementation(libs.firebase.analytics)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        jvmMain {
            resources.srcDir(generateDesktopTelemetryResources)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
