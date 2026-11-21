plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

android {
    namespace = "kirakira.dokidoki"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "kirakira.dokidoki"
        minSdk = 24
        targetSdk = 37
        versionCode = 33
        versionName = "2.0"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildFeatures {
        compose = true
    }

    // Compact Release: shrink unused code and resources; dependency versions remain pinned.
    buildTypes {
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Local preview only: sign with the existing debug certificate.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging {
        jniLibs {
            pickFirsts += "**/libandroidx.graphics.path.so"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation(compose.components.resources)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.datastore:datastore-preferences:1.3.0")
    implementation("androidx.graphics:graphics-path:1.1.0")
    implementation(group = "", name = "quickjs-android-0.9.2-16kb", ext = "aar")

    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.5-beta03")
    implementation("top.yukonga.miuix.kmp:miuix-glass:0.9.5-beta03")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.5-beta03")
    implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.5-beta03")
    implementation("top.yukonga.miuix.kmp:miuix-glass-icons:0.9.5-beta03")
}