<div align="center">

<img src="play/icon-512.png" width="160" alt="">

# Emoji Kitchen

**Google's Emoji Kitchen, offline, with a keyboard to match.**

[![CI](https://github.com/KnlnKS/emoji-kitchen/actions/workflows/ci.yml/badge.svg)](https://github.com/KnlnKS/emoji-kitchen/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/KnlnKS/emoji-kitchen?display_name=tag&sort=semver)](https://github.com/KnlnKS/emoji-kitchen/releases/latest)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Android 9.0+](https://img.shields.io/badge/android-9.0%2B-3ddc84.svg)](#install)

</div>

---

An Android clone of the Emoji Kitchen widget in Google Search, plus a keyboard that only does Emoji Kitchen. Pick two emoji and see what Google's artists made of them, or browse every combination one emoji has.

All 621 emoji and more than 151,000 combinations ship inside the app for fast, offline browsing. Copying or sending a sticker downloads its original-resolution artwork from this repository when an internet connection is available, then caches it for reuse. Offline, the app uses a cached original or the bundled image.

## Features

- **Combine.** Pick two emoji and get the sticker. Roll the dice on either side for a partner that has a combination, or randomize both.
- **Explore.** Every combination for one emoji in a single grid. Tap one to load it into Combine.
- **Same emoji, same order.** Emoji, sections and pairs come from the file the Search widget itself uses, so what you find here matches Google.
- **Copy and favorite.** Copy a full-quality sticker to paste anywhere, or star it. Long-press any combination to star it from a grid.
- **A keyboard for stickers.** Turn on the Emoji Kitchen keyboard, pick an emoji, and tap a combination to send it. Apps that accept images get the sticker directly; anything else gets it on the clipboard.
- **Favorites and recents everywhere.** The app and the keyboard share them live.
- **One-handed reach.** The top of each page opens into a gap that brings the grid down under your thumb, and snaps open or shut.
- **Light and dark.** Follows your system theme.

## Install

Grab the APK from the [latest release](https://github.com/KnlnKS/emoji-kitchen/releases/latest). Requires **Android 9.0** (API 28) or newer.

Releases are cut automatically from `main`, so the latest one is whatever landed most recently.

To use the keyboard, tap the keyboard button at the top right of the app and turn on Emoji Kitchen.

<details>
<summary><b>Why is the download so big?</b></summary>

Every sticker is in the APK for offline use. That's over 151,000 images, each re-encoded as a 256 px AVIF and packed into a single file of about 500 MB. The app memory-maps the index and reads images on demand without unpacking the pack. Original-resolution artwork is downloaded only for stickers you copy or send.
</details>

## Building

You'll need **JDK 21** and the Android SDK. Building the emoji pack also needs [uv](https://docs.astral.sh/uv/) and [libavif](https://github.com/AOMediaCodec/libavif) (`brew install uv libavif`).

```sh
git clone --single-branch https://github.com/KnlnKS/emoji-kitchen.git
cd emoji-kitchen

tools/pack.py --emoji 70              # kitchen-mini.pak: 70 emoji spread across every section
tools/pack.py                         # kitchen.pak: everything, which takes hours
./gradlew testDebugUnitTest           # unit tests, run against the mini pack when it exists
./gradlew :app:assembleDebug          # debug APK, without the pack
tools/push-pack.sh kitchen-mini.pak   # put a pack on the connected device for debug builds
```

Packs land in `$EK_DATA/out`, which defaults to `~/.cache/emoji-kitchen/out`. Packs are generated and never committed. The separate `artwork` branch contains the full-quality images; `--single-branch` avoids downloading them when working on the app.

Debug APKs don't carry the pack, so push one to the device with `tools/push-pack.sh`. Release builds ship it inside the app. `./gradlew :app:bundleRelease` needs the full pack; pass `-Pek.pack=kitchen-mini.pak` for a quick local build. [RELEASE.md](RELEASE.md) covers signing, bundletool testing and the CI release.

## How it works

`tools/pack.py` downloads Google's own pair list, `emoji_kitchen_pairs.13.pb`, which is where the emoji, their order, their sections and every valid pair come from. Base emoji come from Noto Emoji and combinations from Google's sticker CDN. Everything is re-encoded as 256 px AVIF and written into one indexed file, `kitchen.pak`.

The pack ships as an install-time Play Asset Delivery pack, stored uncompressed so the app can memory-map its index. Pair lookups and partner lists read straight from the mapped index, and images are decoded on demand with libavif, then handed to the render thread ahead of drawing.

Sharing uses the [artwork branch](https://github.com/KnlnKS/emoji-kitchen/tree/artwork): lossless WebP exports of the original PNGs, preserving their resolution and transparency. The app downloads a single image over HTTPS from a pinned commit and converts it to PNG for clipboard and keyboard compatibility. Failed downloads fall back to the bundled image; a later online copy still upgrades to the original. Downloads and encoding run off the main thread, and simultaneous shares reuse the same cache entry.

To rebuild the artwork from a shipped pack:

```sh
git fetch origin artwork:artwork
git worktree add ../emoji-kitchen-artwork artwork
tools/artwork.py --pack "$EK_DATA/out/kitchen.pak" --out ../emoji-kitchen-artwork
```

The exporter resolves every item in that pack, compares every WebP export pixel-for-pixel with its source, and records source URLs in `sources.json`. It resumes interrupted runs. Publish the artwork before updating the pinned URL and cache version in `core/src/main/kotlin/com/kunalan/emojikitchen/core/Share.kt`.

The app and the keyboard are both Jetpack Compose and run in the same process, which is how they share favorites and recents live.

## Credits

- **[Emoji Kitchen](https://emojikitchen.dev)** is Google's. The combination artwork belongs to Google.
- **[Noto Emoji](https://github.com/googlefonts/noto-emoji)** supplies the base emoji, under the [Apache License 2.0](https://github.com/googlefonts/noto-emoji/blob/main/LICENSE).
- **[emoji-kitchen-backend](https://github.com/xsalazar/emoji-kitchen-backend)** provides the emoji metadata the pack build starts from.
- **[libavif](https://github.com/AOMediaCodec/libavif)** encodes the pack and decodes it on the phone, under the [BSD 2-Clause License](https://github.com/AOMediaCodec/libavif/blob/main/LICENSE).
- **[Google Sans Flex](https://fonts.google.com/specimen/Google+Sans+Flex)** is the UI font, under the [SIL Open Font License](ui/src/main/assets/licenses/google_sans_flex_OFL.txt).

## License

[MIT](LICENSE) © Kunalan Kevin Subagaran. This covers the code; the emoji artwork belongs to its owners.
