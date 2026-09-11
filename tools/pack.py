#!/usr/bin/env -S uv run --no-project --with pillow python3
import argparse
import gzip
import html
import io
import json
import mmap
import os
import random
import shutil
import struct
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from array import array
from concurrent.futures import ThreadPoolExecutor
from itertools import accumulate
from pathlib import Path
from urllib.parse import urlparse

from PIL import Image

SIZE, QUALITY, ALPHA_QUALITY = 256, 30, 55
EK_DATA = Path(os.environ.get("EK_DATA") or Path.home() / ".cache/emoji-kitchen")
PNG, AVIF, OUT = EK_DATA / "png", EK_DATA / f"avif{SIZE}q{QUALITY}a{ALPHA_QUALITY}", EK_DATA / "out"
METADATA_URL = "https://raw.githubusercontent.com/xsalazar/emoji-kitchen-backend/main/app/metadata.json"
NOTO_URL = "https://raw.githubusercontent.com/googlefonts/noto-emoji/main/png/512/emoji_u{}.png"
AVIFENC = shutil.which("avifenc") or "/opt/homebrew/bin/avifenc"
AVIFDEC = shutil.which("avifdec")
ORDER = Path(__file__).with_name("google-order.txt")
SECTIONS = {"smileys & emotion": 0, "people & body": 0, "animals & nature": 1, "food & drink": 2,
            "travel & places": 3, "activities": 4, "objects": 5}
C = 7
MAGIC, VERSION = 0x4B504B45, 2
HEADER = struct.Struct("<IHH11I12x")
RETRIES = 3


def fetch(urls, dest):
    if dest.exists():
        return True
    dest.parent.mkdir(parents=True, exist_ok=True)
    part = dest.with_name(dest.name + ".part")
    for url in urls:
        for attempt in range(RETRIES + 1):
            try:
                req = urllib.request.Request(url, headers={"Accept-Encoding": "gzip"})
                with urllib.request.urlopen(req, timeout=60) as r, open(part, "wb") as f:
                    shutil.copyfileobj(gzip.GzipFile(fileobj=r) if r.headers.get("Content-Encoding") == "gzip" else r, f)
                os.replace(part, dest)
                return True
            except urllib.error.HTTPError as e:
                if e.code == 404:
                    break
                if attempt == RETRIES:
                    raise
            except (urllib.error.URLError, OSError):
                if attempt == RETRIES:
                    raise
            time.sleep(2 ** attempt)
    return False


def encode(src, dest):
    if not dest.exists():
        png = io.BytesIO()
        Image.open(src).convert("RGBA").resize((SIZE, SIZE), Image.LANCZOS).save(png, "PNG", compress_level=0)
        part = dest.with_suffix(".part.avif")
        subprocess.run([AVIFENC, "--stdin", "--input-format", "png", "-s", "2", "-j", "1", "-q", str(QUALITY),
                        "--qalpha", str(ALPHA_QUALITY), "-y", "444", str(part)],
                       input=png.getvalue(), check=True, capture_output=True)
        os.replace(part, dest)
    return dest


def build_images(jobs):
    def job(rel, urls):
        png, avif = PNG / rel, AVIF / f"{rel}.avif"
        avif.parent.mkdir(parents=True, exist_ok=True)
        if avif.exists() or fetch(urls, png):
            return enc.submit(encode, png, avif)
        return None

    start, out = time.monotonic(), []
    with ThreadPoolExecutor(os.cpu_count()) as enc, ThreadPoolExecutor(32) as dl:
        futures = [dl.submit(job, rel, urls) for rel, urls in jobs]
        for i, f in enumerate(futures, 1):
            e = f.result()
            out.append(e and e.result())
            if i % 10000 == 0 or i == len(futures):
                print(f"images {i}/{len(futures)} {time.monotonic() - start:.0f}s")
    return out


def noto_urls(cp):
    fmt = lambda parts: NOTO_URL.format("_".join(p.lower().zfill(4) for p in parts))
    parts = cp.split("-")
    stripped = [p for p in parts if p.lower() != "fe0f"]
    return [fmt(stripped)] + ([fmt(parts)] if stripped != parts else [])


def norm(cp):
    return "-".join(p for p in (f"{int(x, 16):x}" for x in cp.split("-")) if p != "fe0f")


def load(k):
    fetch([METADATA_URL], EK_DATA / "metadata.json")
    meta = json.loads((EK_DATA / "metadata.json").read_bytes())
    data, known = meta["data"], meta["knownSupportedEmoji"]
    google = {norm(cp): (s, 0, i) for s, line in enumerate(ORDER.read_text().splitlines())
              for i, cp in enumerate(line.split())}
    rank = {cp: google.get(norm(cp)) or (SECTIONS.get(html.unescape(data[cp].get("category") or ""), 6), 1,
                                         data[cp]["gBoardOrder"]) for cp in known}
    fallback = [cp for cp in known if rank[cp][1]]
    skipped = sorted(google.keys() - {norm(cp) for cp in known}, key=google.get)
    print(f"google order: matched={len(known) - len(fallback)} fallback={len(fallback)} {fallback} "
          f"skipped={len(skipped)} {skipped}")
    order = sorted(known, key=lambda cp: (rank[cp], cp))
    if k and k < len(order):
        order = [order[i * len(order) // k] for i in range(k)]
    ids = {cp: i for i, cp in enumerate(order)}
    pairs = {}
    for cp in order:
        for other, combos in data[cp]["combinations"].items():
            if other in ids:
                url = next((c["gStaticUrl"] for c in combos if c["isLatest"]), None)
                if url:
                    pairs.setdefault(tuple(sorted((ids[cp], ids[other]))), url)
    cat_start = [0] * (C + 1)
    for cp in order:
        cat_start[rank[cp][0] + 1] += 1
    return order, data, list(accumulate(cat_start)), sorted(pairs.items())


def align8(n):
    return (n + 7) & ~7


def write_pack(path, emojis, cat_start, pairs, images):
    assert sys.byteorder == "little"
    n, p = len(emojis), len(pairs)
    rows = [[] for _ in range(n)]
    for item, ((lo, hi), _) in enumerate(pairs, n):
        rows[lo].append((hi, item))
        if hi != lo:
            rows[hi].append((lo, item))
    blob = "".join(emojis).encode()
    span = array("i", accumulate((len(e.encode()) for e in emojis), initial=0))
    adj_start = array("i", accumulate((len(r) for r in rows), initial=0))
    partner = array("H", (b for r in rows for b, _ in r))
    adj_item = array("i", (i for r in rows for _, i in r))
    sections = [blob, span, array("i", cat_start), adj_start, partner, adj_item]
    offsets, off = [], HEADER.size
    for s in sections:
        off = align8(off)
        offsets.append(off)
        off += len(s) * (s.itemsize if isinstance(s, array) else 1)
    offsets.append(align8(off))
    sizes = [os.path.getsize(i) for i in images]
    item_start = array("q", accumulate(sizes, initial=offsets[-1] + 8 * (len(sizes) + 1)))
    assert item_start[-1] < 2 ** 31
    sections.append(item_start)
    tmp = path.with_name(path.name + ".tmp")
    with open(tmp, "wb", buffering=1 << 20) as f:
        f.write(HEADER.pack(MAGIC, VERSION, 0, n, p, len(partner), C, *offsets))
        for off, s in zip(offsets, sections):
            f.write(bytes(off - f.tell()))
            f.write(s)
        for i in images:
            f.write(i.read_bytes())
        assert f.tell() == item_start[-1]
    os.replace(tmp, path)
    return len(partner)


def verify(path, emojis, p, a):
    n = len(emojis)
    with open(path, "rb") as f, mmap.mmap(f.fileno(), 0, access=mmap.ACCESS_READ) as m:
        magic, version, zero, hn, hp, ha, hc, *offs = HEADER.unpack_from(m)
        assert (magic, version, zero, hn, hp, ha, hc) == (MAGIC, VERSION, 0, n, p, a, C)
        assert all(o % 8 == 0 for o in offs) and m[52:64] == bytes(12)
        read = lambda code, off, count: array(code, m[off:off + count * array(code).itemsize])
        s_off, span_off, cat_off, adj_off, partner_off, item_off, start_off = offs
        span = read("i", span_off, n + 1)
        assert [m[s_off + span[e]:s_off + span[e + 1]].decode() for e in range(n)] == emojis
        cat = read("i", cat_off, C + 1)
        assert cat[0] == 0 and cat[C] == n and all(x <= y for x, y in zip(cat, cat[1:]))
        adj_start, partner, adj_item = read("i", adj_off, n + 1), read("H", partner_off, a), read("i", item_off, a)
        assert adj_start[0] == 0 and adj_start[n] == a
        edges = {}
        for e in range(n):
            row = range(adj_start[e], adj_start[e + 1])
            assert all(partner[j] < partner[j + 1] for j in row[:-1])
            edges.update(((e, partner[j]), adj_item[j]) for j in row)
        assert all(edges.get((b, e)) == item for (e, b), item in edges.items())
        assert [item for (e, b), item in sorted(edges.items()) if e <= b] == list(range(n, n + p))
        starts = read("q", start_off, n + p + 1)
        assert starts[0] == start_off + 8 * (n + p + 1) and starts[-1] == len(m)
        for s in starts[:-1]:
            assert m[s + 4:s + 12] == b"ftypavif"
        if AVIFDEC:
            with tempfile.NamedTemporaryFile(suffix=".avif", dir=OUT) as tmp:
                for i in random.sample(range(n + p), min(20, n + p)):
                    tmp.seek(0)
                    tmp.truncate()
                    tmp.write(m[starts[i]:starts[i + 1]])
                    tmp.flush()
                    subprocess.run([AVIFDEC, "--info", tmp.name], check=True, capture_output=True)
        print(f"verified {path}: N={n} P={p} A={a} items={n + p} total={len(m) / 2 ** 20:.1f} MB "
              f"avg={(starts[-1] - starts[0]) / (n + p) / 1024:.1f} KB/item avifdec={'20 ok' if AVIFDEC else 'skipped'}")


def main():
    sys.stdout.reconfigure(line_buffering=True)
    ap = argparse.ArgumentParser()
    ap.add_argument("--emoji", type=int, metavar="K")
    ap.add_argument("--out", metavar="NAME")
    args = ap.parse_args()
    out = OUT / (args.out or ("kitchen-mini.pak" if args.emoji else "kitchen.pak"))
    OUT.mkdir(parents=True, exist_ok=True)
    order, data, cat_start, pairs = load(args.emoji)
    print(f"emoji={len(order)} pairs={len(pairs)} catStart={cat_start}")
    jobs = [(f"noto/{cp}.png", noto_urls(cp)) for cp in order]
    jobs += [(urlparse(url).path.lstrip("/"), [url]) for _, url in pairs]
    images = build_images(jobs)
    missing = [cp for cp, i in zip(order, images) if not i]
    if missing:
        sys.exit(f"no Noto image for: {missing}")
    kept = [(pair, i) for pair, i in zip(pairs, images[len(order):]) if i]
    if len(kept) != len(pairs):
        print(f"dropped {len(pairs) - len(kept)} combos with 404 gStaticUrl")
    emojis = [data[cp]["emoji"] for cp in order]
    a = write_pack(out, emojis, cat_start, [pair for pair, _ in kept], images[:len(order)] + [i for _, i in kept])
    verify(out, emojis, len(kept), a)


if __name__ == "__main__":
    main()
