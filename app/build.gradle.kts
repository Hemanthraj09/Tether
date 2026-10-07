plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.tether.app"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.tether.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Crash reports only from release builds (see AndroidManifest meta-data).
        manifestPlaceholders["crashlyticsEnabled"] = false
    }

    // CI (GitHub Actions) signs releases with a keystore passed via secrets.
    // Locally, without those variables, release builds use the debug key.
    val ciKeystore = System.getenv("TETHER_KEYSTORE_PATH")
    signingConfigs {
        if (ciKeystore != null) {
            create("ci") {
                storeFile = file(ciKeystore)
                storePassword = System.getenv("TETHER_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TETHER_KEY_ALIAS")
                keyPassword = System.getenv("TETHER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 shrinking/optimization: smaller, noticeably faster APK than debug.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // The debug key's SHA-1 is registered in Firebase, so Google Sign-In
            // works for sideloaded builds. Use a dedicated keystore for Play.
            signingConfig = signingConfigs.getByName(if (ciKeystore != null) "ci" else "debug")
            manifestPlaceholders["crashlyticsEnabled"] = true
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
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation("com.google.android.material:material:1.12.0")
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Navigation Component
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // RecyclerView & CardView
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")

    testImplementation(libs.junit)
    // Android's org.json is a stub in JVM unit tests; use the real implementation there.
    testImplementation("org.json:json:20260814")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-crashlytics")
    // LeetCode query hot-fixes + kill switch without shipping a new APK (free on Spark)
    implementation("com.google.firebase:firebase-config")

    // Background LeetCode sync
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Applies the libraries' baseline profiles at install time (also for
    // sideloaded APKs) → faster startup and smoother scrolling.
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
}