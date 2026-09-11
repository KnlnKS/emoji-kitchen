plugins {
    alias(libs.plugins.android.asset.pack)
}

assetPack {
    packName = "combopack"
    dynamicDelivery {
        deliveryType = "install-time"
    }
}

val pack = providers.environmentVariable("EK_DATA")
    .orElse(providers.systemProperty("user.home").map { "$it/.cache/emoji-kitchen" })
    .zip(providers.gradleProperty("ek.pack").orElse("kitchen.pak")) { data, name -> File("$data/out/$name") }
    .map { file -> file.also { check(it.isFile) { "Missing asset pack $it. Generate it with tools/pack.py or pass -Pek.pack=kitchen-mini.pak." } } }

val syncPack = tasks.register<Sync>("syncPack") {
    from(pack)
    rename { "kitchen.pak" }
    into(layout.buildDirectory.dir("combopack/src/main/assets"))
}

configurations.named("packElements") {
    outgoing.artifacts.clear()
    outgoing.artifact(syncPack.map { it.destinationDir })
}
