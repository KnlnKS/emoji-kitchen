# Emoji Kitchen

Android clone of Google Search's Emoji Kitchen widget, plus an Emoji Kitchen–only keyboard (IME). Browsing is offline; sharing fetches full-quality artwork from GitHub when online.

## Rules
- Absolutely no code comments.
- Follow DRY and YAGNI, and write the most performant code you can.
- Add tests only when absolutely necessary.
- Make focused commits with accurate authorship.

## Environment
Worktrees have no `local.properties`, so export these before running Gradle:
```
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=$HOME/Library/Android/sdk
export EK_DATA=${EK_DATA:-$HOME/.cache/emoji-kitchen}
```
- **Build:** `./gradlew :app:assembleDebug`
- **Device:** `ANDROID_SERIAL=emulator-5580`.
  - Wrap every adb command in `lockf -k "$EK_DATA/adb.lock" …`.
  - Reinstall with `adb install -r`. Never uninstall: it deletes the pushed pack.
- **Data:** generated packs live in `$EK_DATA` and are never committed. Lossless sharing artwork is committed on the separate `artwork` branch.
  - Build packs with `tools/pack.py [--emoji 70]`, which needs `uv` and Homebrew `libavif`. `$EK_DATA/out/kitchen.pak` is the full pack; `kitchen-mini.pak` holds 70 emoji spread across every section.
  - Push a pack with `tools/push-pack.sh [kitchen-mini.pak]`.
  - Export original-resolution artwork with `tools/artwork.py --pack "$EK_DATA/out/kitchen.pak" --out /path/to/artwork-checkout`. Publish it before updating `ARTWORK_REVISION`; the download URL and cache directory share that revision.

## Modules
| Module | Contents |
|---|---|
| `:core` | Pack reader (`Kitchen`), `Images`, `ComboList` (favorites and recents), `Share`. No UI. |
| `:ui` | `theme/Tokens.kt` (Google widget tokens, `KitchenFont`), `LocalKitchen`, `KitchenImage`, `EmojiCell`, `ComboCell`, `KitchenIcon(s)`, `KitchenRipple`, and the sectioned-grid primitives (`Sections`, `sectioned`, `AtLeast`, `Header`) |
| `:app` | `MainActivity` and the widget-clone screen |
| `:keyboard` | `KitchenIme` and the keyboard UI |
| `:combopack` | Install-time Play Asset Delivery pack that ships `kitchen.pak` |
| `tools/` | `pack.py` pipeline, `push-pack.sh` |

## Contracts
- **Emoji ids** `0..N-1` follow Google's widget: emoji, order, sections and pairs come from Google's `emoji_kitchen_pairs.13.pb`, which `tools/pack.py` downloads. The 7 sections are contiguous, and `Kitchen.categoryStart` has `C+1` entries.
- **Item ids:**
  - `0..N-1` are the base emoji images: item `e` is emoji `e`.
  - `N..N+P-1` are combos, sorted by (low, high).
- **Partner lists:** `partner(e,k)` and `partnerItem(e,k)` are ordered by partner id ascending, which is Google's order within sections. `item(a,b)` returns -1 when the pair doesn't exist.
- **`Combo`** packs a pair as `low shl 16 or high`.
- **Pack images:** items are raw AVIF `mdat` payloads (alpha, then color). The pack carries one container template and a per-item alpha length, and `PackIndex.read` rebuilds the AVIF in a thread-local buffer.
- **Shared images:** paths use Unicode code points, not pack item IDs. Pair paths sort the two code point strings lexicographically. Cached originals take priority; bundled fallbacks never prevent a later online upgrade. Both app and IME use `pngUri`.
- **Favorites and recents:** `Kitchen.favorites` and `Kitchen.recents` are snapshot-state lists shared by the app and the IME, which run in the same process.
- **Compose:** components read `Kitchen` from `LocalKitchen` and colors from `KitchenTheme.colors`. Grid callbacks take `(Int) -> Unit`, so no lambda is allocated per cell.
