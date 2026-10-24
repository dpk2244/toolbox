plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val admobAppId: String = project.findProperty("ADMOB_APP_ID") as String? ?: "ca-app-pub-3940256099942544~3347511713"
val admobBannerId: String = project.findProperty("ADMOB_BANNER_ID") as String? ?: "ca-app-pub-3940256099942544/6300978111"
val admobInterstitialId: String = project.findProperty("ADMOB_INTERSTITIAL_ID") as String? ?: "ca-app-pub-3940256099942544/1033173712"

android {
    namespace = "com.pushpendratech.toolbox"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.pushpendratech.toolbox" // TODO: change to your own unique package id before Play upload
        minSdk = 26 // Android 8.0 — covers MediaCodec/PdfRenderer APIs used, ~95%+ of active devices in India
        targetSdk = 35
        versionCode = 9
        versionName = "1.2.5"
        manifestPlaceholders["ADMOB_APP_ID"] = admobAppId
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerId\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$admobInterstitialId\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signing is intentionally NOT hard-coded. Create keystore.properties (see README) for release signing.
        }
        debug { isMinifyEnabled = false }
    }
    // Optional release signing from keystore.properties (file is git-ignored, never commit it)
    val ksFile = rootProject.file("keystore.properties")
    if (ksFile.exists()) {
        val props = java.util.Properties().apply { ksFile.inputStream().use { load(it) } }
        signingConfigs.create("release") {
            storeFile = rootProject.file(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom); androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui"); implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1") // favorites + recents only. No user files stored.
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // PDF: PDFBox-Android (Apache-2.0) — merge/split/rotate/reorder/delete/extract/watermark/page-numbers/encryption.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // QR & barcode generation (QR & Barcode Generator tool): ZXing core (Apache-2.0).
    implementation("com.google.zxing:core:3.5.3")
    // Video re-encode (compress / resolution): AndroidX Media3 Transformer (Apache-2.0, Google).
    // Deliberately NOT FFmpeg: avoids LGPL/GPL binary + licensing burden and ~25MB+ native libs.
    implementation("androidx.media3:media3-transformer:1.5.1")
    implementation("androidx.media3:media3-effect:1.5.1")
    implementation("androidx.media3:media3-common:1.5.1")
    implementation("androidx.media3:media3-muxer:1.5.1")
    // Ads: Google Mobile Ads SDK (test IDs by default) + UMP consent.
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    implementation("com.google.android.ump:user-messaging-platform:3.0.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
