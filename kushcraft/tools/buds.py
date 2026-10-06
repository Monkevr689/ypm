"""Strain-coloured buds and the animated Mythic looks.

Every strain picks one of four bud shapes (custom_model_data strings[0])
and three colours (custom_model_data colours): 0 = buds, 1 = leaves,
2 = hairs. The 32px sprites are drawn in greys on five layers so the game
can tint each part:

    layer0  plain (stem)                 untinted
    layer1  the bud (calyxes)            colour 0
    layer2  pistils / hairs              colour 2
    layer3  fan + sugar leaves           colour 1
    layer4  frost (trichomes)            untinted

Mythic strains (strings[1]) get an animated overlay on top of the bud:
rainbow, galaxy, golden, crystal, neon or inferno. The same overlays are
used on the plants (tools/plants.py).
"""
import json
import math
import os
import random
import re

from PIL import Image

from art import Sprite, c, alpha, grey, ramp, grey_ramp, poly, ellipse, rect, thick_line, edge, scale
import items32

G = None

SHAPES = ["classic", "foxtail", "popcorn", "spear"]
EXOTICS = ["rainbow", "galaxy", "golden", "crystal", "neon", "inferno"]
FRAMES = 16
FRAMETIME = 2

DEFAULT_BUD = 0x6ABE3A
DEFAULT_LEAF = 0x4E9E34
DEFAULT_PISTIL = 0xE8862E

STEM = "6a7a3a"


# ---------------------------------------------------------------------------
# drawing
# ---------------------------------------------------------------------------
def calyx(s, x, y, r, tones, frost):
    """One calyx bump on layer 1, a frost dot on layer 4."""
    dark, mid, light, hi = tones
    m = ellipse(x, y, r, r * 0.9)
    for (px, py) in m:
        dx, dy = (px + 0.5 - x) / r, (py + 0.5 - y) / r
        d = dx * -0.62 + dy * -0.78
        e = math.hypot(dx, dy)
        col = mid
        if d > 0.35 and e > 0.35:
            col = light
        if d < -0.25 and e > 0.55:
            col = dark
        s.put(px, py, col, 1)
    hx, hy = int(math.floor(x - r * 0.35)), int(math.floor(y - r * 0.4))
    s.put(hx, hy, hi, 1)
    if frost and s.rng.random() < frost:
        s.put(hx, hy, alpha(c("fbfff4"), 235), 4)
    return m


def sugar_leaf(s, x, y, ang, length, width=2.0, dry=False):
    a = math.radians(ang)
    dx, dy = math.cos(a), -math.sin(a)
    nx, ny = -dy, dx
    mid = (x + dx * length * 0.45, y + dy * length * 0.45)
    tip = (x + dx * length, y + dy * length)
    m = poly([(x, y), (mid[0] + nx * width, mid[1] + ny * width), tip, (mid[0] - nx * width, mid[1] - ny * width)])
    s.shade(m, grey_ramp(120, 236, 4) if not dry else grey_ramp(110, 210, 4), 3, dither=0.3)
    s.line(x, y, x + dx * length * 0.75, y + dy * length * 0.75, grey(110), 3)
    return m


def hairs(s, body, cx, count, dry):
    """Curly pistils from the edge of the bud (layer 2, greys)."""
    rng = s.rng
    hc, hd = (grey(244), grey(196)) if not dry else (grey(214), grey(170))
    outer = sorted(edge(body))
    for _ in range(count):
        (x, y) = rng.choice(outer)
        side = -1 if x < cx else 1
        path = [(0, 0), (side, -1), (side * 2, -1), (side * 2, -2)] if rng.random() < 0.6 else [(0, 0), (0, -1), (side, -2)]
        for k, (a, b) in enumerate(path):
            s.put(x + a, y + b, hc if k < 2 else hd, 2)
    # a few short hairs lying on top of the bud
    inner = sorted(body)
    for _ in range(count // 2):
        (x, y) = rng.choice(inner)
        s.put(x, y, hc, 2)
        s.put(x + rng.choice((-1, 1)), y - 1, hd, 2)


def tones(dry):
    return (grey(86), grey(172), grey(222), grey(252)) if not dry else (grey(74), grey(152), grey(204), grey(240))


def cluster(s, cells, dry, frost):
    body = set()
    for (x, y, r) in sorted(cells, key=lambda p: (p[1], -p[0])):
        body |= calyx(s, x, y, r, tones(dry), frost)
    return body


def teardrop(s, cx, top, bottom, width, dry, r=2.6, wobble=0.4):
    """Calyx cells filling a teardrop (classic cola)."""
    rng = s.rng
    h = bottom - top
    rows = max(3, int(round(h / (r * 1.45))))
    cells = []
    for i in range(rows):
        t = i / (rows - 1)
        w = width * (0.3 + 0.7 * math.sin(math.pi * min(1.0, 0.12 + t * 0.92)))
        n = max(1, int(round(w / (r * 1.7))))
        for j in range(n):
            ox = (j - (n - 1) / 2) * (w / n) + (0.9 if i % 2 else -0.4) + rng.uniform(-wobble, wobble)
            cells.append((cx + ox, top + r + t * (h - 2 * r), r + rng.uniform(-0.3, 0.5)))
    return cells


def fan_leaf(s, cx, cy, size, fingers=5):
    """Fan leaf in greys on layer 3 (tinted with the leaf colour)."""
    items32.cannabis_leaf(s, cx, cy, size, layer=3, fingers=fingers)


def stem(s, x0, y0, x1, y1, w=2.4):
    s.shade(thick_line(x0, y0, x1, y1, w), ramp(STEM, 3), 0)


def classic(s, dry):
    if not dry:
        stem(s, 16, 25, 15, 31)
        fan_leaf(s, 16, 25, 9)
        body = cluster(s, teardrop(s, 16, 3, 26, 14, dry), dry, 0.3)
        hairs(s, body, 16, 9, dry)
    else:
        for (side, t, ang) in ((-1, 0.62, 205), (1, 0.55, -25)):
            sugar_leaf(s, 16 + side * 3, 6 + 21 * t, ang + s.rng.uniform(-8, 8), 6.5, dry=True)
        body = cluster(s, teardrop(s, 16, 6, 27, 13, dry, r=2.4), dry, 0.5)
        hairs(s, body, 16, 6, dry)


def foxtail(s, dry):
    """A tall, uneven cola with little spires stacking up out of it."""
    rng = s.rng
    top, bottom = (2, 25) if not dry else (4, 27)
    if not dry:
        stem(s, 16, 24, 17, 31)
        fan_leaf(s, 16, 25, 8)
    cells = []
    # wavy main column
    for i in range(10):
        t = i / 9
        y = top + 3 + t * (bottom - top - 6)
        w = (4.5 if not dry else 4) * (0.55 + 0.45 * math.sin(math.pi * min(1, 0.2 + t * 0.85)))
        x = 16 + math.sin(t * 5.0) * 1.6
        for j in (-1, 0, 1):
            cells.append((x + j * w * 0.7 + rng.uniform(-0.4, 0.4), y + rng.uniform(-0.6, 0.6), 2.1 + rng.uniform(0, 0.5)))
    # spires: chains of calyxes reaching up and out
    for (bx, by, ang, n) in ((13, bottom - 8, 125, 4), (19, bottom - 12, 55, 4), (12, top + 9, 120, 3),
                             (20, top + 6, 60, 3)):
        a = math.radians(ang)
        for k in range(n):
            cells.append((bx + math.cos(a) * k * 2.3, by - math.sin(a) * k * 2.3, 2.0 - k * 0.2))
    body = cluster(s, cells, dry, 0.35 if not dry else 0.5)
    hairs(s, body, 16, 12 if not dry else 8, dry)
    if dry:
        sugar_leaf(s, 13, bottom - 3, 215, 6, dry=True)


def popcorn(s, dry):
    """A handful of small round nugs."""
    rng = s.rng
    nugs = [(10, 12, 4.8), (21, 10, 4.6), (16, 20, 5.6), (8, 23, 4.2), (24, 22, 4.4)]
    if dry:
        nugs = [(11, 13, 4.6), (21, 12, 4.4), (16, 21, 5.4), (9, 24, 3.8), (23, 24, 4.0)]
    else:
        stem(s, 16, 26, 16, 31, 2.0)
        for (x, y, ang) in ((6, 16, 160), (26, 15, 20)):
            sugar_leaf(s, x + (4 if ang > 90 else -4), y + 2, ang, 7)
    bodies = set()
    for (nx, ny, nr) in nugs:
        cells = []
        for k in range(9):
            a = rng.uniform(0, math.pi * 2)
            d = rng.uniform(0, nr - 1.8)
            cells.append((nx + math.cos(a) * d, ny + math.sin(a) * d * 0.9, 1.8 + rng.uniform(0, 0.5)))
        cells.append((nx, ny, 2.2))
        bodies |= cluster(s, cells, dry, 0.4 if not dry else 0.55)
    hairs(s, bodies, 16, 12 if not dry else 8, dry)


def spear(s, dry):
    """A long, narrow cola ending in a sharp point."""
    rng = s.rng
    top, bottom = (1, 27) if not dry else (3, 28)
    if not dry:
        stem(s, 16, 26, 16, 31)
        fan_leaf(s, 16, 27, 8, fingers=7)
    cells = []
    rows = 14
    for i in range(rows):
        t = i / (rows - 1)
        y = top + 3 + t * (bottom - top - 5)
        w = (9 if not dry else 8) * min(1.0, 0.1 + t * 1.6) * (1 - max(0, t - 0.85) * 2.5)
        n = max(1, int(round(w / 3.0)))
        r = 1.1 + 0.9 * min(1.0, t * 2.2)
        for j in range(n):
            ox = (j - (n - 1) / 2) * (w / n) + rng.uniform(-0.3, 0.3)
            cells.append((16 + ox, y, r + rng.uniform(0, 0.4)))
    body = cluster(s, cells, dry, 0.35 if not dry else 0.5)
    # the sharp tip
    for k, (x, y) in enumerate(((16, top + 1), (16, top + 2), (15, top + 2))):
        s.put(x, y, tones(dry)[2 if k == 0 else 1], 1)
        body.add((x, y))
    hairs(s, body, 16, 10 if not dry else 7, dry)
    if dry:
        sugar_leaf(s, 14, bottom - 6, 210, 6, dry=True)
        sugar_leaf(s, 18, bottom - 10, -30, 5.5, dry=True)


DRAW = {"classic": classic, "foxtail": foxtail, "popcorn": popcorn, "spear": spear}


def sprite(item, shape):
    s = Sprite(seed=sum(ord(ch) * (i + 1) for i, ch in enumerate(item + shape)), layers=5)
    DRAW[shape](s, item == "bud_dried")
    s.outline(0.38)
    return s


# ---------------------------------------------------------------------------
# Mythic overlays (animated): frames of RGBA drawn over a mask
# ---------------------------------------------------------------------------
def hsv(h, s, v):
    h = (h % 1.0) * 6
    i = int(h)
    f = h - i
    p, q, t = v * (1 - s), v * (1 - s * f), v * (1 - s * (1 - f))
    r, g, b = [(v, t, p), (q, v, p), (p, v, t), (p, q, v), (t, p, v), (v, p, q)][i % 6]
    return int(r * 255), int(g * 255), int(b * 255)


def overlay_frames(exotic, mask, w, h, seed=1):
    """FRAMES images of w x h with the exotic look painted over mask."""
    rng = random.Random(f"{exotic}-{seed}")
    edges = edge(mask)
    ys = [p[1] for p in mask] or [0]
    ytop, ybot = min(ys), max(ys)
    stars = {p: rng.random() for p in mask if rng.random() < 0.09}
    frames = []
    for f in range(FRAMES):
        t = f / FRAMES
        img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        px = img.load()
        for (x, y) in mask:
            if not (0 <= x < w and 0 <= y < h):
                continue
            col = None
            if exotic == "rainbow":
                r, g, b = hsv((x + y) / 22.0 - t, 0.8, 1.0)
                col = (r, g, b, 150)
            elif exotic == "galaxy":
                n = math.sin(x * 0.7 + y * 0.4) + math.sin(x * 0.23 - y * 0.61 + 1.7)
                r, g, b = hsv(0.72 + n * 0.06, 0.75, 0.45)
                col = (r, g, b, 140)
                if (x, y) in stars:
                    br = math.sin((t + stars[(x, y)]) * math.pi * 2)
                    if br > 0.35:
                        col = (255, 255, 255, int(150 + 105 * br))
            elif exotic == "golden":
                col = (255, 206, 70, 110)
                band = (x + y) - (t * (w + h + 16) - 8)
                if abs(band) < 2.5:
                    col = (255, 250, 210, 225)
                elif abs(band) < 4.5:
                    col = (255, 232, 140, 170)
            elif exotic == "crystal":
                col = (200, 240, 255, 85)
                if (x * 3 + y * 5) % 9 == 0:
                    col = (240, 252, 255, 160)
                if (x, y) in stars:
                    br = math.sin((t + stars[(x, y)]) * math.pi * 2)
                    if br > 0.5:
                        col = (255, 255, 255, 240)
            elif exotic == "neon":
                pulse = 0.5 + 0.5 * math.sin(t * math.pi * 2)
                r, g, b = hsv(0.36 + 0.14 * math.sin(t * math.pi * 2 + y * 0.15), 0.85, 1.0)
                col = (r, g, b, int(150 + 100 * pulse)) if (x, y) in edges else (r, g, b, int(40 + 50 * pulse))
            elif exotic == "inferno":
                k = (y - ytop) / max(1, ybot - ytop)  # 0 top .. 1 bottom
                flick = math.sin(x * 1.3 + (y + t * 20) * 0.9) * 0.5 + math.sin(x * 0.4 - (y + t * 30) * 0.5) * 0.5
                heat = max(0.0, min(1.0, k * 0.8 + flick * 0.35))
                r, g, b = (255, int(80 + 175 * (1 - heat)), int(40 * (1 - heat)))
                col = (r, g, b, int(110 + 60 * heat))
            if col:
                px[x, y] = col
        frames.append(img)
    return frames


def save_animated(frames, rel):
    """Stacks frames vertically and writes the .mcmeta that animates them."""
    w, h = frames[0].size
    strip = Image.new("RGBA", (w, h * len(frames)), (0, 0, 0, 0))
    for i, fr in enumerate(frames):
        strip.alpha_composite(fr, (0, i * h))
    path = G.save_png(strip, rel)
    meta = {"animation": {"frametime": FRAMETIME, "interpolate": False}}
    if w != h:
        meta["animation"]["width"] = w
        meta["animation"]["height"] = h
    with open(path + ".mcmeta", "w") as f:
        json.dump(meta, f)
    return path


def mask_of(img):
    px = img.load()
    return {(x, y) for y in range(img.height) for x in range(img.width) if px[x, y][3] > 0}


# ---------------------------------------------------------------------------
# writing
# ---------------------------------------------------------------------------
def model(ref):
    return {"type": "minecraft:model", "model": ref}


def bud_tints():
    """Tint per item/generated layer: plain, bud, hair, leaf, frost."""
    return [
        {"type": "minecraft:constant", "value": G.WHITE},
        {"type": "minecraft:custom_model_data", "index": 0, "default": DEFAULT_BUD},
        {"type": "minecraft:custom_model_data", "index": 2, "default": DEFAULT_PISTIL},
        {"type": "minecraft:custom_model_data", "index": 1, "default": DEFAULT_LEAF},
        {"type": "minecraft:constant", "value": G.WHITE},
    ]


def exotic_select(name_for_shape):
    """Second part of a composite: the Mythic overlay for strings[1] (per bud shape)."""
    cases = []
    for ex in EXOTICS:
        shapes = name_for_shape(ex)
        if isinstance(shapes, str):
            inner = model(shapes)
        else:
            inner = {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
                     "cases": [{"when": sh, "model": model(shapes[sh])} for sh in SHAPES[1:]],
                     "fallback": model(shapes["classic"])}
        cases.append({"when": ex, "model": inner})
    return {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 1, "cases": cases,
            "fallback": {"type": "minecraft:empty"}}


def write_overlay(rel_tex, frames):
    save_animated(frames, rel_tex)
    G.save_json({"parent": "minecraft:item/generated", "textures": {"layer0": f"{G.NS}:{rel_tex}"}},
                f"models/{rel_tex}.json")
    return f"{G.NS}:{rel_tex}"


PREVIEW = {}


def flat(s, bud=DEFAULT_BUD, leaf=DEFAULT_LEAF, pistil=DEFAULT_PISTIL, dried=False):
    if dried:
        leaf = mix_rgb(leaf, 0x9A8A4A, 0.45)
        pistil = mix_rgb(pistil, 0x8A5A2A, 0.35)
    return s.flatten({1: bud, 2: pistil, 3: leaf})


def mix_rgb(a, b, t):
    out = 0
    for sh in (16, 8, 0):
        out |= int(round(((a >> sh) & 255) * (1 - t) + ((b >> sh) & 255) * t)) << sh
    return out


def buds():
    for item in ("bud_fresh", "bud_dried"):
        by_shape = {}
        masks = {}
        for shape in SHAPES:
            s = sprite(item, shape)
            tex = {}
            for i, layer in enumerate(s.layers):
                rel = f"item/bud/{item}_{shape}" + ("" if i == 0 else "_" + ["", "bud", "hair", "leaf", "frost"][i])
                G.save_png(layer, rel)
                tex[f"layer{i}"] = f"{G.NS}:{rel}"
            ref = f"{G.NS}:item/bud/{item}_{shape}"
            G.save_json({"parent": "minecraft:item/generated", "textures": tex}, f"models/item/bud/{item}_{shape}.json")
            by_shape[shape] = {**model(ref), "tints": bud_tints()}
            masks[shape] = mask_of(s.layers[1])
            PREVIEW[(item, shape)] = s
        overlays = {}
        for ex in EXOTICS:
            overlays[ex] = {sh: write_overlay(f"item/exotic/{ex}_{item}_{sh}",
                                              overlay_frames(ex, masks[sh], 32, 32, f"{item}{sh}")) for sh in SHAPES}
        shape_select = {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
                        "cases": [{"when": sh, "model": by_shape[sh]} for sh in SHAPES[1:]],
                        "fallback": by_shape["classic"]}
        G.save_json({"model": {"type": "minecraft:composite",
                               "models": [shape_select, exotic_select(lambda ex: overlays[ex])]}}, f"items/{item}.json")
        # previews / recipe pictures / award icons use the classic look in the default colours
        G.GENERATED[item] = ([flat(PREVIEW[(item, "classic")], dried=item == "bud_dried")], False)


def seed_pack_overlay():
    """Mythic seed packets sparkle too: composite of the normal model and an overlay."""
    layers, tinted = G.GENERATED["seed_pack"]
    m = set()
    for l in layers:
        m |= mask_of(l)
    overlays = {ex: write_overlay(f"item/exotic/{ex}_seed_pack", overlay_frames(ex, m, 32, 32, "seed"))
                for ex in EXOTICS}
    base = {**model(f"{G.NS}:item/seed_pack"), "tints": G.strain_tints()}
    G.save_json({"model": {"type": "minecraft:composite",
                           "models": [base, exotic_select(lambda ex: overlays[ex])]}}, "items/seed_pack.json")


# ---------------------------------------------------------------------------
# strain sheet for the README
# ---------------------------------------------------------------------------
def read_strains():
    """Tiny reader for src/main/resources/strains.yml (no PyYAML needed)."""
    text = open(os.path.join(G.PROJECT, "src", "main", "resources", "strains.yml"), encoding="utf-8").read()
    out = []
    cur = None
    for line in text.splitlines():
        m = re.match(r"^  ([a-z0-9_]+):\s*$", line)
        if m:
            cur = {"id": m.group(1)}
            out.append(cur)
            continue
        m = re.match(r"^    ([a-z-]+):\s*(.*)$", line)
        if m and cur is not None:
            cur[m.group(1)] = m.group(2).strip().strip("'\"")
    return out


def strain_sheet():
    import gui
    strains = read_strains()
    cols = 5
    cw, ch = 132, 76
    rows = (len(strains) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cw + 12, rows * ch + 12), (34, 40, 34, 255))
    for i, st in enumerate(strains):
        x, y = 12 + (i % cols) * cw, 12 + (i // cols) * ch
        hexi = lambda k, d: int(st.get(k, d).lstrip("#"), 16)
        bud, leaf, pistil = hexi("color", "#6ABE3A"), hexi("leaf", "#4E9E34"), hexi("pistil", "#E8862E")
        shape = st.get("shape", "classic").lower()
        fresh = flat(PREVIEW[("bud_fresh", shape)], bud, leaf, pistil)
        dried = flat(PREVIEW[("bud_dried", shape)], bud, leaf, pistil, dried=True)
        ex = st.get("exotic", "").lower()
        if ex in EXOTICS:
            ov = overlay_frames(ex, mask_of(PREVIEW[("bud_dried", shape)].layers[1]), 32, 32, "sheet")[3]
            dried = Image.alpha_composite(dried, ov)
        sheet.alpha_composite(fresh.resize((64, 64), Image.NEAREST), (x, y))
        sheet.alpha_composite(dried.resize((64, 64), Image.NEAREST), (x + 60, y))
        name = st.get("name", st["id"]).upper()[:30]
        gui.text(sheet, x, y + 62, name, (240, 240, 230, 255), shadow=(10, 10, 10, 255))
        gui.text(sheet, x, y + 68, st.get("climate", "").upper(), (150, 170, 150, 255))
    path = os.path.join(G.PREVIEW_DIR, "strains_preview.png")
    G.ensure(path)
    sheet.resize((sheet.width * 2, sheet.height * 2), Image.NEAREST).save(path)


def generate(g):
    global G
    G = g
    buds()
    seed_pack_overlay()
    strain_sheet()
