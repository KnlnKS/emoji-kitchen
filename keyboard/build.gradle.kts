plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kunalan.emojikitchen.keyboard"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":ui"))
    implementation(libs.lifecycle.runtime)
    implementation(libs.savedstate)
}
