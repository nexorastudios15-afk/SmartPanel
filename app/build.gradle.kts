plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nexora.smartpanel"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nexora.smartpanel"
        minSdk = 24          // Android 7.0
        targetSdk = 35       // Android 15
        versionCode = 3
        versionName = "1.2"

        // ARM 32-bit dan 64-bit (aplikasi ini murni Java/Kotlin, jadi tidak ada library native)
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // ditandatangani dengan debug key supaya langsung bisa di-install
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
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
    // membuka akses API tersembunyi (setLaunchWindowingMode) agar freeform bisa jalan
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
    // Shizuku: dipakai untuk kill aplikasi seperti geser di Recents
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
