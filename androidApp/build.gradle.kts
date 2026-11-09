import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Analytics needs the file google-services.json of the Android app. See docs/telemetry.md.
// Without the file the app builds and runs, and it sends no telemetry.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Release signing with the upload key of Google Play. The repository is public, so the keystore and its passwords are
// not in it: they come from `mts.signing.admin.*` in local.properties, or from the variables MTS_ADMIN_SIGNING_*.
// Without them the release build is not signed. See docs/android-release.md.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.reader().use { load(it) }
}

fun signingValue(name: String): String? {
    val variable = "MTS_ADMIN_SIGNING_" + name.replace(Regex("([A-Z])"), "_$1").uppercase()
    return providers.environmentVariable(variable).orNull ?: localProperties.getProperty("mts.signing.admin.$name")
}

android {
    namespace = "com.etologic.mahjongtournamentsuite"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.etologic.mahjongtournamentsuite"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = libs.versions.admin.versionCode.get().toInt()
        versionName = libs.versions.admin.versionName.get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Every test runs in a new process with empty app data, so no session or cache leaks from one test to the next.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    signingConfigs {
        val keystore = signingValue("storeFile")
        if (!keystore.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystore)
                storePassword = signingValue("storePassword")
                keyAlias = signingValue("keyAlias")
                keyPassword = signingValue("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        animationsDisabled = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    androidTestImplementation(projects.data)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestUtil(libs.androidx.test.orchestrator)
    androidTestImplementation(libs.androidx.test.ext.junit)
    // Older Espresso versions fail on the newest Android emulator images (InputManager.getInstance).
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(projects.adminApp)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.compose.uiTooling)
    implementation(libs.compose.uiToolingPreview)
}
