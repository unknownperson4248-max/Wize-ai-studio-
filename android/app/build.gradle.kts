plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.nova.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.nova.assistant"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
        buildConfigField("String", "DEFAULT_SERVER", "\"https://nova-assistant.rahatwize.workers.dev\"")
    }

    // One fixed signing key, so a newer APK installs over the older one.
    // This key only signs this personal sideloaded app; move it to a GitHub secret before any public release.
    signingConfigs {
        create("nova") {
            storeFile = file("../keystore/nova.jks")
            storePassword = "nova-sideload"
            keyAlias = "nova"
            keyPassword = "nova-sideload"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("nova")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("nova")
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
