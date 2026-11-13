plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firebase (pushberichten) wordt pas actief zodra android/app/google-services.json aanwezig is.
// Zolang dat bestand er niet staat, bouwt de app gewoon zonder Firebase-config (de testversie).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "org.verco.telefoon"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.verco.telefoon"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("BUILD_NUMBER") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.2.0"
    }

    signingConfigs {
        // Testsleutel (staat bewust in de repository): elke testversie heeft dezelfde handtekening,
        // zodat een nieuwe APK over de vorige heen te installeren is.
        create("test") {
            storeFile = rootProject.file("verco-test.keystore")
            storePassword = "verco-test"
            keyAlias = "verco-test"
            keyPassword = "verco-test"
        }
        // Upload-sleutel voor Google Play. Deze sleutel staat NIET in de repository; de gegevens
        // komen uit omgevingsvariabelen (GitHub Actions-secrets). Zonder die variabelen valt de
        // release-build terug op de testsleutel hierboven, zodat de gewone testbuild blijft werken.
        create("upload") {
            val ksFile = System.getenv("UPLOAD_KEYSTORE_FILE")
            if (ksFile != null) {
                storeFile = file(ksFile)
                storePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (System.getenv("UPLOAD_KEYSTORE_FILE") != null)
                signingConfigs.getByName("upload")
            else
                signingConfigs.getByName("test")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { jniLibs { useLegacyPackaging = true } }
}

dependencies {
    implementation("org.linphone:linphone-sdk-android:5.5.39")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("androidx.fragment:fragment-ktx:1.8.5")   // Firebase brengt een te oude versie mee
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
}
