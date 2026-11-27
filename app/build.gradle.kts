plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val releaseVersion = rootProject.file("version.txt").readText().trim()
val (major, minor, patch) = releaseVersion.split('.').map(String::toInt)
require(major in 0..200000 && minor in 0..99 && patch in 0..99)

android {
    namespace = "com.puzzlequest.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.puzzlequest.app"
        minSdk = 26
        targetSdk = 36
        versionCode = major * 10000 + minor * 100 + patch
        versionName = releaseVersion
    }

    signingConfigs {
        System.getenv("PUZZLE_KEYSTORE")?.let { path ->
            create("publisher") {
                storeFile = file(path)
                storePassword = System.getenv("PUZZLE_KEY_PASSWORD")
                keyAlias = "puzzlequest"
                keyPassword = storePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("publisher")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            assets.directories.add("../content")
            assets.directories.add("../public")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.androidsvg)
    testImplementation(libs.junit)
}
