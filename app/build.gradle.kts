plugins {
    alias(libs.plugins.android.application)
       // không version — inherit từ Kotlin đã có trên classpath
}

android {
    namespace = "com.uilover.project278"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.uilover.project278"
        minSdk = 26
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

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // kotlinOptions block BỎ HOÀN TOÀN — AGP 9.x tự handle
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // ── Playback ──────────────────────────────────────────────────────────────
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")

    // ── Image Loading — dùng 4.16.0 + kapt (KHÔNG dùng 5.0.5 + annotationProcessor)
    implementation("com.github.bumptech.glide:glide:4.16.0")
      // Phải dùng kapt, không phải annotationProcessor
    implementation("jp.wasabeef:glide-transformations:4.3.0")

    // ── Palette — gradient động từ album art ──────────────────────────────────
    implementation("androidx.palette:palette-ktx:1.0.0")

    // ── Waveform SeekBar — massoudss only (frolo removed: API conflict) ──────
    implementation("com.github.massoudss:waveformSeekBar:5.0.2")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}