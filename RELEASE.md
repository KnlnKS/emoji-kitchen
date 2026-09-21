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

## CI releases

Every code push to `main` and manual CI run on `main` runs `.github/workflows/ci.yml`. After unit tests pass, it builds a signed `bundleRelease` with the full pack, publishes a signed universal APK as GitHub release `v1.0.<run number>`, and submits the same version to Google Play's production track. Google still controls review and publication timing. Pull requests only run the unit tests and build a debug APK. Documentation-only pushes do not trigger a build.

The `play-release` Actions artifact retains the signed AAB and R8 mapping for 14 days. Signing credentials are removed after the build. A failed Play submission fails the workflow; it is never silently skipped.

The packs are never committed, so CI downloads them from the `pack` pre-release. After rebuilding them, replace its assets:

```
gh release upload pack $EK_DATA/out/kitchen.pak $EK_DATA/out/kitchen-mini.pak --clobber
```

CI signs both the AAB and APK with the upload key, read from four repository secrets:

```
base64 < ~/keys/emoji-kitchen-upload.jks | gh secret set KEYSTORE_BASE64
gh secret set KEYSTORE_PASSWORD
gh secret set KEY_ALIAS --body upload
gh secret set KEY_PASSWORD
```

A run on `main` fails in its first step if any of them is missing. GitHub APKs are signed with the upload key rather than Play's app signing key, so a GitHub install and a Play install can't update each other.

## Google Play setup

Create `com.kunalan.emojikitchen` in Play Console and complete its store listing, app content declarations, country availability, and first release. Upload the signed AAB from the `play-release` artifact manually for the first release; the publishing API requires an existing app. Use the full pack and enroll in Play App Signing.

Enable the Google Play Android Developer API in a dedicated Google Cloud project. Create a publishing service account without Google Cloud project roles. In Play Console, grant it access only to Emoji Kitchen, with app information viewing and production release permissions. Save its JSON key directly as the `PLAY_SERVICE_ACCOUNT_JSON` repository secret; never commit it:

```
gh secret set PLAY_SERVICE_ACCOUNT_JSON < /secure/path/play-service-account.json
```

Once the first release is ready and the secret is configured, subsequent `main` builds automatically upload the AAB and mapping, complete the production rollout, and send the changes for review. Managed publishing must be off for approved updates to publish automatically. If Google requires testing or further declarations, complete those requirements in Play Console first.

To retry a failed upload before Play accepts the version, rerun only the failed publishing job. After Play accepts a version code, start a new workflow run for a new build number instead of rerunning the build with that same code.
