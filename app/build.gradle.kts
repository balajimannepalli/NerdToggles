plugins {
    id("com.android.application") version "9.3.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
}

android {
    namespace = "com.nerdtoggles.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nerdtoggles.app"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }

    buildFeatures { compose = true }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3:1.4.0")
}
