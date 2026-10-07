"""Procedural cannabis + magic mushroom plant textures and their 3D models.

Cannabis plants are vanilla-style crossed planes drawn in greys so every
strain can colour them: leaves (tintindex 1, the strain's leaf colour),
buds (tintindex 0, bud colour), pistils (tintindex 2, hair colour) and an
untinted frost layer. Flowering plants of Mythic strains get an animated
overlay on their buds (tools/buds.py). Sativa grows two blocks tall,
indica stays short and bushy, hybrid sits in between.
"""
import math
import random

from PIL import Image

G = None  # gen_assets module (set in generate)

# greys: the game multiplies them with the strain's leaf colour
PALETTES = {
    "sativa": dict(leaf="d4d4d4", leaf_hi="f6f6f6", leaf_dk="8a8a8a", stem="b4b4b4", stem_dk="7a7a7a"),
    "indica": dict(leaf="b0b0b0", leaf_hi="d8d8d8", leaf_dk="6a6a6a", stem="9a9a9a", stem_dk="646464"),
    "hybrid": dict(leaf="c4c4c4", leaf_hi="e8e8e8", leaf_dk="7c7c7c", stem="a8a8a8", stem_dk="707070"),
}
YELLOW = (255, 255, 236, 255)  # old fan leaves fade lighter
W = 32  # texture width: 32 px per block (twice the vanilla detail)


def px(img, x, y, c):
    x, y = int(round(x)), int(round(y))
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c)


def line(img, x0, y0, x1, y1, c, c_tip=None):
    steps = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
    for i in range(steps + 1):
        t = i / max(1, steps)
        px(img, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, c_tip if (c_tip and i == steps) else c)


def _poly(img_size, pts):
    from PIL import ImageDraw
    m = Image.new("L", img_size, 0)
    ImageDraw.Draw(m).polygon([tuple(p) for p in pts], fill=255)
    d = m.load()
    return [(x, y) for y in range(img_size[1]) for x in range(img_size[0]) if d[x, y] > 127]


def leaflet(img, hx, hy, ang, length, width, light, mid, dark):
    """One pointed leaflet: lit on one side of the midrib, darker on the other."""
    a = math.radians(ang)
    dx, dy = math.cos(a), -math.sin(a)
    nx, ny = -dy, dx
    tip = (hx + dx * length, hy + dy * length)
    pts = [(hx, hy),
           (hx + dx * length * 0.3 + nx * width * 0.8, hy + dy * length * 0.3 + ny * width * 0.8),
           (hx + dx * length * 0.55 + nx * width, hy + dy * length * 0.55 + ny * width),
           tip,
           (hx + dx * length * 0.55 - nx * width, hy + dy * length * 0.55 - ny * width),
           (hx + dx * length * 0.3 - nx * width * 0.8, hy + dy * length * 0.3 - ny * width * 0.8)]
    for (x, y) in _poly(img.size, pts):
        side = (x + 0.5 - hx) * nx + (y + 0.5 - hy) * ny
        # the half facing up catches the light
        up = ny < 0
        lit = (side > 0) == up
        img.putpixel((x, y), light if lit else mid)
    line(img, hx, hy, hx + dx * length * 0.8, hy + dy * length * 0.8, dark)


def leaf(img, x, y, angle, size, pal, broad=False, fade=0.0, rng=None):
    """A cannabis fan leaf whose petiole starts at (x, y). angle: degrees,
    0 = right, 90 = up. broad = indica leaflets."""
    rgba = G.rgba
    light, mid, dark = rgba(pal["leaf_hi"]), rgba(pal["leaf"]), rgba(pal["leaf_dk"])
    if fade:
        light, mid = G.mix(light, YELLOW, fade), G.mix(mid, YELLOW, fade)
    if rng is not None:
        f = rng.uniform(0.92, 1.06)
        light, mid = G.shade(light, f), G.shade(mid, f)
    a = math.radians(angle)
    pet = max(1.5, size * 0.22)
    cx, cy = x + math.cos(a) * pet, y - math.sin(a) * pet
    line(img, x, y, cx, cy, rgba(pal["stem"]))
    if size < 5:
        spec = [(-45, 0.75), (0, 1.0), (45, 0.75)]
    elif size < 9:
        spec = [(-70, 0.5), (-35, 0.82), (0, 1.0), (35, 0.82), (70, 0.5)]
    else:
        spec = [(-82, 0.36), (-55, 0.66), (-27, 0.9), (0, 1.0), (27, 0.9), (55, 0.66), (82, 0.36)]
    wf = 0.22 if broad else 0.13
    for off, lf in sorted(spec, key=lambda o: -abs(o[0])):
        L = size * lf
        leaflet(img, cx, cy, angle + off, L, max(1.0, L * wf), light, mid, dark)
    px(img, cx, cy, dark)


def stem(img, x, y0, y1, pal, thick_until=None):
    s = G.rgba(pal["stem"])
    sd = G.rgba(pal["stem_dk"])
    for y in range(y1, y0 + 1):
        px(img, x, y, s)
        px(img, x + 1, y, sd)
        if thick_until is not None and y >= thick_until:
            px(img, x - 1, y, s)


def bud(img, x, y, w, h, pist, frost, young, rng):
    """A cola made of little round calyxes (greys, tinted with the bud
    colour in game), with hairs on the pistil layer (tinted with the hair
    colour) and white frost on the frost layer."""
    tones = [(92, 92, 92, 255), (170, 170, 170, 255), (222, 222, 222, 255), (252, 252, 252, 255)]
    rows = max(2, int(h / 2.4))
    cells = []
    for i in range(rows):
        t = i / max(1, rows - 1)
        ww = w * (0.4 + 0.6 * math.sin(math.pi * min(1.0, 0.12 + t * 0.92)))
        n = max(1, int(round(ww / 2.4)))
        for j in range(n):
            ox = (j - (n - 1) / 2) * (ww / n) + (0.6 if i % 2 else -0.3) + rng.uniform(-0.4, 0.4)
            cells.append((x + ox, y + 1.2 + t * (h - 2.4), 1.6 + rng.uniform(0, 0.5)))
    body = set()
    for (cx, cy, r) in sorted(cells, key=lambda c: c[1]):
        for yy in range(int(cy - r - 1), int(cy + r + 2)):
            for xx in range(int(cx - r - 1), int(cx + r + 2)):
                dx, dy = (xx + 0.5 - cx) / r, (yy + 0.5 - cy) / (r * 0.9)
                if dx * dx + dy * dy <= 1.0 and 0 <= xx < img.width and 0 <= yy < img.height:
                    lightness = -(dx * 0.62 + dy * 0.78)
                    e = math.hypot(dx, dy)
                    tone = tones[2] if (lightness > 0.3 and e > 0.3) else tones[0] if (lightness < -0.3 and e > 0.55) else tones[1]
                    img.putpixel((xx, yy), tone)
                    body.add((xx, yy))
        hx, hy = int(cx - r * 0.4), int(cy - r * 0.45)
        px(img, hx, hy, tones[3])
        if frost is not None and rng.random() < 0.35:
            px(frost, hx, hy, (255, 255, 248, 235))
    # a dark outline on the outside keeps the buds readable from afar
    for (bx, by) in list(body):
        for (ax, ay) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (bx + ax, by + ay)
            if q not in body and 0 <= q[0] < img.width and 0 <= q[1] < img.height and img.getpixel(q)[3] == 0:
                img.putpixel(q, (60, 60, 60, 255))
    if pist is not None:
        light, dark = ((250, 250, 250, 255), (205, 205, 205, 255))
        edge_px = [p for p in sorted(body) if any((p[0] + a, p[1] + b) not in body for a, b in ((1, 0), (-1, 0), (0, -1)))]
        n = max(3, int(len(body) * (0.14 if young else 0.1)))
        for _ in range(n):
            (bx, by) = rng.choice(edge_px)
            side = -1 if bx < x else 1
            px(pist, bx, by, light)
            px(pist, bx + side, by - 1, light if young else dark)
            if rng.random() < 0.5:
                px(pist, bx + side * 2, by - 1, dark)


# Hand-tuned layouts in texture pixels (32 per block): nodes are (y, leaf size,
# angle) counted from the top of a 32 or 64 pixel tall canvas.
SPECS = {
    "sativa": {
        1: dict(H=32, top=16, nodes=[(26, 7.5, 30)], apex=5),
        2: dict(H=32, top=4, nodes=[(27, 10, 22), (17, 9, 34), (9, 7, 46)], apex=6),
        3: dict(H=64, top=6, nodes=[(56, 12, 20), (44, 11, 26), (32, 10, 32), (20, 8.5, 40)], apex=7),
        4: dict(H=64, top=2, nodes=[(56, 12.5, 18), (44, 11.5, 24), (32, 10.5, 30), (20, 9, 38)], apex=7),
    },
    "indica": {
        1: dict(H=32, top=18, nodes=[(26, 8, 25)], apex=5),
        2: dict(H=32, top=10, nodes=[(27, 11, 15), (19, 10, 28)], apex=6),
        3: dict(H=32, top=6, nodes=[(27, 12, 10), (19, 11, 22), (12, 9, 35)], apex=6),
        4: dict(H=32, top=4, nodes=[(27, 12.5, 8), (19, 11.5, 20), (12, 9.5, 32)], apex=6),
    },
    "hybrid": {
        1: dict(H=32, top=16, nodes=[(26, 7.8, 28)], apex=5),
        2: dict(H=32, top=6, nodes=[(27, 10.5, 20), (17, 9.5, 32)], apex=6),
        3: dict(H=64, top=22, nodes=[(56, 12, 16), (46, 11, 24), (36, 10, 32), (28, 8.5, 40)], apex=6),
        4: dict(H=64, top=18, nodes=[(56, 12.5, 14), (46, 11.5, 22), (36, 10.5, 30), (28, 9, 38)], apex=6),
    },
}


def plant_texture(kind, stage):
    """Returns (leaves, buds, pistils, frost, height) for the given type/stage."""
    rng = random.Random(f"{kind}-{stage}")
    pal = PALETTES[kind]
    rgba = G.rgba
    broad = kind == "indica"
    cx = 15
    if stage == 0:  # seedling, same for every type
        leaves = Image.new("RGBA", (W, 32))
        p = PALETTES["hybrid"]
        stem(leaves, cx, 31, 23, p)
        for (x, y) in ((cx - 2, 24), (cx - 4, 23), (cx - 5, 22), (cx + 2, 24), (cx + 4, 23), (cx + 5, 22)):
            px(leaves, x, y, rgba("f0f0f0"))
            px(leaves, x, y + 1, rgba("b4b4b4"))
        leaf(leaves, cx + 0.5, 22, 90, 4.5, p)
        return leaves, None, None, None, 32

    spec = SPECS[kind][stage]
    H = spec["H"]
    leaves = Image.new("RGBA", (W, H))
    buds = Image.new("RGBA", (W, H))
    pist = Image.new("RGBA", (W, H))
    frost = Image.new("RGBA", (W, H))
    bottom = H - 1
    stem(leaves, cx, bottom, spec["top"], pal, thick_until=bottom - 10 if stage >= 2 else None)
    young = stage == 3
    nodes = spec["nodes"]
    for i, (ny, size, ang) in enumerate(nodes):
        fade = 0.45 if (stage == 4 and i == 0) else 0.0
        leaf(leaves, cx + 1, ny, ang, size, pal, broad, fade, rng)
        leaf(leaves, cx, ny, 180 - ang, size, pal, broad, fade, rng)
        if broad and stage >= 2:
            leaf(leaves, cx + 2, ny - 2, 62, size * 0.6, pal, broad, 0, rng)
            leaf(leaves, cx - 1, ny - 2, 118, size * 0.6, pal, broad, 0, rng)
        if stage >= 3 and i > 0:
            w = 5 if stage == 3 else (8 if broad else 6)
            h = 4 if stage == 3 else (6 if broad else 8)
            # side colas at the branch tips
            for side in (-1, 1):
                bx = cx + 0.5 + side * size * 0.55
                bud(buds, bx, ny - h - 1, w * 0.7, h * 0.8, pist, frost, young, rng)
            bud(buds, cx + 0.5, ny - h, w, h, pist, frost, young, rng)
    leaf(leaves, cx + 0.5, spec["top"] + 2, 90, spec["apex"], pal, broad, 0, rng)
    if stage >= 3:
        top = spec["top"]
        if stage == 3:
            w, h = (8, 6) if broad else (6, 8)
        else:
            w, h = (10, 10) if broad else (8, 16 if kind == "sativa" else 12)
        bud(buds, cx + 0.5, top, w, h, pist, frost, young, rng)
    return leaves, buds, pist, frost, H


# ---------------------------------------------------------------------------
# models
# ---------------------------------------------------------------------------
def cross(y0, y1, tex, depth=0.0, tint=None, planes=2):
    """Crossed planes (2 = vanilla X, 3 = a fuller asterisk)."""
    lo, hi = 8 - depth, 8 + depth

    def face(t):
        f = {"uv": [0, 0, 16, 16], "texture": t}
        if tint is not None:
            f["tintindex"] = tint
        return f

    out = []
    for k in range(planes):
        ang = [45, -45, 0][k] if planes == 3 else [45, -45][k]
        rot = {"origin": [8, 8, 8], "axis": "y", "angle": ang, "rescale": ang != 0}
        out.append({"from": [0.8, y0, lo], "to": [15.2, y1, hi], "rotation": rot, "shade": False,
                    "faces": {"north": face(tex), "south": face(tex)}})
    return out


def plant_model(name, tall, has_buds):
    tex = {"particle": f"{G.NS}:block/plant/{name}", "leaves": f"{G.NS}:block/plant/{name}"}
    el = cross(0, 16, "#leaves", tint=1, planes=3)
    if tall:
        tex["leaves_top"] = f"{G.NS}:block/plant/{name}_top"
        el += cross(16, 32, "#leaves_top", tint=1, planes=3)
    if has_buds:
        for part, depth, tint in (("buds", 0.12, 0), ("pistils", 0.24, 2), ("frost", 0.3, None)):
            tex[part] = f"{G.NS}:block/plant/{name}_{part}"
            el += cross(0, 16, "#" + part, depth, tint=tint, planes=3)
            if tall:
                tex[part + "_top"] = f"{G.NS}:block/plant/{name}_{part}_top"
                el += cross(16, 32, "#" + part + "_top", depth, tint=tint, planes=3)
    return {"ambientocclusion": False, "textures": tex, "elements": el}


def exotic_model(name, ex, tall):
    """Animated Mythic look over the buds (between the buds and the hairs)."""
    base = f"{G.NS}:block/plant/exotic/{ex}_{name}"
    tex = {"particle": base, "glow": base}
    el = cross(0, 16, "#glow", 0.18, planes=3)
    if tall:
        tex["glow_top"] = base + "_top"
        el += cross(16, 32, "#glow_top", 0.18, planes=3)
    return {"ambientocclusion": False, "textures": tex, "elements": el}


def pattern_model(name, pat, tall):
    """The accent pattern over the buds (tint 0 = colours[3]), just in front of the buds."""
    base = f"{G.NS}:block/plant/pattern/{pat}_{name}"
    tex = {"particle": base, "pat": base}
    el = cross(0, 16, "#pat", 0.15, tint=0, planes=3)
    if tall:
        tex["pat_top"] = base + "_top"
        el += cross(16, 32, "#pat_top", 0.15, tint=0, planes=3)
    return {"ambientocclusion": False, "textures": tex, "elements": el}


def split_save(img, rel):
    """Saves a W x H image as rel (bottom block) and rel_top (the block above)."""
    if img.height == img.width:
        G.save_png(img, rel)
        return
    w = img.width
    G.save_png(img.crop((0, w, w, 2 * w)), rel)
    G.save_png(img.crop((0, 0, w, w)), rel + "_top")


PREVIEW = []


LEAF_DEFAULT = 0x4E9E34
PISTIL_DEFAULT = 0xE8862E


def cannabis():
    import buds as budart
    for kind in ("sativa", "indica", "hybrid"):
        for stage in range(5):
            name = f"{kind}_{stage}"
            leaves, buds, pist, frost, H = plant_texture(kind, stage)
            split_save(leaves, f"block/plant/{name}")
            has_buds = buds is not None and buds.getbbox() is not None
            tall = H == 2 * W
            if has_buds:
                split_save(buds, f"block/plant/{name}_buds")
                split_save(pist, f"block/plant/{name}_pistils")
                split_save(frost, f"block/plant/{name}_frost")
            G.save_json(plant_model(name, tall, has_buds), f"models/plant/{name}.json")
            # tint 0 = bud colour, 1 = leaf colour, 2 = hair colour (all from the strain)
            base = {"type": "minecraft:model", "model": f"{G.NS}:plant/{name}", "tints": [
                {"type": "minecraft:custom_model_data", "index": 0, "default": G.DEFAULT_TINT},
                {"type": "minecraft:custom_model_data", "index": 1, "default": LEAF_DEFAULT},
                {"type": "minecraft:custom_model_data", "index": 2, "default": PISTIL_DEFAULT}]}
            if has_buds:
                mask = budart.mask_of(buds)
                refs = {}
                for ex in budart.EXOTICS:
                    frames = budart.overlay_frames(ex, mask, W, H, name)
                    if tall:
                        # one animated texture per block: bottom half and top half
                        budart.save_animated([f.crop((0, W, W, 2 * W)) for f in frames], f"block/plant/exotic/{ex}_{name}")
                        budart.save_animated([f.crop((0, 0, W, W)) for f in frames], f"block/plant/exotic/{ex}_{name}_top")
                    else:
                        budart.save_animated(frames, f"block/plant/exotic/{ex}_{name}")
                    G.save_json(exotic_model(name, ex, tall), f"models/plant/exotic/{ex}_{name}.json")
                    refs[ex] = f"{G.NS}:plant/exotic/{ex}_{name}"
                # two-tone strains: the accent pattern over the buds (not where hairs or frost are)
                bp, pp, fp = buds.load(), pist.load(), frost.load()
                body, shading = set(), {}
                for yy in range(H):
                    for xx in range(W):
                        if bp[xx, yy][3] > 0 and pp[xx, yy][3] == 0 and fp[xx, yy][3] == 0:
                            body.add((xx, yy))
                            shading[(xx, yy)] = bp[xx, yy][0]
                prefs = {}
                for pat in budart.PATTERNS:
                    img = budart.pattern_image(pat, body, shading, W, H, name)
                    split_save(img, f"block/plant/pattern/{pat}_{name}")
                    G.save_json(pattern_model(name, pat, tall), f"models/plant/pattern/{pat}_{name}.json")
                    prefs[pat] = f"{G.NS}:plant/pattern/{pat}_{name}"
                definition = {"type": "minecraft:composite",
                              "models": [base, budart.pattern_select(lambda pat: prefs[pat]),
                                         budart.exotic_select(lambda ex: refs[ex])]}
            else:
                definition = base
            G.save_json({"model": definition}, f"items/plant_{name}.json")
            # preview: composite with a few strain looks
            for (bud_c, leaf_c, pist_c) in (((0x8FD14F, 0x4C9A30, 0xE8862E), (0xA45AE8, 0x5A3A82, 0xF08AD8))
                                            if has_buds else ((None, 0x4C9A30, None),)):
                canvas = Image.new("RGBA", (W, 2 * W))
                canvas.alpha_composite(tinted(leaves, leaf_c), (0, 2 * W - H))
                if has_buds:
                    canvas.alpha_composite(tinted(buds, bud_c), (0, 2 * W - H))
                    canvas.alpha_composite(tinted(pist, pist_c), (0, 2 * W - H))
                    canvas.alpha_composite(frost, (0, 2 * W - H))
                PREVIEW.append(canvas)


def tinted(img, rgb):
    if rgb is None:
        return img
    out = img.copy()
    p = out.load()
    r, g, b = (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255
    for y in range(out.height):
        for x in range(out.width):
            pr, pg, pb, pa = p[x, y]
            if pa:
                p[x, y] = (pr * r // 255, pg * g // 255, pb * b // 255, pa)
    return out


# ---------------------------------------------------------------------------
# magic mushrooms: little 3D cuboids
# ---------------------------------------------------------------------------
def mushroom_textures():
    rgba = G.rgba
    rng = random.Random(7)
    N = 32
    c0 = (N - 1) / 2
    cap = Image.new("RGBA", (N, N))
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - c0, y - c0) / (N / 16)
            col = G.mix(rgba("f2c66a"), rgba("a86a2e"), min(1, d / 10))
            if d < 1.6:
                col = rgba("8a5226")  # the dark nipple of a psilocybe cap
            if rng.random() < 0.06:
                col = rgba("fbe8b4")
            cap.putpixel((x, y), col)
    cap_side = Image.new("RGBA", (N, N))
    for y in range(N):
        for x in range(N):
            col = G.mix(rgba("d39a48"), rgba("8a5424"), y / (N - 1))
            if y > N - 4:
                col = rgba("f0dca8")  # pale rim
            elif rng.random() < 0.05:
                col = rgba("f2d79a")
            cap_side.putpixel((x, y), col)
    gills = Image.new("RGBA", (N, N))
    for y in range(N):
        for x in range(N):
            ang = math.atan2(y - c0, x - c0)
            col = rgba("8a6a4a") if int(ang * 14) % 2 == 0 else rgba("5e4430")
            if math.hypot(x - c0, y - c0) < 3:
                col = rgba("e8dcc4")
            gills.putpixel((x, y), col)
    stalk = Image.new("RGBA", (N, N))
    for y in range(N):
        for x in range(N):
            col = rgba("f2ead8") if x % 8 not in (0, 1) else rgba("d8ccb2")
            if rng.random() < 0.04:
                col = rgba("8aaad8")  # bluish bruise, very psilocybe
            stalk.putpixel((x, y), col)
    myc = Image.new("RGBA", (N, N))
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - c0, y - c0) / (N / 16)
            if d < 7.5 and rng.random() < 0.6 - d * 0.06:
                v = rng.randint(200, 248)
                myc.putpixel((x, y), (v, v, v - 10, 255))
    for name, img in (("mushroom_cap", cap), ("mushroom_cap_side", cap_side), ("mushroom_gills", gills),
                      ("mushroom_stalk", stalk), ("mycelium_patch", myc)):
        G.save_png(img, f"block/plant/{name}")


def box(f, t, faces, rot=None):
    e = {"from": f, "to": t, "faces": faces}
    if rot:
        e["rotation"] = rot
    return e


def shroom(x, z, stalk_h, cap_w, cap_h=2.0, tilt=0, axis="x"):
    ns = G.NS
    sw = 1 if cap_w <= 3 else 2
    sx0, sz0 = x - sw / 2, z - sw / 2
    rot = {"origin": [x, 0, z], "axis": axis, "angle": tilt} if tilt else None
    allf = lambda tex: {d: {"uv": [0, 0, 16, 16], "texture": tex} for d in
                        ("north", "south", "east", "west", "up", "down")}
    stalk = box([sx0, 0, sz0], [sx0 + sw, stalk_h, sz0 + sw], {
        d: {"uv": [0, 0, 4, 16], "texture": "#stalk"} for d in ("north", "south", "east", "west")}, rot)
    c0x, c0z = x - cap_w / 2, z - cap_w / 2
    capf = allf("#cap_side")
    capf["up"] = {"uv": [0, 0, 16, 16], "texture": "#cap"}
    capf["down"] = {"uv": [0, 0, 16, 16], "texture": "#gills"}
    cap = box([c0x, stalk_h, c0z], [c0x + cap_w, stalk_h + cap_h, c0z + cap_w], capf, rot)
    els = [stalk, cap]
    if cap_w >= 4:
        dome_w = cap_w - 2
        d0x, d0z = x - dome_w / 2, z - dome_w / 2
        domef = allf("#cap_side")
        domef["up"] = {"uv": [4, 4, 12, 12], "texture": "#cap"}
        els.append(box([d0x, stalk_h + cap_h, d0z], [d0x + dome_w, stalk_h + cap_h + 1, d0z + dome_w], domef, rot))
    return els


def mushrooms():
    mushroom_textures()
    ns = G.NS
    patch = box([1, 0.05, 1], [15, 0.15, 15], {"up": {"uv": [0, 0, 16, 16], "texture": "#mycelium"}})
    stages = {
        0: [shroom(5, 6, 1.5, 1.5, 1.0), shroom(10, 9, 1.0, 1.5, 1.0), shroom(7, 11, 1.5, 1.5, 1.0)],
        1: [shroom(5, 6, 3, 3, 1.5), shroom(10.5, 9, 2.5, 3, 1.5, 22.5, "z"), shroom(7, 11.5, 2, 2.5, 1.5)],
        2: [shroom(5, 6, 5, 4), shroom(11, 9, 4, 4, 2, -22.5, "z"), shroom(7, 12, 3, 3, 1.5), shroom(11, 4, 2, 2.5, 1.5)],
        3: [shroom(6, 7, 8, 6), shroom(11.5, 10, 6, 5, 2, -22.5, "z"), shroom(5, 12, 4, 4, 2, 22.5, "x"),
            shroom(11, 4, 3, 3, 1.5), shroom(3, 3.5, 2, 2.5, 1.5)],
    }
    for stage, groups in stages.items():
        els = [patch]
        for g in groups:
            els += g
        model = {
            "ambientocclusion": False,
            "textures": {
                "particle": f"{ns}:block/plant/mushroom_cap", "cap": f"{ns}:block/plant/mushroom_cap",
                "cap_side": f"{ns}:block/plant/mushroom_cap_side", "gills": f"{ns}:block/plant/mushroom_gills",
                "stalk": f"{ns}:block/plant/mushroom_stalk", "mycelium": f"{ns}:block/plant/mycelium_patch",
            },
            "elements": els,
        }
        G.save_json(model, f"models/plant/mushroom_{stage}.json")
        G.save_json(G.item_definition(f"{ns}:plant/mushroom_{stage}"), f"items/plant_mushroom_{stage}.json")


# ---------------------------------------------------------------------------
# coca bush & opium poppy (4 stages each, crossed planes, no tint)
# ---------------------------------------------------------------------------
def oval_leaf(img, x, y, angle, length, col, hi, vein, width=None):
    """Glossy oval leaf (coca): pointed tip, lit on its upper half."""
    leaflet(img, x, y, angle, length, width or max(1.2, length * 0.32), hi, col, vein)


def coca_texture(stage):
    rgba = G.rgba
    rng = random.Random(f"coca-{stage}")
    img = Image.new("RGBA", (W, 32))
    leaf_c, hi, vein = rgba("44a838"), rgba("86dc5a"), rgba("2a6a20")
    wood, wood_d = rgba("7a6240"), rgba("4e3e26")
    cx = 15
    if stage == 0:
        line(img, cx, 31, cx, 24, rgba("6a8a3a"))
        oval_leaf(img, cx, 24, 140, 5, leaf_c, hi, vein)
        oval_leaf(img, cx, 24, 40, 5, leaf_c, hi, vein)
        return img
    height = {1: 14, 2: 22, 3: 26}[stage]
    branches = {1: 3, 2: 6, 3: 8}[stage]
    for y in range(31 - height, 32):
        px(img, cx, y, wood)
        px(img, cx + 1, y, wood_d)
    for b in range(branches):
        by = 31 - int(height * (0.3 + 0.62 * b / max(1, branches - 1)))
        side = -1 if b % 2 == 0 else 1
        bx = cx + side * (4 + (b % 3) * 2)
        line(img, cx, by + 2, bx, by - 2, wood)
        for k in range(3):
            ang = 90 + side * (20 + 32 * k) + rng.randint(-10, 10)
            oval_leaf(img, bx, by - 2, ang, 5 + rng.random() * 2.5, leaf_c, hi, vein)
        if stage == 3:
            for (dx, dy) in ((0, 1), (side, 2), (-side, 2)):
                if rng.random() < 0.8:
                    px(img, bx + dx, by + dy, rgba("e8402a"))
                    px(img, bx + dx + 1, by + dy, rgba("a82a1a"))
    for ang in (90, 55, 125):
        oval_leaf(img, cx + 0.5, 31 - height, ang, 6, leaf_c, hi, vein)
    return img


def poppy_texture(stage):
    rgba = G.rgba
    img = Image.new("RGBA", (W, 32))
    stem_c, leaf_c, leaf_d, leaf_h = rgba("7aa86a"), rgba("8ab890"), rgba("5a8a6a"), rgba("b4d8b8")
    for (x0, ang, ln) in ((14, 150, 10), (16, 30, 10), (14, 115, 8), (16, 65, 8), (15, 170, 6), (15, 10, 6)):
        if stage == 0 and ln >= 8:
            continue
        leaflet(img, x0, 31, ang, ln, 1.6, leaf_h, leaf_c, leaf_d)
    if stage == 0:
        line(img, 15, 31, 15, 25, stem_c)
        leaflet(img, 15, 25, 135, 3, 1.2, leaf_h, leaf_c, leaf_d)
        leaflet(img, 15, 25, 45, 3, 1.2, leaf_h, leaf_c, leaf_d)
        return img
    stems = [(8, 18), (16, 24), (23, 20)] if stage >= 2 else [(12, 12), (19, 10)]
    for (x, h) in stems:
        top = 31 - h
        for y in range(top, 32):
            sway = 1 if (y < top + 5 and x < 14) else (-1 if (y < top + 5 and x > 18) else 0)
            px(img, x + sway, y, stem_c)
        hx = x + (1 if x < 14 else -1 if x > 18 else 0)
        if stage == 1:
            # drooping hairy bud
            for (dx, dy) in ((0, 0), (1, 1), (2, 2), (1, 2), (2, 3), (3, 3)):
                px(img, hx + dx, top + dy, rgba("6a9a5a"))
            px(img, hx + 3, top + 4, rgba("8ab87a"))
        elif stage == 2:
            # a red flower with a dark heart
            for dy in range(-3, 3):
                for dx in range(-4, 5):
                    if (dx / 4.5) ** 2 + ((dy + 0.5) / 3.0) ** 2 <= 1:
                        c = rgba("ff5a4a") if dy < -1 else rgba("e8302a") if dy < 1 else rgba("b8201a")
                        px(img, hx + dx, top + dy, c)
            for (dx, dy) in ((0, 0), (-1, 0), (0, -1)):
                px(img, hx + dx, top + dy, rgba("1a1a1a"))
        else:
            # a fat seed pod with its crown
            for dy in range(-1, 4):
                for dx in range(-2, 3):
                    if (dx / 2.6) ** 2 + ((dy - 1.2) / 2.8) ** 2 <= 1:
                        px(img, hx + dx, top + dy, rgba("8ec8a0") if dx < 1 else rgba("6aa880"))
            for dx in (-2, 0, 2):
                px(img, hx + dx, top - 2, rgba("8a7aa8"))
            px(img, hx - 1, top, rgba("d8f0e0"))
    return img


def small_crops():
    for kind, fn in (("coca", coca_texture), ("poppy", poppy_texture)):
        for stage in range(4):
            name = f"{kind}_{stage}"
            img = fn(stage)
            G.save_png(img, f"block/plant/{name}")
            model = {"ambientocclusion": False,
                     "textures": {"particle": f"{G.NS}:block/plant/{name}", "leaves": f"{G.NS}:block/plant/{name}"},
                     "elements": cross(0, 16, "#leaves")}
            G.save_json(model, f"models/plant/{name}.json")
            G.save_json(G.item_definition(f"{G.NS}:plant/{name}"), f"items/plant_{name}.json")
            canvas = Image.new("RGBA", (W, 2 * W))
            canvas.alpha_composite(img, (0, W))
            PREVIEW.append(canvas)


# ---------------------------------------------------------------------------
# peyote: little round cacti made of boxes, pink flowers when ripe
# ---------------------------------------------------------------------------
def peyote_textures():
    rgba = G.rgba
    rng = random.Random("peyote")
    N = 32
    c0 = (N - 1) / 2
    side = Image.new("RGBA", (N, N))
    for y in range(N):
        for x in range(N):
            rib = x % 8
            col = rgba("74b496") if rib in (2, 3, 4) else rgba("5e9e80") if rib in (1, 5) else rgba("467e64")
            side.putpixel((x, y), G.shade(col, rng.uniform(0.94, 1.05)))
    for x in range(3, N, 8):
        for y in (4, 14, 24):
            for (dx, dy) in ((0, 0), (1, 0), (0, 1), (1, 1), (-1, 0)):
                side.putpixel((x + dx, y + dy), rgba("f4f6ee") if (dx, dy) != (-1, 0) else rgba("d8dcd0"))
    top = Image.new("RGBA", (N, N))
    for y in range(N):
        for x in range(N):
            a = math.atan2(y - c0, x - c0)
            rib = int((a + math.pi) / (2 * math.pi) * 8) % 2
            d = math.hypot(x - c0, y - c0)
            col = rgba("80c0a0") if rib else rgba("5a9a7a")
            col = G.shade(col, 1.08 - d / N * 0.4)
            top.putpixel((x, y), G.shade(col, rng.uniform(0.96, 1.04)))
    for k in range(8):
        a = (k + 0.5) / 8 * 2 * math.pi - math.pi
        for r in (6, 11):
            x, y = int(c0 + math.cos(a) * r), int(c0 + math.sin(a) * r)
            for (dx, dy) in ((0, 0), (1, 0), (0, 1), (1, 1)):
                top.putpixel((x + dx, y + dy), rgba("f4f8ee"))
    for (dx, dy) in ((0, 0), (1, 0), (0, 1), (1, 1), (-1, 0), (0, -1)):
        top.putpixel((int(c0) + dx, int(c0) + dy), rgba("f8faf2"))
    flower = Image.new("RGBA", (N, N))
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - c0, y - c0)
            a = math.atan2(y - c0, x - c0)
            petal = (math.cos(a * 6) + 1) / 2
            col = rgba("ffe070") if d < 5 else G.mix(rgba("f07ab8"), rgba("ffb4dc"), petal)
            flower.putpixel((x, y), col)
    G.save_png(side, "block/plant/peyote_side")
    G.save_png(top, "block/plant/peyote_top")
    G.save_png(flower, "block/plant/peyote_flower")


def cactus(x, z, w, h):
    faces = {d: {"uv": [0, 0, 16, 16], "texture": "#side"} for d in ("north", "south", "east", "west")}
    faces["up"] = {"uv": [0, 0, 16, 16], "texture": "#top"}
    els = [box([x - w / 2, 0, z - w / 2], [x + w / 2, h, z + w / 2], faces)]
    if w >= 3:
        d = w - 1.5
        dome = {k: {"uv": [0, 0, 16, 4], "texture": "#side"} for k in ("north", "south", "east", "west")}
        dome["up"] = {"uv": [0, 0, 16, 16], "texture": "#top"}
        els.append(box([x - d / 2, h, z - d / 2], [x + d / 2, h + 0.75, z + d / 2], dome))
    return els


def bloom(x, z, y):
    f = {k: {"uv": [0, 0, 16, 16], "texture": "#flower"} for k in ("north", "south", "east", "west", "up")}
    return [box([x - 1, y, z - 1], [x + 1, y + 1.25, z + 1], f)]


def peyote():
    peyote_textures()
    ns = G.NS
    stages = {
        0: cactus(8, 8, 2, 1),
        1: cactus(8, 8, 4, 2.5),
        2: cactus(7, 8, 5, 3.5) + cactus(11, 10.5, 3, 2) + cactus(5, 11.5, 3, 2),
        3: cactus(7, 8, 6, 4) + bloom(7, 8, 4.75) + cactus(11.5, 10.5, 4, 3) + bloom(11.5, 10.5, 3.75)
           + cactus(4.5, 11.5, 3.5, 2.5),
    }
    for stage, els in stages.items():
        model = {"ambientocclusion": False,
                 "textures": {"particle": f"{ns}:block/plant/peyote_top", "side": f"{ns}:block/plant/peyote_side",
                              "top": f"{ns}:block/plant/peyote_top", "flower": f"{ns}:block/plant/peyote_flower"},
                 "elements": els}
        G.save_json(model, f"models/plant/peyote_{stage}.json")
        G.save_json(G.item_definition(f"{ns}:plant/peyote_{stage}"), f"items/plant_peyote_{stage}.json")


def generate(g):
    global G
    G = g
    cannabis()
    small_crops()
    mushrooms()
    peyote()
    # plant preview strip
    import os
    sheet = Image.new("RGBA", (len(PREVIEW) * 36 * 2 + 8, 64 * 2 + 8), (60, 80, 120, 255))
    for i, im in enumerate(PREVIEW):
        sheet.alpha_composite(im.resize((64, 128), Image.NEAREST), (4 + i * 72, 4))
    path = os.path.join(G.PREVIEW_DIR, "plants_preview.png")
    G.ensure(path)
    sheet.save(path)
