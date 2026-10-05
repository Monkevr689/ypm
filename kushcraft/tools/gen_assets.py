#!/usr/bin/env python3
"""Generates every texture, model, item definition and font used by the
KushCraft resource pack.  Output goes to src/main/resources/pack/ and is
zipped and served by the plugin at runtime.

Run:  python3 tools/gen_assets.py          (needs Pillow)
"""
import json
import math
import os
import random
import shutil
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
from sprites import ITEMS, ICONS  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(HERE)
PACK = os.path.join(PROJECT, "src", "main", "resources", "pack")
NS = "kush"
ASSETS = os.path.join(PACK, "assets", NS)
PREVIEW_DIR = os.path.join(PROJECT, "docs")

WHITE = 0xFFFFFF
DEFAULT_TINT = 0x6ABE3A

random.seed(1337)


# ---------------------------------------------------------------------------
# helpers
# ---------------------------------------------------------------------------
def rgba(hexstr):
    hexstr = hexstr.lstrip("#")
    r, g, b = int(hexstr[0:2], 16), int(hexstr[2:4], 16), int(hexstr[4:6], 16)
    a = int(hexstr[6:8], 16) if len(hexstr) == 8 else 255
    return (r, g, b, a)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3] if len(c) > 3 else 255,)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def ensure(path):
    os.makedirs(os.path.dirname(path), exist_ok=True)


def save_png(img, rel):
    path = os.path.join(ASSETS, "textures", rel + ".png")
    ensure(path)
    img.save(path)
    return path


def save_json(obj, rel):
    path = os.path.join(ASSETS, rel)
    ensure(path)
    with open(path, "w") as f:
        json.dump(obj, f, indent=1)


def render_sprite(name, spr):
    art, pal = spr["art"], spr["pal"]
    if len(art) != 16 or any(len(r) != 16 for r in art):
        bad = [(i, len(r)) for i, r in enumerate(art) if len(r) != 16]
        raise SystemExit(f"sprite {name}: bad size rows={len(art)} {bad}")
    layers = [Image.new("RGBA", (16, 16), (0, 0, 0, 0)) for _ in range(3)]
    for y, row in enumerate(art):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if ch not in pal:
                raise SystemExit(f"sprite {name}: char {ch!r} not in palette")
            v = pal[ch]
            layer = 0
            if isinstance(v, tuple):
                v, layer = v
            layers[layer].putpixel((x, y), rgba(v))
    return layers


def nonempty(img):
    return img.getbbox() is not None


def item_definition(model, tints=None):
    m = {"type": "minecraft:model", "model": model}
    if tints:
        m["tints"] = tints
    return {"model": m}


def strain_tints(default=DEFAULT_TINT):
    return [
        {"type": "minecraft:constant", "value": WHITE},
        {"type": "minecraft:custom_model_data", "index": 0, "default": default},
    ]


GENERATED = {}  # name -> list of preview images (for the contact sheet)


def write_flat_item(name, layers, folder="item"):
    """layers: list of PIL images; layer index 1 is tinted."""
    tex = {}
    used = [l for l in layers if nonempty(l)]
    # keep layer order stable: 0, 1, 2 (drop trailing empties only)
    count = 0
    for i, l in enumerate(layers):
        if nonempty(l) or any(nonempty(x) for x in layers[i + 1:]):
            count = i + 1
    # textures must live under item/ or block/ - those are the only folders the
    # game stitches into its texture atlas (anything else renders magenta/black)
    tex_folder = "item" if folder == "item" else f"item/{folder}"
    for i in range(count):
        suffix = "" if i == 0 else ("_tint" if i == 1 else "_overlay")
        rel = f"{tex_folder}/{name}{suffix}"
        save_png(layers[i], rel)
        tex[f"layer{i}"] = f"{NS}:{rel}"
    save_json({"parent": "minecraft:item/generated", "textures": tex}, f"models/{folder}/{name}.json")
    tinted = count >= 2 and nonempty(layers[1])
    save_json(item_definition(f"{NS}:{folder}/{name}", strain_tints() if tinted else None), f"items/{name}.json")
    GENERATED[name] = (layers[:count], tinted)
    return used


# ---------------------------------------------------------------------------
# procedural item sprites
# ---------------------------------------------------------------------------
RAINBOW = ["ff3a3a", "ff9a2a", "ffe23a", "5ae84a", "3ac8f0", "6a5af0", "d84af0"]


def proc_lucid_tab():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(2, 14):
        for x in range(2, 14):
            edge = x in (2, 13) or y in (2, 13)
            if edge:
                img.putpixel((x, y), rgba("4a2a5a"))
                continue
            d = math.hypot(x - 7.5, y - 7.5)
            c = rgba(RAINBOW[int(d * 1.35) % len(RAINBOW)])
            if x == 8 or y == 8:  # perforation
                c = shade(c, 0.6) if (x + y) % 2 == 0 else (240, 240, 240, 255)
            img.putpixel((x, y), c)
    return [img, Image.new("RGBA", (16, 16)), Image.new("RGBA", (16, 16))]


def proc_trippy_icon():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = math.hypot(dx, dy)
            if d > 7.6:
                continue
            a = math.atan2(dy, dx)
            v = (d * 0.9 + a * 1.6) % len(RAINBOW)
            c = rgba(RAINBOW[int(v)])
            if d > 6.8:
                c = rgba("2a0a3a")
            img.putpixel((x, y), c)
    return [img]


# ---------------------------------------------------------------------------
# preview sheet
# ---------------------------------------------------------------------------
def composite(layers, tint=None):
    base = Image.new("RGBA", layers[0].size, (0, 0, 0, 0))
    for i, l in enumerate(layers):
        if i == 1 and tint is not None:
            r, g, b = (tint >> 16) & 255, (tint >> 8) & 255, tint & 255
            px = l.load()
            l = l.copy()
            p2 = l.load()
            for y in range(l.height):
                for x in range(l.width):
                    pr, pg, pb, pa = px[x, y]
                    p2[x, y] = (pr * r // 255, pg * g // 255, pb * b // 255, pa)
        base = Image.alpha_composite(base, l)
    return base


def contact_sheet(entries, path, scale=6, cols=10, bg=(44, 52, 40, 255)):
    cell = 16 * scale + 12
    rows = (len(entries) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell + 12, rows * cell + 12), bg)
    for i, img in enumerate(entries):
        x = 12 + (i % cols) * cell
        y = 12 + (i // cols) * cell
        big = img.resize((img.width * scale * 16 // img.width, img.height * scale * 16 // img.width), Image.NEAREST)
        sheet.alpha_composite(big, (x, y))
    ensure(path)
    sheet.save(path)


def main():
    if os.path.isdir(PACK):
        shutil.rmtree(PACK)
    os.makedirs(ASSETS, exist_ok=True)

    # flat items --------------------------------------------------------
    for name, spr in ITEMS.items():
        write_flat_item(name, render_sprite(name, spr))
    write_flat_item("lucid_tab", proc_lucid_tab())
    for name, spr in ICONS.items():
        write_flat_item(name, render_sprite(name, spr), folder="icon")
    write_flat_item("effect_trippy", proc_trippy_icon() + [Image.new("RGBA", (16, 16))] * 2, folder="icon")

    import plants
    import machines
    import gui
    import pack_meta
    import recipe_images
    plants.generate(sys.modules[__name__])
    machines.generate(sys.modules[__name__])
    gui.generate(sys.modules[__name__])
    recipe_images.generate(sys.modules[__name__])
    pack_meta.generate(sys.modules[__name__])

    # preview -----------------------------------------------------------
    previews = []
    for name, (layers, tinted) in GENERATED.items():
        if tinted:
            for t in (0x6abe3a, 0x9a4fd4):
                previews.append(composite(layers, t))
        else:
            previews.append(composite(layers))
    contact_sheet(previews, os.path.join(PREVIEW_DIR, "items_preview.png"))
    import render_models
    render_models.generate(sys.modules[__name__], [
        "block/lab_station", "block/strain_maker", "block/rolling_table", "block/drying_rack_fresh",
        "block/drying_rack_dry", "block/grow_lamp", "block/planter_box", "block/dealer"], "blocks_preview.png")
    render_models.generate(sys.modules[__name__], [
        "plant/sativa_2", "plant/sativa_4", "plant/indica_3", "plant/indica_4", "plant/hybrid_4",
        "plant/coca_3", "plant/poppy_2", "plant/poppy_3", "plant/mushroom_3"], "plants3d_preview.png", tint=0xb05ae0)
    print(f"generated {len(GENERATED)} flat items into {PACK}")


if __name__ == "__main__":
    main()
