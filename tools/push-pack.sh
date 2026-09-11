#!/usr/bin/env bash
set -euo pipefail
EK_DATA="${EK_DATA:-$HOME/.cache/emoji-kitchen}"
pack="$EK_DATA/out/${1:-kitchen.pak}"
dir=/sdcard/Android/data/com.kunalan.emojikitchen/files
[[ -f $pack ]] || { echo "pack not found: $pack" >&2; exit 1; }
lockf -k "$EK_DATA/adb.lock" adb shell mkdir -p "$dir"
lockf -k "$EK_DATA/adb.lock" adb push "$pack" "$dir/kitchen.pak"
