plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kunalan.emojikitchen.ui"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    api(project(":core"))
    api(libs.compose.ui)
    api(libs.compose.foundation)
}
