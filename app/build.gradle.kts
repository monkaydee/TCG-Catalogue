plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.monkaydee.tcgcatalogue"
    testOptions { unitTests { isIncludeAndroidResources = true } }
    // Lists the app's languages for Android 13+'s per-app language setting.
    androidResources { generateLocaleConfig = true }
    compileSdk = 35

    defaultConfig {
        applicationId = "com.monkaydee.tcgcatalogue"
        minSdk = 26
        targetSdk = 35
        // The Play Store build (play.yml) passes VERSION_CODE; every other build uses the Actions run number.
        val build = System.getenv("VERSION_CODE")?.trim()?.takeIf { it.isNotEmpty() } ?: System.getenv("GITHUB_RUN_NUMBER")
        versionCode = (build ?: "1").toInt()
        versionName = "0.1.${build ?: "0"}"
        // The app's price server (docs/CLOUDFLARE.md), from repository secrets in CI builds. The app
        // key is not a real secret (anyone can read it from the APK); it only keeps casual callers out.
        fun env(name: String) = System.getenv(name).orEmpty().trim().filter { it.isLetterOrDigit() || it in ":/._-~" }
        buildConfigField("String", "PRICE_SERVER_URL", "\"${env("PRICE_SERVER_URL")}\"")
        buildConfigField("String", "PRICE_SERVER_KEY", "\"${env("PRICE_SERVER_KEY")}\"")
        // CardNavo Pro (docs/PRO-TIER.md): only a build made with PRO_PAYWALL=true (the Play Store build) puts
        // the Pro tools behind the purchase. Every other build, including the GitHub APKs, leaves them open.
        buildConfigField("boolean", "PRO_PAYWALL", (System.getenv("PRO_PAYWALL")?.trim() == "true").toString())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // A fixed key checked into the repo so every CI build can be installed
        // over the previous one without losing the local collection.
        getByName("debug") {
            storeFile = file("signing/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // The Play Store upload key, from CI secrets only (docs/PRO-TIER.md). Never in the repository.
        System.getenv("UPLOAD_KEYSTORE")?.takeIf { it.isNotBlank() }?.let { path ->
            create("upload") {
                storeFile = file(path)
                storePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            // Phones only; drops the emulator (x86) native libraries that ML Kit ships. Debug keeps
            // them, so the recognition test (golden set) can run on an emulator in CI.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.text)
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")
    // Slab labels: Google code scanner (barcode/QR, Play services UI; no camera permission of our own).
    implementation(libs.code.scanner)
    // CardNavo Pro: the one-time purchase through Google Play. The Java artifact: billing-ktx 9 needs a newer Kotlin.
    implementation(libs.billing)
    // On-device image model for "find by picture" (the model itself is downloaded on first use).
    implementation(libs.litert)

    implementation(libs.work.runtime)
    implementation(libs.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    testImplementation(libs.junit)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    // Screenshot tests: screens rendered on the JVM (Robolectric native graphics), saved as PNGs.
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.robolectric)
}
