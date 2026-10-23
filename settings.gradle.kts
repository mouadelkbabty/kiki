pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kiki"
include(":core")
// The Android app needs an SDK; the PC used for core work has none, CI does.
if (System.getenv("ANDROID_HOME") != null || file("local.properties").exists()) {
    include(":app")
}
