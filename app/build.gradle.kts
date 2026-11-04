plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.visionosmobile"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.visionosmobile"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-prototype"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
}
dependencies {
    implementation("com.google.mediapipe:tasks-vision:0.10.20")
    testImplementation("junit:junit:4.13.2")
}
