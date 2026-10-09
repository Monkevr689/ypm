#!/usr/bin/env python3
"""Draws the eight Bliss gem textures and builds SMPSuite's resource pack.

The gems are vanilla items with a custom_model_data string; the pack only
adds a "select" case to those items' definitions, so without the pack (or
with other packs) everything falls back to the normal vanilla look.

Writes src/main/resources/SMPSuite-pack.zip (inside the jar, for its hash)
and release/SMPSuite-pack-<version>.zip (what url: auto downloads) - the
same bytes. A released versioned zip must never change afterwards.
"""
import io
import json
import math
import os
import re
import shutil
import zipfile

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(HERE)
VERSION = re.search(r"<artifactId>SMPSuite</artifactId>\s*<version>([^<]+)</version>",
                    open(os.path.join(PROJECT, "pom.xml"), encoding="utf-8").read()).group(1)
JAR_ZIP = os.path.join(PROJECT, "src", "main", "resources", "SMPSuite-pack.zip")
RELEASE_ZIP = os.path.join(PROJECT, "release", f"SMPSuite-pack-{VERSION}.zip")
PREVIEW = os.path.join(PROJECT, "docs", "gems_preview.png")

S = 32  # texture size

# gem id -> (vanilla base item, colour, outline polygon on a 32x32 grid)
def star(n, r1, r2, rot=-90):
    pts = []
    for i in range(n * 2):
        r = r1 if i % 2 == 0 else r2
        a = math.radians(rot + i * 180 / n)
        pts.append((16 + r * math.cos(a), 16 + r * math.sin(a)))
    return pts


def ring(n, r, rot=-90, sx=1.0, sy=1.0):
    return [(16 + r * sx * math.cos(math.radians(rot + i * 360 / n)),
             16 + r * sy * math.sin(math.radians(rot + i * 360 / n))) for i in range(n)]


GEMS = {
    "astra": ("amethyst_shard", (0xB5, 0x7C, 0xFF), star(4, 14.5, 6.0)),
    "fire": ("blaze_powder", (0xFF, 0x7A, 0x2E),
             [(16, 1.5), (21, 8), (25.5, 15), (25, 23), (21, 28.5), (16, 30), (11, 28.5), (7, 23), (6.5, 15), (11, 8)]),
    "flux": ("prismarine_crystals", (0x3A, 0xE8, 0xE0), [(16, 1.5), (26.5, 9), (26.5, 23), (16, 30.5), (5.5, 23), (5.5, 9)]),
    "life": ("glistering_melon_slice", (0xFF, 0x6A, 0xB0),
             [(16, 8), (19, 4.5), (23.5, 3.5), (27.5, 6), (28.5, 11), (26.5, 17), (16, 29), (5.5, 17), (3.5, 11),
              (4.5, 6), (8.5, 3.5), (13, 4.5)]),
    "puff": ("feather", (0xE6, 0xEC, 0xFF), ring(12, 13, rot=-75, sy=0.85)),
    "speed": ("sugar", (0xFF, 0xE0, 0x4A), [(16, 1.5), (27.5, 16), (16, 30.5), (4.5, 16)]),
    "strength": ("redstone", (0xE8, 0x3A, 0x3A),
                 [(9.5, 3.5), (22.5, 3.5), (28.5, 9.5), (28.5, 22.5), (22.5, 28.5), (9.5, 28.5), (3.5, 22.5), (3.5, 9.5)]),
    "wealth": ("emerald", (0x3A, 0xD8, 0x6A),
               [(11, 2.5), (21, 2.5), (25.5, 7), (25.5, 25), (21, 29.5), (11, 29.5), (6.5, 25), (6.5, 7)]),
}


def shade(rgb, f):
    return tuple(max(0, min(255, int(c * f))) for c in rgb) + (255,)


def gem_texture(rgb, poly):
    """A faceted gem: triangles from the centre, lit from the top left, a lighter table and sparkles."""
    big = 8
    img = Image.new("RGBA", (S * big, S * big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx = sum(p[0] for p in poly) / len(poly)
    cy = sum(p[1] for p in poly) / len(poly)
    light = (-0.6, -0.8)
    pts = [(x * big, y * big) for x, y in poly]
    n = len(pts)
    for i in range(n):
        a, b = poly[i], poly[(i + 1) % n]
        mx, my = (a[0] + b[0]) / 2 - cx, (a[1] + b[1]) / 2 - cy
        ln = math.hypot(mx, my) or 1
        lit = (mx / ln) * light[0] + (my / ln) * light[1]  # -1 .. 1
        d.polygon([(cx * big, cy * big), pts[i], pts[(i + 1) % n]], fill=shade(rgb, 0.92 + 0.38 * lit))
    # the table (top facet): the outline shrunk towards the centre
    table = [(cx * big + (x - cx * big) * 0.45, cy * big + (y - cy * big) * 0.45) for x, y in pts]
    d.polygon(table, fill=shade(rgb, 1.22))
    for i in range(n):
        d.line([table[i], pts[i]], fill=shade(rgb, 0.78), width=big // 2)
    d.polygon(pts, outline=shade(rgb, 0.45), width=big)
    img = img.resize((S, S), Image.LANCZOS)
    # crisp edges: no half-transparent pixels in a pixel-art item
    px = img.load()
    for y in range(S):
        for x in range(S):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, 255) if a >= 110 else (0, 0, 0, 0)
    # sparkles (top left of the table)
    sx, sy = int(cx - 4), int(cy - 5)
    for dx, dy, al in ((0, 0, 255), (1, 0, 200), (0, 1, 200), (-1, 0, 120), (0, -1, 120), (3, 2, 160)):
        x, y = sx + dx, sy + dy
        if 0 <= x < S and 0 <= y < S and px[x, y][3] > 0:
            r, g, b, _ = px[x, y]
            k = al / 255
            px[x, y] = (int(r + (255 - r) * k), int(g + (255 - g) * k), int(b + (255 - b) * k), 255)
    return img


def png_bytes(img):
    b = io.BytesIO()
    img.save(b, "PNG", optimize=True)
    return b.getvalue()


def build():
    files = {}
    textures = {}
    for gem, (base, rgb, poly) in GEMS.items():
        tex = gem_texture(rgb, poly)
        textures[gem] = tex
        files[f"assets/smp/textures/item/gem_{gem}.png"] = png_bytes(tex)
        files[f"assets/smp/models/item/gem_{gem}.json"] = json.dumps(
            {"parent": "minecraft:item/generated", "textures": {"layer0": f"smp:item/gem_{gem}"}}, indent=1)
        files[f"assets/minecraft/items/{base}.json"] = json.dumps({"model": {
            "type": "minecraft:select",
            "property": "minecraft:custom_model_data",
            "cases": [{"when": f"smp_gem_{gem}", "model": {"type": "minecraft:model", "model": f"smp:item/gem_{gem}"}}],
            "fallback": {"type": "minecraft:model", "model": f"minecraft:item/{base}"},
        }}, indent=1)
    files["pack.mcmeta"] = json.dumps({"pack": {
        "description": "§bSMPSuite §7gem textures",
        "min_format": 97,
        "max_format": 120,
    }}, indent=1)
    # pack icon: the eight gems on a dark tile
    icon = Image.new("RGBA", (128, 128), (24, 22, 34, 255))
    for i, gem in enumerate(GEMS):
        t = textures[gem].resize((32 * 1, 32 * 1), Image.NEAREST)
        icon.alpha_composite(t, (16 + (i % 3) * 34 if i < 6 else 33 + (i - 6) * 34, 10 + (i // 3) * 38 if i < 6 else 86))
    files["pack.png"] = png_bytes(icon)
    return files, textures


def write_zip(path, files):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        for name in sorted(files):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            data = files[name]
            z.writestr(info, data.encode("utf-8") if isinstance(data, str) else data)


def preview(textures):
    scale = 4
    img = Image.new("RGBA", (len(textures) * (S * scale + 12) + 12, S * scale + 24), (30, 30, 38, 255))
    for i, gem in enumerate(textures):
        img.alpha_composite(textures[gem].resize((S * scale, S * scale), Image.NEAREST), (12 + i * (S * scale + 12), 12))
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
    img.save(PREVIEW)


if __name__ == "__main__":
    files, textures = build()
    if os.path.exists(RELEASE_ZIP):
        # a released pack must never change: rebuild into the jar only if it is identical
        tmp = RELEASE_ZIP + ".new"
        write_zip(tmp, files)
        same = open(tmp, "rb").read() == open(RELEASE_ZIP, "rb").read()
        os.remove(tmp)
        if not same:
            raise SystemExit(f"{RELEASE_ZIP} is released and would change - bump the version in pom.xml first")
    else:
        write_zip(RELEASE_ZIP, files)
    shutil.copyfile(RELEASE_ZIP, JAR_ZIP)
    preview(textures)
    print(f"wrote {RELEASE_ZIP} and the jar copy ({len(files)} files, {os.path.getsize(RELEASE_ZIP) // 1024} KB)")
