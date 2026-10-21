#!/usr/bin/env -S uv run --no-project --with pillow --with httpx python3
import argparse
import hashlib
import io
import json
import os
import struct
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from itertools import batched, chain
from pathlib import Path

import httpx
from PIL import Image

from pack import EK_DATA, GOOGLE_DATES, dataset_urls, gstatic, noto_urls


def catalog(path):
    with path.open("rb") as f:
        magic, version, _, n, p, count, _, blob, span, _, adj, partner, *_ = struct.unpack("<IHH13I4x", f.read(64))
        if (magic, version) != (0x4B504B45, 3):
            raise ValueError("Not a version 3 kitchen pack")

        def read(offset, code, count):
            f.seek(offset)
            return struct.unpack(f"<{count}{code}", f.read(struct.calcsize(code) * count))

        spans = read(span, "i", n + 1)
        f.seek(blob)
        strings = f.read(spans[-1])
        emojis = ["-".join(f"{ord(c):x}" for c in strings[spans[i]:spans[i + 1]].decode()) for i in range(n)]
        starts, partners = read(adj, "i", n + 1), read(partner, "H", count)
        pairs = [(emojis[a], emojis[b]) for a in range(n) for b in partners[starts[a]:starts[a + 1]] if a <= b]
        assert len(pairs) == p
        return emojis, pairs


def main():
    sys.stdout.reconfigure(line_buffering=True)
    ap = argparse.ArgumentParser(description="Export pixel-exact, full-resolution artwork for a shipped pack.")
    ap.add_argument("--pack", type=Path, default=EK_DATA / "out/kitchen.pak")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--workers", type=int, default=32)
    args = ap.parse_args()
    emojis, pairs = catalog(args.pack)
    dataset = dataset_urls()
    dates = sorted({url.split("/")[-3] for url in dataset.values()}.union(GOOGLE_DATES), reverse=True)
    jobs = [(f"emoji/{cp}.webp", noto_urls(cp)) for cp in emojis]
    for a, b in pairs:
        lo, hi = sorted((a, b))
        source = dataset.get(frozenset((a, b)))
        jobs.append((f"combos/{lo}/{hi}.webp", chain([source] if source else [], gstatic(a, b, dates))))
    args.out.mkdir(parents=True, exist_ok=True)
    manifest = args.out / "sources.json"
    sources = json.loads(manifest.read_text()) if manifest.exists() else {}
    started, failures = time.monotonic(), []

    with httpx.Client(timeout=20, follow_redirects=True, limits=httpx.Limits(max_connections=args.workers)) as client:
        def export(job):
            name, urls = job
            dest = args.out / name
            if name in sources and dest.is_file():
                return name, sources[name]
            for url in urls:
                for attempt in range(4):
                    try:
                        response = client.get(url)
                        if response.status_code == 404:
                            break
                        response.raise_for_status()
                        original = Image.open(io.BytesIO(response.content)).convert("RGBA")
                        encoded = io.BytesIO()
                        original.save(encoded, "WEBP", lossless=True, exact=True, method=4)
                        assert Image.open(io.BytesIO(encoded.getvalue())).convert("RGBA").tobytes() == original.tobytes()
                        dest.parent.mkdir(parents=True, exist_ok=True)
                        part = dest.with_suffix(".tmp")
                        part.write_bytes(encoded.getvalue())
                        os.replace(part, dest)
                        return name, url
                    except (httpx.HTTPError, OSError) as error:
                        if attempt == 3:
                            return name, str(error)
                        time.sleep(2 ** attempt)
            return name, "No source artwork found"

        with ThreadPoolExecutor(args.workers) as pool:
            for batch in batched(jobs, 512):
                for name, source in pool.map(export, batch):
                    if source.startswith("https://"):
                        sources[name] = source
                    else:
                        failures.append((name, source))
                        print(f"FAILED {name}: {source}")
                part = manifest.with_suffix(".tmp")
                part.write_text(json.dumps(sources, sort_keys=True, indent=2) + "\n")
                os.replace(part, manifest)
                print(f"artwork {len(sources)}/{len(jobs)} ({time.monotonic() - started:.0f}s)")

    if failures:
        sys.exit(f"{len(failures)} images failed; rerun to resume")
    assert set(sources) == {name for name, _ in jobs}
    with args.pack.open("rb") as f:
        digest = hashlib.file_digest(f, "sha256").hexdigest()
    (args.out / "README.md").write_text(
        "# Emoji Kitchen artwork\n\n"
        f"{len(emojis):,} base emoji and {len(pairs):,} combinations, stored as lossless WebP at their original resolution. "
        "Every export is decoded and compared pixel-for-pixel with its source, including transparency. "
        "No resizing, upscaling, or lossy compression is applied.\n\n"
        "`sources.json` records the original URL for every image. Base emoji are from "
        "[Noto Emoji](https://github.com/googlefonts/noto-emoji) under the "
        "[Apache License 2.0](https://github.com/googlefonts/noto-emoji/blob/main/LICENSE). "
        "Combination artwork belongs to Google and is not covered by this project's MIT code license.\n\n"
        "Paths use lowercase Unicode code points separated by hyphens. Combination paths sort the two code point "
        "strings lexicographically: `combos/<first>/<second>.webp`; base emoji use `emoji/<codepoints>.webp`. "
        "These names are independent of item IDs in full and mini packs.\n\n"
        "Rebuild with `tools/artwork.py --pack /path/to/kitchen.pak --out /path/to/artwork-checkout` "
        "from the source branch. Interrupted runs resume from the manifest.\n\n"
        f"Source pack SHA-256: `{digest}`.\n"
    )


if __name__ == "__main__":
    main()
