plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.verco.telefoon"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.verco.telefoon"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("BUILD_NUMBER") ?: "1").toInt()
        versionName = "0.1.0"
    }

    signingConfigs {
        // Testsleutel (staat bewust in de repository): elke testversie heeft dezelfde handtekening,
        // zodat een nieuwe APK over de vorige heen te installeren is. Voor Google Play komt er een eigen sleutel.
        create("test") {
            storeFile = rootProject.file("verco-test.keystore")
            storePassword = "verco-test"
            keyAlias = "verco-test"
            keyPassword = "verco-test"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("test")
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
}
