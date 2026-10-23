plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.kiki.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kiki.tv"
        minSdk = 30
        targetSdk = 30
        versionCode = 9
        versionName = "2.1.0"
    }

    // Release signing comes from CI secrets; without them the release build is left unsigned.
    val keystorePath = System.getenv("KIKI_KEYSTORE_PATH")
    if (keystorePath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KIKI_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KIKI_KEY_ALIAS")
                keyPassword = System.getenv("KIKI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    lint {
        // kiki is side-loaded on an Android 11 TV, never published on Google Play.
        disable += "ExpiredTargetSdkVersion"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core"))
}
