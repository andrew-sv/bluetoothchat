plugins {
    id("com.android.application")
}

android {
    namespace = "com.bluetoothchat"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bluetoothchat"
        minSdk = 18           // Galaxy S3 (Android 4.3)
        targetSdk = 34        // Note 10 Lite handles Android 12+ permission model
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    // Last AppCompat release that supports minSdk < 19. Required for Galaxy S3 (API 18).
    implementation("androidx.appcompat:appcompat:1.3.1")
}
