import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.bridge"
    compileSdk = 35

    // Load secrets from local.properties
    val properties = Properties()
    val propertiesFile = project.rootProject.file("local.properties")
    if (propertiesFile.exists()) {
        properties.load(propertiesFile.inputStream())
    }

    // This must be here to generate the BuildConfig class
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.bridge"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Inject the variables into the app's DNA
        buildConfigField("String", "BASE_URL", "\"${properties.getProperty("BASE_URL") ?: ""}\"")
        buildConfigField("String", "JWT_TOKEN", "\"${properties.getProperty("JWT_TOKEN") ?: ""}\"")
        buildConfigField("String", "ACCOUNT_UUID", "\"${properties.getProperty("ACCOUNT_UUID") ?: ""}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    // Core Android libraries
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    
    // Security and Networking
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    
    // Background Tasks
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}