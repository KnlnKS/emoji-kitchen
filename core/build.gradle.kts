plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.kunalan.emojikitchen.core"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.runtime)
    api(libs.core.ktx)
    api(libs.coroutines.android)
    testImplementation(libs.junit)
}
