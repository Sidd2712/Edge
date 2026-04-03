import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // This MUST match the folder structure: com/example/bridge
    namespace = "com.example.bridge"
    compileSdk = 35 

    defaultConfig {
        applicationId = "com.example.bridge"
        minSdk = 26 // Required for NotificationListener and Security-Crypto
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    // Standard Android UI Libraries
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    // 🛡️ SECURITY: Hardware-backed encryption for your FastAPI token
    implementation("androidx.security:security-crypto:1.1.0")

    // 🌐 NETWORKING: Retrofit (The Kotlin version of Python's 'requests')
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    
    // Coroutines for background tasks (so the UI doesn't freeze)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}