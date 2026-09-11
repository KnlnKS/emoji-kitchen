# Release

## Upload key

Play App Signing keeps the app signing key. You only sign uploads with an upload key, which Play can reset if it's lost.

```
keytool -genkeypair -v -keystore ~/keys/emoji-kitchen-upload.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

Create `keystore.properties` at the repo root. It's gitignored, so never commit it:

```
storeFile=/Users/you/keys/emoji-kitchen-upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

Without this file, release builds come out unsigned. That's fine for local bundletool testing, but Play won't accept them. Back up the keystore and passwords somewhere other than this machine.

## Build

`bundleRelease` ships `$EK_DATA/out/kitchen.pak` in the install-time `combopack` asset pack and fails if the file is missing.

```
./gradlew :app:bundleRelease
```

The output is `app/build/outputs/bundle/release/app-release.aab`. On first upload, Play Console enrolls the app in Play App Signing. The store icon is `play/icon-512.png`.

For quick local builds, pass `-Pek.pack=kitchen-mini.pak`. Never upload those.

## Local testing with bundletool

Download `bundletool-all-*.jar` from https://github.com/google/bundletool/releases to `$EK_DATA/bundletool.jar`.

```
java -jar $EK_DATA/bundletool.jar build-apks --local-testing \
  --bundle app/build/outputs/bundle/release/app-release.aab --output $EK_DATA/app.apks
java -jar $EK_DATA/bundletool.jar install-apks --apks $EK_DATA/app.apks
```

Without `--ks`, bundletool signs with `~/.android/debug.keystore`, so this updates a debug install in place. Don't uninstall first, because that deletes the pushed debug pack. To confirm the pack is memory-mappable, check that it's stored uncompressed:

```
adb pull $(adb shell pm path com.kunalan.emojikitchen | grep combopack | cut -d: -f2) combopack.apk
unzip -v combopack.apk | grep kitchen.pak
```

The `unzip -v` output must show `Stored`.
