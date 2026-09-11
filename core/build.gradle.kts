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
    implementation(libs.collection)
    testImplementation(libs.junit)
}

val miniPack = providers.environmentVariable("EK_DATA")
    .orElse(providers.systemProperty("user.home").map { "$it/.cache/emoji-kitchen" })
    .map { "$it/out/kitchen-mini.pak" }

tasks.withType<Test>().configureEach {
    inputs.file(miniPack).optional().withPathSensitivity(PathSensitivity.NONE).withPropertyName("miniPack")
    systemProperty("ek.pack", miniPack.get())
}
