import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.the

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

abstract class GenerateAppVersionConstantsTask : DefaultTask() {
    @get:Input
    abstract val appVersionCode: Property<String>

    @get:Input
    abstract val appVersionName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val outputFile = outputDir.get()
            .file("com/etologic/mahjongtournamentsuite/domain/AppVersion.kt")
            .asFile

        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            package com.etologic.mahjongtournamentsuite.domain

            object AppVersion {
                const val code: Int = ${appVersionCode.get()}
                const val name: String = "${appVersionName.get()}"
            }
            """.trimIndent(),
        )
    }
}

val versionCatalog = the<VersionCatalogsExtension>().named("libs")
val appVersionCodeValue = versionCatalog.findVersion("app-versionCode").get().requiredVersion
val appVersionNameValue = versionCatalog.findVersion("app-versionName").get().requiredVersion
val generatedAppVersionDir = layout.buildDirectory.dir("generated/source/appVersion/commonMain/kotlin")

val generateAppVersionConstants by tasks.registering(GenerateAppVersionConstantsTask::class) {
    appVersionCode.set(appVersionCodeValue)
    appVersionName.set(appVersionNameValue)
    outputDir.set(generatedAppVersionDir)
}

kotlin {
    android {
        namespace = "com.etologic.mahjongtournamentsuite.domain"
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
        val commonMain by getting {
            kotlin.srcDir(generateAppVersionConstants)

            dependencies {
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
