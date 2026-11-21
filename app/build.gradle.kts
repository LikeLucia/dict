import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

val versionPropertiesFile = rootProject.file("version.properties")
val versionProperties = Properties().apply {
    versionPropertiesFile.inputStream().use { load(it) }
}
val isCiBuild = !System.getenv("CI").isNullOrBlank()
val buildsApk = gradle.startParameter.taskNames.any { requested ->
    val taskName = requested.substringAfterLast(':')
    taskName == "build" || taskName.startsWith("assemble") ||
        taskName.startsWith("bundle") || taskName.startsWith("install")
}
var buildVersionCode = versionProperties.getProperty("versionCode").toInt()
if (buildsApk && !isCiBuild) {
    buildVersionCode += 1
    versionPropertiesFile.writeText("versionCode=$buildVersionCode\n")
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
        versionCode = buildVersionCode
        versionName = "2.0"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildFeatures {
        compose = true
    }

    // Release signing: use the release keystore when its path is provided (CI),
    // otherwise fall back to the debug certificate for local preview builds.
    val releaseKeystorePath = providers.environmentVariable("RELEASE_KEYSTORE_PATH").orNull
    val releaseStorePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD").orNull
    if (!releaseKeystorePath.isNullOrBlank()) {
        signingConfigs.create("release") {
            storeFile = file(releaseKeystorePath)
            storePassword = releaseStorePassword
            keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull ?: "ksm"
            keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull ?: releaseStorePassword
            storeType = providers.environmentVariable("RELEASE_STORE_TYPE").orNull ?: "PKCS12"
        }
    }

    // Compact Release: shrink unused code and resources; dependency versions remain pinned.
    buildTypes {
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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