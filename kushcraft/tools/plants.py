"""Procedural cannabis + magic mushroom plant textures and their 3D models.

Cannabis plants are vanilla-style crossed planes.  Each stage can carry two
extra plane layers: buds (tintindex 0, coloured per strain in-game) and
pistils (untinted).  Sativa grows two blocks tall, indica stays short and
bushy, hybrid sits in between.
"""
import math
import random

from PIL import Image

G = None  # gen_assets module (set in generate)

PALETTES = {
    "sativa": dict(leaf="72c23e", leaf_hi="a6e65e", leaf_dk="3f8a24", stem="6f9a3c", stem_dk="4e6e28"),
    "indica": dict(leaf="3d8a2c", leaf_hi="5fae3e", leaf_dk="245a1a", stem="5c7a30", stem_dk="3e5420"),
    "hybrid": dict(leaf="55a834", leaf_hi="84cf52", leaf_dk="2f7020", stem="668a36", stem_dk="465e24"),
}
YELLOW = (214, 196, 74, 255)


def px(img, x, y, c):
    x, y = int(round(x)), int(round(y))
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), c)


def line(img, x0, y0, x1, y1, c, c_tip=None):
    steps = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
    for i in range(steps + 1):
        t = i / max(1, steps)
        px(img, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, c_tip if (c_tip and i == steps) else c)


def leaf(img, x, y, angle, size, pal, broad=False, fade=0.0):
    """Draws a cannabis fan leaf whose petiole starts at (x, y).
    angle: degrees, 0 = right, 90 = up."""
    rgba = G.rgba
    leaf_c = rgba(pal["leaf"])
    hi = rgba(pal["leaf_hi"])
    dk = rgba(pal["leaf_dk"])
    if fade:
        leaf_c = G.mix(leaf_c, YELLOW, fade)
        hi = G.mix(hi, YELLOW, fade)
    a = math.radians(angle)
    # short petiole
    pet = max(1.0, size * 0.25)
    cx, cy = x + math.cos(a) * pet, y - math.sin(a) * pet
    line(img, x, y, cx, cy, dk)
    if size <= 2:
        offs = [(-50, 0.8), (0, 1.0), (50, 0.8)]
    else:
        offs = [(-80, 0.45), (-42, 0.78), (0, 1.0), (42, 0.78), (80, 0.45)]
    for off, lf in offs:
        b = math.radians(angle + off)
        L = size * lf
        ex, ey = cx + math.cos(b) * L, cy - math.sin(b) * L
        line(img, cx, cy, ex, ey, leaf_c, hi)
        if broad and off in (0, -42, 42) and size > 2:
            nx, ny = math.cos(b + math.pi / 2) * 0.7, -math.sin(b + math.pi / 2) * 0.7
            line(img, cx + nx, cy + ny, ex * 0.85 + cx * 0.15 + nx, ey * 0.85 + cy * 0.15 + ny, leaf_c)
    px(img, cx, cy, dk)


def stem(img, x, y0, y1, pal, thick_until=None):
    s = G.rgba(pal["stem"])
    sd = G.rgba(pal["stem_dk"])
    for y in range(y1, y0 + 1):
        px(img, x, y, s)
        if thick_until is not None and y >= thick_until:
            px(img, x + 1, y, sd)


def bud(img, x, y, w, h, pist, pist_color, rng):
    """Grey bud blob (tinted in game) + pistil specks on a second image."""
    for yy in range(h):
        ww = w if 0 < yy < h - 1 else max(1, w - 2)
        if h <= 2:
            ww = w if yy == h - 1 else max(1, w - 1)
        x0 = int(round(x - ww / 2))
        for xx in range(ww):
            v = 228 + rng.randint(-22, 27)
            if xx == 0 or xx == ww - 1:
                v -= 38
            if yy == h - 1:
                v -= 30
            v = max(120, min(255, v))
            px(img, x0 + xx, y + yy, (v, v, v, 255))
            if pist is not None and rng.random() < 0.16:
                px(pist, x0 + xx, y + yy, pist_color)


# Hand-tuned layouts: nodes are (y, leaf_size, angle); y counted from the top of
# a 16 or 32 pixel tall canvas.
SPECS = {
    "sativa": {
        1: dict(H=16, top=8, nodes=[(13, 3.2, 30)], apex=2.5),
        2: dict(H=16, top=2, nodes=[(13, 4.6, 22), (8, 4.0, 34), (4, 3.2, 45)], apex=3),
        3: dict(H=32, top=3, nodes=[(28, 5.6, 20), (22, 5.2, 26), (16, 4.6, 32), (10, 4.0, 40)], apex=3.5),
        4: dict(H=32, top=1, nodes=[(28, 5.8, 18), (22, 5.4, 24), (16, 4.8, 30), (10, 4.2, 38)], apex=3.5),
    },
    "indica": {
        1: dict(H=16, top=9, nodes=[(13, 3.6, 25)], apex=2.5),
        2: dict(H=16, top=5, nodes=[(13, 5.0, 15), (9, 4.4, 28)], apex=3),
        3: dict(H=16, top=3, nodes=[(13, 5.6, 10), (9, 5.0, 22), (6, 4.0, 35)], apex=3),
        4: dict(H=16, top=2, nodes=[(13, 5.8, 8), (9, 5.2, 20), (6, 4.2, 32)], apex=3),
    },
    "hybrid": {
        1: dict(H=16, top=8, nodes=[(13, 3.4, 28)], apex=2.5),
        2: dict(H=16, top=3, nodes=[(13, 4.8, 20), (8, 4.2, 32)], apex=3),
        3: dict(H=32, top=11, nodes=[(28, 5.6, 16), (23, 5.0, 24), (18, 4.4, 32), (14, 3.8, 40)], apex=3),
        4: dict(H=32, top=9, nodes=[(28, 5.8, 14), (23, 5.2, 22), (18, 4.6, 30), (14, 4.0, 38)], apex=3),
    },
}


def plant_texture(kind, stage):
    """Returns (leaves, buds, pistils, height) for the given type/stage."""
    rng = random.Random(f"{kind}-{stage}")
    pal = PALETTES[kind]
    rgba = G.rgba
    broad = kind == "indica"
    cx = 7
    if stage == 0:  # seedling, same for every type
        leaves = Image.new("RGBA", (16, 16))
        p = PALETTES["hybrid"]
        stem(leaves, cx, 15, 12, p)
        for x, y in ((cx - 1, 12), (cx - 2, 11), (cx - 3, 11), (cx + 1, 12), (cx + 2, 11), (cx + 3, 11)):
            px(leaves, x, y, rgba("8ad84a"))
        leaf(leaves, cx, 11, 90, 2.4, p)
        return leaves, None, None, 16

    spec = SPECS[kind][stage]
    H = spec["H"]
    leaves = Image.new("RGBA", (16, H))
    buds = Image.new("RGBA", (16, H))
    pist = Image.new("RGBA", (16, H))
    bottom = H - 1
    stem(leaves, cx, bottom, spec["top"], pal, thick_until=bottom - 5 if stage >= 2 else None)
    pistil_c = rgba("f4f4f4" if stage == 3 else "ec8a32")
    nodes = spec["nodes"]
    for i, (ny, size, ang) in enumerate(nodes):
        fade = 0.4 if (stage == 4 and i == 0) else 0.0
        leaf(leaves, cx + 0.5, ny, ang, size, pal, broad, fade)
        leaf(leaves, cx - 0.5, ny, 180 - ang, size, pal, broad, fade)
        if broad and stage >= 2:
            leaf(leaves, cx + 1, ny - 1, 62, size * 0.62, pal, broad)
            leaf(leaves, cx - 1, ny - 1, 118, size * 0.62, pal, broad)
        if stage >= 3 and i > 0:
            w = 3 if stage == 3 else (4 if broad else 3)
            h = 2 if stage == 3 else (3 if broad else 4)
            bud(buds, cx + 0.5, ny - h, w, h, pist, pistil_c, rng)
    leaf(leaves, cx, spec["top"] + 1, 90, spec["apex"], pal, broad)
    if stage >= 3:
        top = spec["top"]
        if stage == 3:
            w, h = (4, 3) if broad else (3, 4)
        else:
            w, h = (5, 5) if broad else (4, 8 if kind == "sativa" else 6)
        bud(buds, cx + 0.5, top, w, h, pist, pistil_c, rng)
    return leaves, buds, pist, H


# ---------------------------------------------------------------------------
# models
# ---------------------------------------------------------------------------
def cross(y0, y1, tex, depth=0.0, tint=False):
    lo, hi = 8 - depth, 8 + depth
    rot = {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True}

    def face(t):
        f = {"uv": [0, 0, 16, 16], "texture": t}
        if tint:
            f["tintindex"] = 0
        return f

    return [
        {"from": [0.8, y0, lo], "to": [15.2, y1, hi], "rotation": rot, "shade": False,
         "faces": {"north": face(tex), "south": face(tex)}},
        {"from": [lo, y0, 0.8], "to": [hi, y1, 15.2], "rotation": rot, "shade": False,
         "faces": {"east": face(tex), "west": face(tex)}},
    ]


def plant_model(name, tall, has_buds):
    tex = {"particle": f"{G.NS}:block/plant/{name}", "leaves": f"{G.NS}:block/plant/{name}"}
    el = cross(0, 16, "#leaves")
    if tall:
        tex["leaves_top"] = f"{G.NS}:block/plant/{name}_top"
        el += cross(16, 32, "#leaves_top")
    if has_buds:
        tex["buds"] = f"{G.NS}:block/plant/{name}_buds"
        tex["pistils"] = f"{G.NS}:block/plant/{name}_pistils"
        el += cross(0, 16, "#buds", 0.12, tint=True)
        el += cross(0, 16, "#pistils", 0.24)
        if tall:
            tex["buds_top"] = f"{G.NS}:block/plant/{name}_buds_top"
            tex["pistils_top"] = f"{G.NS}:block/plant/{name}_pistils_top"
            el += cross(16, 32, "#buds_top", 0.12, tint=True)
            el += cross(16, 32, "#pistils_top", 0.24)
    return {"ambientocclusion": False, "textures": tex, "elements": el}


def split_save(img, rel):
    """Saves a 16xH image as rel (bottom 16 rows) and rel_top (upper rows)."""
    if img.height == 16:
        G.save_png(img, rel)
        return
    top = img.crop((0, 0, 16, 16))
    bot = img.crop((0, 16, 16, 32))
    G.save_png(bot, rel)
    G.save_png(top, rel + "_top")


PREVIEW = []


def cannabis():
    for kind in ("sativa", "indica", "hybrid"):
        for stage in range(5):
            name = f"{kind}_{stage}"
            leaves, buds, pist, H = plant_texture(kind, stage)
            split_save(leaves, f"block/plant/{name}")
            has_buds = buds is not None and buds.getbbox() is not None
            if has_buds:
                split_save(buds, f"block/plant/{name}_buds")
                split_save(pist, f"block/plant/{name}_pistils")
            G.save_json(plant_model(name, H == 32, has_buds), f"models/plant/{name}.json")
            G.save_json(G.item_definition(f"{G.NS}:plant/{name}",
                                          [{"type": "minecraft:custom_model_data", "index": 0,
                                            "default": G.DEFAULT_TINT}] if has_buds else None),
                        f"items/plant_{name}.json")
            # preview: composite with a tint
            for tint in ((0x6abe3a, 0xa04fd8) if has_buds else (None,)):
                canvas = Image.new("RGBA", (16, 32))
                canvas.alpha_composite(leaves, (0, 32 - H))
                if has_buds:
                    t = G.composite([Image.new("RGBA", (16, H)), buds], tint)
                    canvas.alpha_composite(t, (0, 32 - H))
                    canvas.alpha_composite(pist, (0, 32 - H))
                PREVIEW.append(canvas)


# ---------------------------------------------------------------------------
# magic mushrooms: little 3D cuboids
# ---------------------------------------------------------------------------
def mushroom_textures():
    rgba = G.rgba
    rng = random.Random(7)
    cap = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            c = G.mix(rgba("f0c46a"), rgba("b47432"), min(1, d / 10))
            if rng.random() < 0.08:
                c = rgba("fbe8b4")
            cap.putpixel((x, y), c)
    cap_side = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = G.mix(rgba("d39a48"), rgba("9a5e28"), y / 15)
            if rng.random() < 0.06:
                c = rgba("f2d79a")
            cap_side.putpixel((x, y), c)
    gills = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            ang = math.atan2(y - 7.5, x - 7.5)
            c = rgba("8a6a4a") if int(ang * 8) % 2 == 0 else rgba("6e5038")
            gills.putpixel((x, y), c)
    stalk = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = rgba("f2ead8") if x % 4 else rgba("d8ccb2")
            if rng.random() < 0.05:
                c = rgba("a8c4d8")  # bluish bruise, very psilocybe
            stalk.putpixel((x, y), c)
    myc = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 7.5 and rng.random() < 0.55 - d * 0.05:
                v = rng.randint(200, 245)
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
def oval_leaf(img, x, y, angle, length, col, hi, vein):
    """Glossy oval leaf (coca): 2px wide in the middle, pointed tip."""
    a = math.radians(angle)
    dx, dy = math.cos(a), -math.sin(a)
    nx, ny = -dy, dx
    steps = max(2, int(length * 2))
    for i in range(steps + 1):
        t = i / steps
        cx, cy = x + dx * length * t, y + dy * length * t
        w = math.sin(math.pi * min(1.0, t * 1.1)) * 1.1
        px(img, cx, cy, vein if 0.15 < t < 0.85 else col)
        if w > 0.5:
            px(img, cx + nx * w, cy + ny * w, col)
            px(img, cx - nx * w, cy - ny * w, hi if t < 0.6 else col)


def coca_texture(stage):
    rgba = G.rgba
    rng = random.Random(f"coca-{stage}")
    img = Image.new("RGBA", (16, 16))
    leaf_c, hi, vein = rgba("4fb83a"), rgba("8ae05a"), rgba("2e7a24")
    wood = rgba("6a5434")
    if stage == 0:
        line(img, 7, 15, 7, 12, rgba("6a8a3a"))
        oval_leaf(img, 7, 12, 135, 2.5, leaf_c, hi, vein)
        oval_leaf(img, 7, 12, 45, 2.5, leaf_c, hi, vein)
        return img
    height = {1: 7, 2: 11, 3: 13}[stage]
    branches = {1: 2, 2: 4, 3: 5}[stage]
    line(img, 7, 15, 7, 15 - height, wood)
    if stage >= 2:
        px(img, 8, 15, wood)
        px(img, 8, 14, wood)
    for b in range(branches):
        by = 15 - int(height * (0.35 + 0.6 * b / max(1, branches - 1)))
        side = -1 if b % 2 == 0 else 1
        bx = 7 + side * (2 + (b % 3))
        line(img, 7, by + 1, bx, by - 1, wood)
        for k in range(3):
            ang = 90 + side * (25 + 35 * k) + rng.randint(-8, 8)
            oval_leaf(img, bx, by - 1, ang, 2.6 + rng.random() * 1.2, leaf_c, hi, vein)
        if stage == 3 and rng.random() < 0.85:
            px(img, bx + side, by + 1, rgba("e8402a"))
            px(img, bx, by + 1, rgba("b02a1a"))
    oval_leaf(img, 7, 15 - height, 90, 3, leaf_c, hi, vein)
    oval_leaf(img, 7, 15 - height, 60, 2.6, leaf_c, hi, vein)
    oval_leaf(img, 7, 15 - height, 120, 2.6, leaf_c, hi, vein)
    return img


def poppy_texture(stage):
    rgba = G.rgba
    img = Image.new("RGBA", (16, 16))
    stem_c, leaf_c, leaf_d = rgba("7aa86a"), rgba("8ab890"), rgba("5a8a6a")
    # long grey-green leaves at the base
    for (x0, ang, ln) in ((7, 150, 5), (8, 30, 5), (7, 115, 4), (8, 65, 4)):
        if stage == 0 and ln == 5:
            continue
        a = math.radians(ang)
        line(img, x0, 15, x0 + math.cos(a) * ln, 15 - math.sin(a) * ln, leaf_c, leaf_d)
    if stage == 0:
        line(img, 7, 15, 7, 12, stem_c)
        px(img, 6, 12, leaf_c)
        px(img, 8, 12, leaf_c)
        return img
    stems = [(4, 9), (8, 12), (11, 10)] if stage >= 2 else [(6, 6), (9, 5)]
    for (x, h) in stems:
        top = 15 - h
        for y in range(top, 16):
            sway = 1 if (y < top + 3 and x < 7) else (-1 if (y < top + 3 and x > 9) else 0)
            px(img, x + sway, y, stem_c)
        hx = x + (1 if x < 7 else -1 if x > 9 else 0)
        if stage == 1:
            # drooping bud
            px(img, hx, top, rgba("6a9a5a"))
            px(img, hx + 1, top + 1, rgba("6a9a5a"))
        elif stage == 2:
            # red flower with a dark centre
            for (dx, dy) in ((-1, -1), (0, -1), (1, -1), (-2, 0), (-1, 0), (1, 0), (2, 0), (-1, 1), (0, 1), (1, 1)):
                px(img, hx + dx, top + dy, rgba("e8302a") if dy != -1 else rgba("ff5a4a"))
            px(img, hx, top, rgba("1a1a1a"))
        else:
            # green seed pod with a little crown
            for (dx, dy) in ((-1, 0), (0, 0), (1, 0), (-1, 1), (0, 1), (1, 1), (0, -1)):
                px(img, hx + dx, top + dy, rgba("8ec8a0"))
            px(img, hx - 1, top - 1, rgba("8a7aa8"))
            px(img, hx + 1, top - 1, rgba("8a7aa8"))
            px(img, hx, top, rgba("d8f0e0"))
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
            canvas = Image.new("RGBA", (16, 32))
            canvas.alpha_composite(img, (0, 16))
            PREVIEW.append(canvas)


# ---------------------------------------------------------------------------
# peyote: little round cacti made of boxes, pink flowers when ripe
# ---------------------------------------------------------------------------
def peyote_textures():
    rgba = G.rgba
    rng = random.Random("peyote")
    side = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = rgba("6aa88a") if x % 4 else rgba("4a8a6a")
            side.putpixel((x, y), G.shade(c, rng.uniform(0.93, 1.05)))
    for x in range(2, 16, 4):
        for y in (2, 7, 12):
            side.putpixel((x, y), rgba("eef4e8"))
    top = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            a = math.atan2(y - 7.5, x - 7.5)
            rib = int((a + math.pi) / (2 * math.pi) * 8) % 2
            c = rgba("78b898") if rib else rgba("5a9a7a")
            top.putpixel((x, y), G.shade(c, rng.uniform(0.95, 1.05)))
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8), (4, 4), (11, 4), (4, 11), (11, 11), (7, 2), (2, 8), (13, 7), (8, 13)):
        top.putpixel((x, y), rgba("f4f8ee"))
    flower = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            flower.putpixel((x, y), rgba("ffe070") if d < 2.5 else rgba("f07ab8") if (x + y) % 3 else rgba("ff9ad0"))
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
    sheet = Image.new("RGBA", (len(PREVIEW) * 18 * 4 + 8, 32 * 4 + 8), (60, 80, 120, 255))
    for i, im in enumerate(PREVIEW):
        sheet.alpha_composite(im.resize((64, 128), Image.NEAREST), (4 + i * 72, 4))
    path = os.path.join(G.PREVIEW_DIR, "plants_preview.png")
    G.ensure(path)
    sheet.save(path)
