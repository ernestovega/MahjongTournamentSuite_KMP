import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.the
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kover)
}

abstract class GenerateAppVersionConstantsTask : DefaultTask() {
    @get:Input
    abstract val webVersionName: Property<String>

    @get:Input
    abstract val adminVersionName: Property<String>

    @get:Input
    abstract val adminVersionCode: Property<String>

    @get:Input
    abstract val playerVersionName: Property<String>

    @get:Input
    abstract val playerVersionCode: Property<String>

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

            /** The versions of the three products. They come from gradle/libs.versions.toml. See docs/versioning.md. */
            object AppVersion {
                /** Both web apps: everything deployed to Firebase. */
                const val web: String = "${webVersionName.get()}"

                /** The admin app on Android and desktop. */
                const val admin: String = "${adminVersionName.get()}"
                const val adminCode: Int = ${adminVersionCode.get()}

                /** The player app on Android. */
                const val player: String = "${playerVersionName.get()}"
                const val playerCode: Int = ${playerVersionCode.get()}
            }
            """.trimIndent(),
        )
    }
}

val versionCatalog = the<VersionCatalogsExtension>().named("libs")
fun catalogVersion(alias: String): String = versionCatalog.findVersion(alias).get().requiredVersion
val generatedAppVersionDir = layout.buildDirectory.dir("generated/source/appVersion/commonMain/kotlin")

val generateAppVersionConstants by tasks.registering(GenerateAppVersionConstantsTask::class) {
    webVersionName.set(catalogVersion("web-versionName"))
    adminVersionName.set(catalogVersion("admin-versionName"))
    adminVersionCode.set(catalogVersion("admin-versionCode"))
    playerVersionName.set(catalogVersion("player-versionName"))
    playerVersionCode.set(catalogVersion("player-versionCode"))
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
