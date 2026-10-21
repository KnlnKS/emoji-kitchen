# Emoji Kitchen artwork

621 base emoji and 151,129 combinations, stored as lossless WebP at their original resolution. Every export is decoded and compared pixel-for-pixel with its source, including transparency. No resizing, upscaling, or lossy compression is applied.

`sources.json` records the original URL for every image. Base emoji are from [Noto Emoji](https://github.com/googlefonts/noto-emoji) under the [Apache License 2.0](https://github.com/googlefonts/noto-emoji/blob/main/LICENSE). Combination artwork belongs to Google and is not covered by this project's MIT code license.

Paths use lowercase Unicode code points separated by hyphens. Combination paths sort the two code point strings lexicographically: `combos/<first>/<second>.webp`; base emoji use `emoji/<codepoints>.webp`. These names are independent of item IDs in full and mini packs.

Rebuild with `tools/artwork.py --pack /path/to/kitchen.pak --out /path/to/artwork-checkout` from the source branch. Interrupted runs resume from the manifest.

Source pack SHA-256: `cef21a1a192cac1f7ba1ba895cdf96f36d0f85111bd4dc86e4afdb16d05984cd`.
