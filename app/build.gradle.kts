import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystore = providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties")).asText.orNull
    ?.let { text -> Properties().apply { load(text.reader()) } }
val buildNumber = providers.environmentVariable("BUILD_NUMBER").map(String::toInt).getOrElse(1)

android {
    namespace = "com.kunalan.emojikitchen"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "com.kunalan.emojikitchen"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = keystore?.let { props ->
                signingConfigs.create("release") {
                    storeFile = rootProject.file(props.getProperty("storeFile"))
                    storePassword = props.getProperty("storePassword")
                    keyAlias = props.getProperty("keyAlias")
                    keyPassword = props.getProperty("keyPassword")
                }
            }
        }
    }
    androidResources {
        noCompress += "pak"
    }
    assetPacks += ":combopack"
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":ui"))
    implementation(project(":keyboard"))
    implementation(libs.activity.compose)
    implementation(libs.profileinstaller)
}
