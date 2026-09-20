plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.multiplatform)
}
val mapTilerApiKey = providers.environmentVariable("MAPTILER_API_KEY").orElse("").get()


kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation("org.maplibre.gl:android-sdk-opengl:13.0.1")
            implementation(project(":core"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

android {
    namespace = "org.maplibre.kotlin.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.maplibre.kotlin.android"
        minSdk = 23
        ndk {
            abiFilters += "x86_64"
        }
        targetSdk = 35
        versionCode = 1
        buildConfigField("String", "MAPTILER_API_KEY", "\"$mapTilerApiKey\"")
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true
    }

    val sampleAssets by tasks.registering(Copy::class) {
        from("../../test") {
            include("basic.json")
        }
        into(layout.buildDirectory.dir("generated/sampleAssets"))
    }
    sourceSets {
        getByName("main").assets.srcDir(sampleAssets)
    }
    tasks.configureEach {
        if (name == "mergeDebugAssets") dependsOn(sampleAssets)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}