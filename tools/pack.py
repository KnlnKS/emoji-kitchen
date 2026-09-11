#!/usr/bin/env python3
import argparse
import gzip
import html
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
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from itertools import accumulate
from pathlib import Path
from urllib.parse import urlparse

EK_DATA = Path(os.environ.get("EK_DATA") or Path.home() / ".cache/emoji-kitchen")
PNG, WEBP, OUT = EK_DATA / "png", EK_DATA / "webp", EK_DATA / "out"
METADATA_URL = "https://raw.githubusercontent.com/xsalazar/emoji-kitchen-backend/main/app/metadata.json"
NOTO_URL = "https://raw.githubusercontent.com/googlefonts/noto-emoji/main/png/512/emoji_u{}.png"
CWEBP = shutil.which("cwebp") or "/opt/homebrew/bin/cwebp"
DWEBP = shutil.which("dwebp")
SECTION_NAMES = ["Smileys and emotions", "Animals and nature", "Food and drink", "Travel and places",
                 "Activities and events", "Objects", "Symbols"]
SECTIONS = {"smileys & emotion": 0, "people & body": 0, "animals & nature": 1, "food & drink": 2,
            "travel & places": 3, "activities": 4, "objects": 5}
C = len(SECTION_NAMES)
MAGIC, VERSION = 0x4B504B45, 1
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
        part = dest.with_name(dest.name + ".part")
        subprocess.run([CWEBP, "-quiet", "-m", "6", "-q", "75", "-alpha_q", "80", "-resize", "256", "256",
                        str(src), "-o", str(part)], check=True)
        os.replace(part, dest)
    return dest


def build_images(jobs):
    def job(rel, urls):
        png, webp = PNG / rel, WEBP / f"{rel}.webp"
        webp.parent.mkdir(parents=True, exist_ok=True)
        if webp.exists() or fetch(urls, png):
            return enc.submit(encode, png, webp)
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


def load(k):
    fetch([METADATA_URL], EK_DATA / "metadata.json")
    meta = json.loads((EK_DATA / "metadata.json").read_bytes())
    data, known = meta["data"], meta["knownSupportedEmoji"]
    cats = {cp: data[cp].get("category") or "" for cp in known}
    section = {cp: SECTIONS.get(html.unescape(c), 6) for cp, c in cats.items()}
    print("category histogram:")
    for cat, n in Counter(cats.values()).most_common():
        print(f"  {n:4d}  {cat!r} -> {SECTION_NAMES[SECTIONS.get(html.unescape(cat), 6)]}")
    order = sorted(known, key=lambda cp: (section[cp], data[cp]["gBoardOrder"], cp))
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
        cat_start[section[cp] + 1] += 1
    return order, data, list(accumulate(cat_start)), sorted(pairs.items())


def align8(n):
    return (n + 7) & ~7


def write_pack(path, emojis, cat_start, pairs, webps):
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
    sizes = [os.path.getsize(w) for w in webps]
    item_start = array("q", accumulate(sizes, initial=offsets[-1] + 8 * (len(sizes) + 1)))
    assert item_start[-1] < 2 ** 31
    sections.append(item_start)
    tmp = path.with_name(path.name + ".tmp")
    with open(tmp, "wb", buffering=1 << 20) as f:
        f.write(HEADER.pack(MAGIC, VERSION, 0, n, p, len(partner), C, *offsets))
        for off, s in zip(offsets, sections):
            f.write(bytes(off - f.tell()))
            f.write(s)
        for w in webps:
            f.write(w.read_bytes())
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
        for s, t in zip(starts, starts[1:]):
            assert m[s:s + 4] == b"RIFF" and m[s + 8:s + 12] == b"WEBP" and struct.unpack_from("<I", m, s + 4)[0] + 8 == t - s
        if DWEBP:
            with tempfile.NamedTemporaryFile(suffix=".webp", dir=OUT) as tmp:
                for i in random.sample(range(n + p), min(20, n + p)):
                    tmp.seek(0)
                    tmp.truncate()
                    tmp.write(m[starts[i]:starts[i + 1]])
                    tmp.flush()
                    subprocess.run([DWEBP, "-quiet", tmp.name, "-o", "/dev/null"], check=True)
        print(f"verified {path}: N={n} P={p} A={a} items={n + p} total={len(m) / 2 ** 20:.1f} MB "
          f"avg={(starts[-1] - starts[0]) / (n + p) / 1024:.1f} KB/item dwebp={'20 ok' if DWEBP else 'skipped'}")


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
    webps = build_images(jobs)
    missing = [cp for cp, w in zip(order, webps) if not w]
    if missing:
        sys.exit(f"no Noto image for: {missing}")
    kept = [(pair, w) for pair, w in zip(pairs, webps[len(order):]) if w]
    if len(kept) != len(pairs):
        print(f"dropped {len(pairs) - len(kept)} combos with 404 gStaticUrl")
    emojis = [data[cp]["emoji"] for cp in order]
    a = write_pack(out, emojis, cat_start, [pair for pair, _ in kept], webps[:len(order)] + [w for _, w in kept])
    verify(out, emojis, len(kept), a)


if __name__ == "__main__":
    main()
