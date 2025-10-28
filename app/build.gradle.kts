plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
    kotlin("plugin.serialization") version "2.0.0"
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.solarkit"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.solarkit"
        minSdk = 24
        targetSdk = 36
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.protolite.well.known.types)
    implementation(libs.androidx.runtime)
    implementation(libs.play.services.location)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    //dagger hilt
    implementation("com.google.dagger:hilt-android:2.56.2")
    kapt("com.google.dagger:hilt-android-compiler:2.56.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Coil (image loading)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Lottie animation
    implementation("com.airbnb.android:lottie-compose:4.2.0")

// Navigation
    implementation("androidx.navigation:navigation-compose:2.8.0")

// Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")


    // ViewModel + LiveData + Coroutines
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.runtime:runtime-livedata") // ✅ version BOM manage karega
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

// SplashScreen
    implementation("androidx.core:core-splashscreen:1.0.1")

// Bottom Bar
    implementation("com.canopas.compose-animated-navigationbar:bottombar:1.0.1")
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.31.1-alpha")

    implementation("io.coil-kt:coil:2.7.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("androidx.compose.material3:material3:1.2.1")




    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.19.1")


    implementation("com.googlecode.libphonenumber:libphonenumber:8.13.27")


    // Firebase BOM
    implementation(platform("com.google.firebase:firebase-bom:34.1.0"))

// Firebase Auth
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-analytics")

    //Lottie Animation
    implementation("com.airbnb.android:lottie-compose:6.0.0")


    // CameraX
    // CameraX (latest stable)
    implementation("androidx.camera:camera-core:1.3.0")
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")
    implementation("androidx.camera:camera-extensions:1.3.4") // optional (HDR, night mode etc.)



    implementation("androidx.activity:activity-compose:1.7.2")
    implementation("androidx.compose.ui:ui:1.5.0")


    implementation("com.google.ar:core:1.40.0")
    implementation("com.gorisse.thomas.sceneform:sceneform:1.21.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")











}