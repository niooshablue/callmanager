import java.util.Properties
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val ks = Properties().apply { rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
android {
    namespace = "ir.callmanager"
    compileSdk = 34
    defaultConfig {
        applicationId = "ir.callmanager"
        minSdk = 31
        targetSdk = 34
        versionCode = 3
        versionName = "0.3"
    }
    signingConfigs {
        if (ks.isNotEmpty()) create("release") {
            storeFile = rootProject.file(ks["storeFile"] as String)
            storePassword = ks["storePassword"] as String
            keyAlias = ks["keyAlias"] as String
            keyPassword = ks["keyPassword"] as String
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
