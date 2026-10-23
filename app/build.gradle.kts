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
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
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
