#!/usr/bin/env -S uv run --no-project --with pillow python3
import argparse
import gzip
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
from bisect import bisect_left
from concurrent.futures import ProcessPoolExecutor, ThreadPoolExecutor
from itertools import accumulate, batched, chain
from pathlib import Path
from urllib.parse import urlparse

from PIL import Image

SIZE, QUALITY, ALPHA_QUALITY, CHROMA = 256, 30, 40, "420"
EK_DATA = Path(os.environ.get("EK_DATA") or Path.home() / ".cache/emoji-kitchen")
PNG, AVIF, OUT = EK_DATA / "png", EK_DATA / f"avif{SIZE}q{QUALITY}a{ALPHA_QUALITY}y{CHROMA}", EK_DATA / "out"
METADATA_URL = "https://raw.githubusercontent.com/xsalazar/emoji-kitchen-backend/main/app/metadata.json"
PAIRS_URL = "https://www.google.com/logos/fnbx/emoji_kitchen/emoji_kitchen_pairs.13.pb"
GSTATIC_URL = "https://www.gstatic.com/android/keyboard/emojikitchen/{0}/{1}/{1}_{2}.png"
GOOGLE_DATES = ("20240612", "20241004", "20241113", "20250728", "20260428", "20260728")
NOTO_URL = "https://raw.githubusercontent.com/googlefonts/noto-emoji/main/png/512/emoji_u{}.png"
C = 7
MAGIC, VERSION = 0x4B504B45, 2
HEADER = struct.Struct("<IHH11I12x")
RETRIES = 3
BATCH = 4096


def retry(request):
    for attempt in range(RETRIES + 1):
        try:
            return request()
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return False
            if attempt == RETRIES:
                raise
        except (urllib.error.URLError, OSError):
            if attempt == RETRIES:
                raise
        time.sleep(2 ** attempt)


def fetch(urls, dest):
    if dest.exists():
        return True
    dest.parent.mkdir(parents=True, exist_ok=True)
    part = dest.with_name(dest.name + ".part")

    def get(url):
        req = urllib.request.Request(url, headers={"Accept-Encoding": "gzip"})
        with urllib.request.urlopen(req, timeout=60) as r, open(part, "wb") as f:
            shutil.copyfileobj(gzip.GzipFile(fileobj=r) if r.headers.get("Content-Encoding") == "gzip" else r, f)
        os.replace(part, dest)
        return True

    return any(retry(lambda: get(url)) for url in urls)


def head(url):
    def request():
        with urllib.request.urlopen(urllib.request.Request(url, method="HEAD"), timeout=60):
            return True

    return retry(request)


def download(url):
    dest = EK_DATA / url.rsplit("/", 1)[1]
    fetch([url], dest)
    return dest.read_bytes()


def encode(src, dest):
    png = io.BytesIO()
    Image.open(src).convert("RGBA").resize((SIZE, SIZE), Image.LANCZOS).save(png, "PNG", compress_level=0)
    dest.parent.mkdir(parents=True, exist_ok=True)
    part = dest.with_suffix(".part.avif")
    subprocess.run(["avifenc", "--stdin", "--input-format", "png", "-s", "2", "-j", "1", "-q", str(QUALITY),
                    "--qalpha", str(ALPHA_QUALITY), "-y", CHROMA, str(part)],
                   input=png.getvalue(), check=True, capture_output=True)
    os.replace(part, dest)
    return dest


def build_images(jobs):
    def job(rel, urls):
        png, avif = PNG / rel, AVIF / f"{rel}.avif"
        if avif.exists():
            return avif
        return encode(png, avif) if fetch(urls, png) else None

    start, out = time.monotonic(), []
    with ThreadPoolExecutor(os.cpu_count()) as pool:
        for batch in batched(jobs, BATCH):
            out += pool.map(lambda j: job(*j), batch)
            print(f"images {len(out)}/{len(jobs)} {time.monotonic() - start:.0f}s")
    return out


def noto_urls(cp):
    fmt = lambda parts: NOTO_URL.format("_".join(p.lower().zfill(4) for p in parts))
    parts = cp.split("-")
    stripped = [p for p in parts if p.lower() != "fe0f"]
    return [fmt(stripped)] + ([fmt(parts)] if stripped != parts else [])


def varint(b, i):
    n = s = 0
    while b[i] & 0x80:
        n |= (b[i] & 0x7F) << s
        i, s = i + 1, s + 7
    return n | b[i] << s, i + 1


def proto(b):
    fields, i = {}, 0
    while i < len(b):
        key, i = varint(b, i)
        if key & 7 == 2:
            n, i = varint(b, i)
            v, i = b[i:i + n], i + n
        else:
            v, i = varint(b, i)
        fields.setdefault(key >> 3, []).append(v)
    return fields


def packed(b):
    out, i = [], 0
    while i < len(b):
        v, i = varint(b, i)
        out.append(v)
    return out


def gstatic(a, b, dates):
    u = lambda cp: "-".join("u" + x for x in cp.split("-"))
    orders = dict.fromkeys(((u(a), u(b)), (u(b), u(a))))
    return (GSTATIC_URL.format(d, x, y) for d in dates for x, y in orders)


def probe(pairs, dates):
    path = EK_DATA / "resolved-urls.json"
    cache = json.loads(path.read_bytes()) if path.exists() else {}
    keys = ["_".join(p) for p in pairs]
    todo = [(key, p) for key, p in zip(keys, pairs) if key not in cache]
    if todo:
        with ThreadPoolExecutor(32) as pool:
            found = list(pool.map(lambda p: next(filter(head, gstatic(*p, dates)), None), (p for _, p in todo)))
        cache.update(zip((key for key, _ in todo), found))
        tmp = path.with_name(path.name + ".tmp")
        tmp.write_text(json.dumps(cache, indent=0, sort_keys=True))
        os.replace(tmp, path)
        print(f"probed {len(todo)} pairs: {len(found) - found.count(None)} found")
    return [cache[key] for key in keys]


def dataset_urls():
    data = json.loads(download(METADATA_URL))["data"]
    return {frozenset((cp, other)): c["gStaticUrl"] for cp, v in data.items()
            for other, combos in v["combinations"].items() for c in combos if c["isLatest"]}


def load(k):
    with ProcessPoolExecutor(1) as pool:
        dataset = pool.submit(dataset_urls).result()
    pb = proto(download(PAIRS_URL))
    emojis = [m[1][0].decode() for m in sorted(map(proto, pb[1]), key=lambda m: m[2][0])]
    starts = [m[2][0] - 1 for m in map(proto, pb[3]) if m[1][0] != b"People"] + [len(emojis)]
    keep = range(len(emojis)) if not k or k >= len(emojis) else [i * len(emojis) // k for i in range(k)]
    ids = {g + 1: i for i, g in enumerate(keep)}
    pairs = set()
    for m in map(proto, pb[2]):
        if m[1][0] in ids:
            a = ids[m[1][0]]
            pairs.update((min(a, ids[p]), max(a, ids[p])) for p in packed(m[2][0]) if p in ids)
    pairs = sorted(pairs)
    emojis = [emojis[g] for g in keep]
    order = ["-".join(f"{ord(c):x}" for c in e) for e in emojis]
    dates = sorted({url.split("/")[-3] for url in dataset.values()}.union(GOOGLE_DATES), reverse=True)
    names = [frozenset((order[a], order[b])) for a, b in pairs]
    urls = [[dataset[s]] if s in dataset else [] for s in names]
    todo = [i for i, u in enumerate(urls) if not u]
    for i, url in zip(todo, probe([(order[pairs[i][0]], order[pairs[i][1]]) for i in todo], dates)):
        if url:
            urls[i].append(url)
    unresolved = [(order[pairs[i][0]], order[pairs[i][1]]) for i in todo if not urls[i]]
    chosen, kept = set(order), set(names)
    removed = sorted(tuple(sorted(s)) for s in dataset if s <= chosen and s not in kept)
    print(f"resolved: dataset={sum(s in dataset for s in names)} "
          f"probed={len(todo) - len(unresolved)}/{len(todo)} unresolved={len(unresolved)} {unresolved}")
    print(f"removed {len(removed)} dataset pairs not in Google's set: {removed}")
    resolved = [((a, b), (urlparse(u[0]).path.lstrip("/"), chain(u, gstatic(order[a], order[b], dates))))
                for (a, b), u in zip(pairs, urls) if u]
    return order, emojis, [bisect_left(keep, s) for s in starts], resolved


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
        with tempfile.NamedTemporaryFile(suffix=".avif", dir=OUT) as tmp:
            for i in random.sample(range(n + p), min(20, n + p)):
                tmp.seek(0)
                tmp.truncate()
                tmp.write(m[starts[i]:starts[i + 1]])
                tmp.flush()
                subprocess.run(["avifdec", "--info", tmp.name], check=True, capture_output=True)
        print(f"verified {path}: N={n} P={p} A={a} items={n + p} total={len(m) / 2 ** 20:.1f} MB "
              f"avg={(starts[-1] - starts[0]) / (n + p) / 1024:.1f} KB/item")


def main():
    sys.stdout.reconfigure(line_buffering=True)
    ap = argparse.ArgumentParser()
    ap.add_argument("--emoji", type=int, metavar="K")
    ap.add_argument("--out", metavar="NAME")
    args = ap.parse_args()
    out = OUT / (args.out or ("kitchen-mini.pak" if args.emoji else "kitchen.pak"))
    OUT.mkdir(parents=True, exist_ok=True)
    order, emojis, cat_start, pairs = load(args.emoji)
    print(f"emoji={len(order)} pairs={len(pairs)} catStart={cat_start}")
    jobs = [(f"noto/{cp}.png", noto_urls(cp)) for cp in order]
    jobs += [job for _, job in pairs]
    images = build_images(jobs)
    missing = [cp for cp, i in zip(order, images) if not i]
    if missing:
        sys.exit(f"no Noto image for: {missing}")
    kept = [(pair, i) for pair, i in zip(pairs, images[len(order):]) if i]
    if len(kept) != len(pairs):
        print(f"dropped {len(pairs) - len(kept)} combos with no reachable image")
    a = write_pack(out, emojis, cat_start, [pair for pair, _ in kept], images[:len(order)] + [i for _, i in kept])
    verify(out, emojis, len(kept), a)


if __name__ == "__main__":
    main()
