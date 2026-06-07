plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    id("com.google.devtools.ksp")
}

fun xcodeToolsAvailable(): Boolean = try {
    val process = ProcessBuilder("/usr/bin/xcrun", "xcodebuild", "-version")
        .redirectErrorStream(true)
        .start()
    process.waitFor() == 0
} catch (_: Exception) {
    false
}

val enableIosTargets = providers
    .gradleProperty("enableIosTargets")
    .orNull
    ?.toBooleanStrictOrNull()
    ?: xcodeToolsAvailable()

kotlin {
    androidLibrary {
        namespace = "com.pennywiseai.shared"
        compileSdk = 36
        minSdk = 26
    }

    if (enableIosTargets) {
        iosX64()
        iosArm64()
        iosSimulatorArm64()
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation(libs.androidx.room.runtime)
                implementation(libs.androidx.sqlite.bundled)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.pdfbox.android)
            }
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    if (enableIosTargets) {
        add("kspIosX64", libs.androidx.room.compiler)
        add("kspIosArm64", libs.androidx.room.compiler)
        add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    }
}

ksp {
    arg("room.generateKotlin", "true")
}
