"""3D block models (rendered by item display entities) for every KushCraft
machine, plus their textures.  All models live in 0..16 block space (some
decorations poke above 16) and carry vanilla-like display transforms so the
same model looks right in the inventory and in hand."""
import math
import random

from PIL import Image, ImageDraw

G = None


def tex(name, img):
    G.save_png(img, f"block/{name}")


def noise_fill(img, base, var, rng, box=None):
    x0, y0, x1, y1 = box or (0, 0, img.width, img.height)
    for y in range(y0, y1):
        for x in range(x0, x1):
            f = 1 + rng.uniform(-var, var)
            img.putpixel((x, y), G.shade(base, f))


def planks(base_hex, rng, horizontal=True):
    base = G.rgba(base_hex)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            row = (y if horizontal else x) // 4
            f = 1 + 0.05 * math.sin((x if horizontal else y) * 0.9 + row * 2.1) + rng.uniform(-0.05, 0.05)
            f *= (0.94, 1.0, 0.97, 1.03)[row % 4]
            c = G.shade(base, f)
            if (y if horizontal else x) % 4 == 3:
                c = G.shade(base, 0.68)
            img.putpixel((x, y), c)
    # nail heads / plank ends
    for row in range(4):
        cut = (row * 7 + 3) % 16
        for k in range(3):
            yy = row * 4 + k
            if horizontal:
                img.putpixel((cut, yy), G.shade(base, 0.72))
            else:
                img.putpixel((yy, cut), G.shade(base, 0.72))
    return img


def metal(base_hex, rng, rivets=True, border=True):
    base = G.rgba(base_hex)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            f = 1 + 0.04 * math.sin(y * 1.7) + rng.uniform(-0.03, 0.03)
            img.putpixel((x, y), G.shade(base, f))
    if border:
        for i in range(16):
            for (x, y) in ((i, 0), (0, i)):
                img.putpixel((x, y), G.shade(base, 1.25))
            for (x, y) in ((i, 15), (15, i)):
                img.putpixel((x, y), G.shade(base, 0.7))
    if rivets:
        for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
            img.putpixel((x, y), G.shade(base, 1.4))
            img.putpixel((x + 1, y + 1), G.shade(base, 0.6))
    return img


def glass(tint_hex, alpha=70):
    t = G.rgba(tint_hex)
    img = Image.new("RGBA", (16, 16), t[:3] + (alpha,))
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel((x, y), G.shade(t, 0.8)[:3] + (200,))
    for k in range(3, 8):
        img.putpixel((k, k - 1), (255, 255, 255, 160))
        img.putpixel((k + 1, k - 1), (255, 255, 255, 110))
    return img


def liquid(hex_, rng):
    c = G.rgba(hex_)
    img = Image.new("RGBA", (16, 16), c[:3] + (215,))
    for _ in range(9):
        x, y = rng.randrange(1, 15), rng.randrange(1, 15)
        img.putpixel((x, y), (255, 255, 255, 190))
    for x in range(16):
        img.putpixel((x, 0), G.shade(c, 1.35)[:3] + (230,))
    return img


def solid(hex_):
    return Image.new("RGBA", (16, 16), G.rgba(hex_))


def leaf_emblem(img, ox, oy, col):
    pts = ["...L...", ".L.L.L.", "L.LLL.L", ".LLLLL.", "..LLL..", "...L..."]
    for y, row in enumerate(pts):
        for x, ch in enumerate(row):
            if ch == "L":
                img.putpixel((ox + x, oy + y), col)


def textures():
    rng = random.Random(99)
    rgba = G.rgba
    tex("walnut", planks("8a5a34", rng))
    tex("walnut_v", planks("8a5a34", rng, horizontal=False))
    tex("pine", planks("c49a62", rng))
    tex("pine_v", planks("c49a62", rng, horizontal=False))
    tex("steel", metal("8d949c", rng))
    tex("steel_plain", metal("8d949c", rng, rivets=False, border=False))
    tex("steel_dark", metal("3c4248", rng))
    tex("black_metal", metal("24262a", rng, rivets=False))

    # lab counter top: white epoxy with a few stains
    top = Image.new("RGBA", (16, 16))
    noise_fill(top, rgba("e8ecec"), 0.025, rng)
    for (x, y, c) in ((4, 5, "a8e8a0"), (5, 5, "a8e8a0"), (11, 10, "b8c8f0"), (12, 11, "d8b0f0")):
        top.putpixel((x, y), rgba(c))
    for i in range(16):
        top.putpixel((i, 0), rgba("2a2e32"))
        top.putpixel((i, 15), rgba("2a2e32"))
        top.putpixel((0, i), rgba("2a2e32"))
        top.putpixel((15, i), rgba("2a2e32"))
    tex("lab_top", top)
    edge = Image.new("RGBA", (16, 16))
    noise_fill(edge, rgba("2a2e32"), 0.05, rng)
    tex("lab_edge", edge)

    cab = metal("b4bac0", rng, rivets=False)
    d = ImageDraw.Draw(cab)
    d.rectangle((2, 2, 13, 6), outline=rgba("6e767e"))
    d.rectangle((2, 9, 13, 13), outline=rgba("6e767e"))
    d.line((6, 4, 9, 4), fill=rgba("2a2e32"))
    d.line((6, 11, 9, 11), fill=rgba("2a2e32"))
    tex("lab_cabinet", cab)

    tex("glass_clear", glass("d8f4ff", 70))
    tex("glass_green", glass("b8f0c8", 90))
    tex("liquid_green", liquid("5ae86a", rng))
    tex("liquid_blue", liquid("3a9af0", rng))
    tex("liquid_purple", liquid("b04ae8", rng))
    tex("liquid_pink", liquid("ff7ad0", rng))

    # strain maker panels
    base = metal("2e3a36", rng)
    for x in range(2, 14):
        base.putpixel((x, 12), rgba("4af07a") if x % 2 == 0 else rgba("1a8a3a"))
    tex("sm_base", base)
    screen = metal("2e3a36", rng, rivets=False)
    d = ImageDraw.Draw(screen)
    d.rectangle((2, 2, 13, 11), fill=rgba("0a1a12"), outline=rgba("1a2a22"))
    for y in range(3, 11):
        t = (y - 3) / 7 * math.pi * 2
        xa = 7.5 + math.sin(t) * 3.5
        xb = 7.5 - math.sin(t) * 3.5
        screen.putpixel((int(round(xa)), y), rgba("ff5aa0"))
        screen.putpixel((int(round(xb)), y), rgba("5ad8ff"))
        if y % 2 == 0:
            for x in range(int(min(xa, xb)) + 1, int(max(xa, xb))):
                screen.putpixel((x, y), rgba("3a6a4a"))
    for x in range(2, 14):
        screen.putpixel((x, 13), rgba("4af07a") if x % 3 else rgba("f0e04a"))
    tex("sm_screen", screen)
    tex("sm_top", metal("3c4a44", rng))

    # rolling tray: wooden rim, green mat, papers + a joint
    tray = planks("6e4428", rng)
    d = ImageDraw.Draw(tray)
    d.rectangle((2, 2, 13, 13), fill=rgba("2f7a3a"))
    d.rectangle((3, 3, 12, 12), outline=rgba("3c8f48"))
    for (x, y) in ((4, 5), (9, 8), (6, 10), (11, 4)):
        tray.putpixel((x, y), rgba("5aaf5a"))
    d.rectangle((3, 4, 7, 6), fill=rgba("f4f4ee"))  # rolling paper
    d.line((4, 9, 9, 9), fill=rgba("f8f8f0"))  # joint
    tray.putpixel((10, 9), rgba("ff8a2a"))
    leaf_emblem(tray, 8, 3, rgba("8ad84a"))
    tex("roll_tray", tray)
    grinder = metal("3a9a4a", rng, rivets=False)
    for x in range(16):
        if x % 2 == 0:
            for y in range(16):
                grinder.putpixel((x, y), G.shade(grinder.getpixel((x, y)), 0.8))
    tex("grinder", grinder)
    tex("grinder_top", metal("4ab85a", rng, rivets=False))

    # drying rack buds: greyscale, tinted by strain colour (fresh / dried)
    for name, lo, hi in (("rack_bud_fresh", 190, 250), ("rack_bud_dry", 120, 175)):
        b = Image.new("RGBA", (16, 16))
        for y in range(16):
            for x in range(16):
                v = rng.randint(lo, hi)
                b.putpixel((x, y), (v, v, v, 255))
        tex(name, b)
    st = Image.new("RGBA", (16, 16), rgba("e8e0c8"))
    tex("string", st)

    # grow lamp LEDs
    led = Image.new("RGBA", (16, 16), rgba("1a1a22"))
    for y in range(1, 16, 3):
        for x in range(1, 16, 3):
            c = ("ff4ad8", "6a5aff", "ff6a6a")[(x // 3 + y // 3) % 3]
            led.putpixel((x, y), rgba(c))
            led.putpixel((x + 1, y), rgba(c))
            led.putpixel((x, y + 1), G.shade(rgba(c), 0.8))
            led.putpixel((x + 1, y + 1), G.shade(rgba(c), 0.8))
    tex("lamp_led", led)
    side = metal("24262a", rng, rivets=False)
    for x in range(1, 15):
        c = ("ff4ad8", "6a5aff", "ff6a6a")[x % 3]
        side.putpixel((x, 11), rgba(c))
        side.putpixel((x, 12), G.shade(rgba(c), 0.75))
    tex("lamp_side", side)

    # planter
    soil = Image.new("RGBA", (16, 16))
    noise_fill(soil, rgba("3a2616"), 0.18, rng)
    for _ in range(14):
        soil.putpixel((rng.randrange(16), rng.randrange(16)), rgba("e8e8e0"))  # perlite
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            soil.putpixel((x, y), rgba("6e4428"))
    tex("planter_soil", soil)
    side = planks("6e4428", rng)
    d = ImageDraw.Draw(side)
    d.rectangle((0, 0, 15, 1), fill=rgba("4e2e18"))
    leaf_emblem(side, 4, 6, rgba("5ac83a"))
    tex("planter_side", side)

    # dealer stand
    crate = planks("9a6a3c", rng)
    d = ImageDraw.Draw(crate)
    d.rectangle((0, 0, 15, 15), outline=rgba("5a3a1e"))
    d.line((0, 0, 15, 15), fill=rgba("6e4a28"))
    d.rectangle((4, 4, 11, 11), fill=rgba("2a5a22"), outline=rgba("1a3a14"))
    leaf_emblem(crate, 5, 5, rgba("9ae85a"))
    tex("crate_front", crate)
    crate2 = planks("9a6a3c", rng)
    d = ImageDraw.Draw(crate2)
    d.rectangle((0, 0, 15, 15), outline=rgba("5a3a1e"))
    d.line((0, 15, 15, 0), fill=rgba("6e4a28"))
    tex("crate_side", crate2)
    reg = metal("4a4e56", rng, rivets=False)
    d = ImageDraw.Draw(reg)
    d.rectangle((2, 2, 13, 7), fill=rgba("0a2a12"))
    for (x, y) in ((4, 4), (5, 4), (6, 4)):
        reg.putpixel((x, y), rgba("4af07a"))
    reg.putpixel((9, 3), rgba("4af07a"))
    reg.putpixel((9, 4), rgba("4af07a"))
    reg.putpixel((9, 5), rgba("4af07a"))
    for y in range(9, 15, 2):
        for x in range(3, 13, 3):
            reg.putpixel((x, y), rgba("d8d8d8"))
            reg.putpixel((x + 1, y), rgba("d8d8d8"))
    tex("register", reg)
    cash = Image.new("RGBA", (16, 16), rgba("8ad88a"))
    d = ImageDraw.Draw(cash)
    for y in range(0, 16, 3):
        d.line((0, y, 15, y), fill=rgba("3a8a3a"))
    d.rectangle((6, 0, 9, 15), fill=rgba("e8ffe8"))
    tex("cash_stack", cash)
    sign = planks("c49a62", rng)
    d = ImageDraw.Draw(sign)
    d.rectangle((0, 0, 15, 15), outline=rgba("5a3a1e"))
    dollar = ["..X..", ".XXX.", "X.X..", ".XXX.", "..X.X", ".XXX.", "..X.."]
    for y, row in enumerate(dollar):
        for x, ch in enumerate(row):
            if ch == "X":
                sign.putpixel((2 + x, 4 + y), rgba("1e6a1e"))
    leaf_emblem(sign, 8, 5, rgba("2e8a24"))
    tex("dealer_sign", sign)

    # small product boxes on the dealer counter / lab bench reuse item textures


# ---------------------------------------------------------------------------
# model helpers
# ---------------------------------------------------------------------------
def face(t, uv=None, tint=False, cull=None):
    f = {"texture": t}
    if uv:
        f["uv"] = uv
    if tint:
        f["tintindex"] = 0
    return f


def cube(frm, to, faces, rot=None, shade=True):
    e = {"from": frm, "to": to, "faces": faces}
    if rot:
        e["rotation"] = rot
    if not shade:
        e["shade"] = False
    return e


def allfaces(t, uvs=None, tint=False, skip=()):
    out = {}
    for d in ("north", "south", "east", "west", "up", "down"):
        if d in skip:
            continue
        out[d] = face(t, None, tint)
    return out


def sides(t, up=None, down=None, tint=False):
    f = {d: face(t, None, tint) for d in ("north", "south", "east", "west")}
    if up:
        f["up"] = face(up, None, tint)
    if down:
        f["down"] = face(down, None, tint)
    return f


def auto_uv(elements):
    """Fills in face UVs from element geometry (like vanilla does when uv is
    omitted) so textures are not stretched on small parts."""
    for e in elements:
        (x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
        for d, f in e["faces"].items():
            if "uv" in f:
                continue
            if d in ("up", "down"):
                uv = [x0, z0, x1, z1]
            elif d in ("north", "south"):
                uv = [x0, 16 - y1, x1, 16 - y0]
            else:
                uv = [z0, 16 - y1, z1, 16 - y0]
            # keep inside 0..16
            uv = [max(0, min(16, v)) for v in uv]
            if uv[0] == uv[2]:
                uv[2] = min(16, uv[0] + 1)
            if uv[1] == uv[3]:
                uv[3] = min(16, uv[1] + 1)
            f["uv"] = uv
    return elements


DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, -1, 0], "scale": [0.55, 0.55, 0.55]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
}


def model(name, textures_, elements, tinted=False, item=True, item_name=None):
    ns = G.NS
    t = {k: (v if ":" in v else f"{ns}:block/{v}") for k, v in textures_.items()}
    m = {"textures": t, "display": DISPLAY, "elements": auto_uv(elements)}
    G.save_json(m, f"models/block/{name}.json")
    if item:
        tints = [{"type": "minecraft:custom_model_data", "index": 0, "default": G.DEFAULT_TINT}] if tinted else None
        G.save_json(G.item_definition(f"{ns}:block/{name}", tints), f"items/{item_name or 'machine_' + name}.json")


def lab_station():
    els = [
        cube([1, 0, 1], [15, 10, 15], sides("#cabinet", down="#edge")),
        cube([0, 10, 0], [16, 12, 16], sides("#edge", up="#top", down="#edge")),
        # erlenmeyer flask (glass + liquid)
        cube([3, 12, 3], [7, 15, 7], sides("#liquid_g", up="#liquid_g")),
        cube([2.5, 12, 2.5], [7.5, 16, 7.5], sides("#glass", up="#glass")),
        cube([4, 16, 4], [6, 19, 6], sides("#glass", up="#glass")),
        # tall graduated cylinder
        cube([10.5, 12, 3.5], [12.5, 18, 5.5], sides("#liquid_b", up="#liquid_b")),
        cube([10, 12, 3], [13, 21, 6], sides("#glass", up="#glass")),
        # beaker
        cube([9.5, 12, 9.5], [13.5, 14.5, 13.5], sides("#liquid_p", up="#liquid_p")),
        cube([9, 12, 9], [14, 16, 14], sides("#glass", up="#glass")),
        # connecting tube
        cube([6, 18, 4.5], [11, 18.6, 5.1], sides("#glass", up="#glass", down="#glass")),
        # burner
        cube([3.5, 12, 10], [6.5, 13.5, 13], sides("#steel", up="#steel_dark")),
    ]
    model("lab_station", {
        "particle": "steel", "cabinet": "lab_cabinet", "edge": "lab_edge", "top": "lab_top",
        "glass": "glass_clear", "liquid_g": "liquid_green", "liquid_b": "liquid_blue", "liquid_p": "liquid_purple",
        "steel": "steel", "steel_dark": "steel_dark",
    }, els)


def strain_maker():
    els = [
        cube([0, 0, 0], [16, 6, 16], {"north": face("#screen"), "south": face("#base"), "east": face("#base"),
                                       "west": face("#base"), "up": face("#top"), "down": face("#base")}),
        cube([1, 15, 1], [15, 16.5, 15], sides("#top", up="#top", down="#top")),
        cube([7, 16.5, 7], [9, 18.5, 9], sides("#base", up="#top")),
        # soil pad + a little plant inside the dome
        cube([5, 6, 5], [11, 7, 11], sides("#planter", up="#soil")),
        cube([2, 6, 2], [14, 15, 14], sides("#glass", up="#glass", down="#glass")),
    ]
    # inner plant: crossed planes
    rot = {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True}
    els.append(cube([4, 7, 8], [12, 15, 8], {"north": face("#plant", [0, 0, 16, 16]),
                                             "south": face("#plant", [0, 0, 16, 16])}, rot, shade=False))
    els.append(cube([8, 7, 4], [8, 15, 12], {"east": face("#plant", [0, 0, 16, 16]),
                                             "west": face("#plant", [0, 0, 16, 16])}, rot, shade=False))
    model("strain_maker", {
        "particle": "sm_base", "base": "sm_base", "screen": "sm_screen", "top": "sm_top", "glass": "glass_green",
        "planter": "steel_dark", "soil": "planter_soil", "plant": f"{G.NS}:block/plant/hybrid_2",
    }, els)


def rolling_table():
    els = [cube([0, 12, 0], [16, 14, 16], sides("#wood", up="#tray", down="#wood"))]
    for (x, z) in ((1, 1), (13, 1), (1, 13), (13, 13)):
        els.append(cube([x, 0, z], [x + 2, 12, z + 2], sides("#wood_v", down="#wood")))
    els.append(cube([1, 4, 1], [15, 5, 15], sides("#wood", up="#wood", down="#wood")))
    # grinder
    els.append(cube([10, 14, 10], [13.5, 16, 13.5], sides("#grinder", up="#grinder_top", down="#grinder")))
    els.append(cube([10.5, 16, 10.5], [13, 16.5, 13], sides("#grinder", up="#grinder_top")))
    # lighter
    els.append(cube([3, 14, 11], [4.2, 16.2, 12], sides("#red", up="#steel")))
    model("rolling_table", {
        "particle": "walnut", "wood": "walnut", "wood_v": "walnut_v", "tray": "roll_tray",
        "grinder": "grinder", "grinder_top": "grinder_top", "red": "liquid_pink", "steel": "steel",
    }, els)


def drying_rack(variant):
    els = [
        cube([0, 0, 6], [2, 16, 10], sides("#wood_v", up="#wood", down="#wood")),
        cube([14, 0, 6], [16, 16, 10], sides("#wood_v", up="#wood", down="#wood")),
        cube([0, 0, 2], [2, 1, 14], sides("#wood", up="#wood", down="#wood")),
        cube([14, 0, 2], [16, 1, 14], sides("#wood", up="#wood", down="#wood")),
        cube([2, 14, 7.5], [14, 15, 8.5], sides("#wood", up="#wood", down="#wood")),
        cube([2, 8, 7.5], [14, 9, 8.5], sides("#wood", up="#wood", down="#wood")),
    ]
    if variant != "empty":
        btex = "#bud"
        for row_y in (14, 8):
            for i, x in enumerate((3.5, 7, 10.5)):
                drop = 1.5 + (i % 2) * 0.8
                els.append(cube([x + 0.8, row_y - drop, 7.9], [x + 1.2, row_y, 8.1], sides("#string")))
                h = 3.2 if variant == "fresh" else 2.6
                w = 2.0 if variant == "fresh" else 1.6
                els.append(cube([x + 1 - w / 2, row_y - drop - h, 8 - w / 2], [x + 1 + w / 2, row_y - drop, 8 + w / 2],
                                sides(btex, up=btex, down=btex, tint=True)))
    textures_ = {"particle": "pine", "wood": "pine", "wood_v": "pine_v", "string": "string",
                 "bud": "rack_bud_fresh" if variant == "fresh" else "rack_bud_dry"}
    name = "drying_rack" if variant == "empty" else f"drying_rack_{variant}"
    model(name, textures_, els, tinted=variant != "empty")


def grow_lamp():
    els = [
        cube([5, 0, 5], [11, 1, 11], sides("#black", up="#black", down="#black")),
        cube([7.5, 1, 7.5], [8.5, 14, 8.5], sides("#steel", up="#steel")),
        cube([2, 14, 4], [14, 16, 12], {"north": face("#side", [0, 0, 16, 16]), "south": face("#side", [0, 0, 16, 16]),
                                         "east": face("#side", [0, 0, 16, 16]), "west": face("#side", [0, 0, 16, 16]),
                                         "up": face("#black"), "down": face("#led")}),
        cube([3, 13.5, 5], [13, 14, 11], {"down": face("#led"), "north": face("#led"), "south": face("#led"),
                                           "east": face("#led"), "west": face("#led")}),
    ]
    model("grow_lamp", {"particle": "black_metal", "black": "black_metal", "steel": "steel_plain", "led": "lamp_led",
                        "side": "lamp_side"},
          els)


def planter():
    els = [
        cube([0, 0, 0], [16, 16, 16], {"north": face("#side"), "south": face("#side"), "east": face("#side"),
                                        "west": face("#side"), "up": face("#soil"), "down": face("#side")}),
    ]
    model("planter_box", {"particle": "planter_side", "side": "planter_side", "soil": "planter_soil"}, els)


def dealer():
    els = [
        cube([1, 0, 1], [15, 10, 15], {"north": face("#crate"), "south": face("#crate2"), "east": face("#crate2"),
                                        "west": face("#crate2"), "up": face("#crate2"), "down": face("#crate2")}),
        cube([0, 10, 0], [16, 11, 16], sides("#wood", up="#wood", down="#wood")),
        cube([9, 11, 8], [14, 14.5, 13], sides("#register", up="#register")),
        cube([9.5, 14.5, 11], [13.5, 16, 12], sides("#register", up="#register")),
        cube([2, 11, 3], [6, 12.5, 6], sides("#cash", up="#cash")),
        cube([2.3, 12.5, 3.3], [5.7, 13.5, 5.7], sides("#cash", up="#cash")),
        cube([1, 11, 14], [2, 22, 15], sides("#wood_v", up="#wood")),
        cube([14, 11, 14], [15, 22, 15], sides("#wood_v", up="#wood")),
        cube([2, 16, 14.2], [14, 22, 14.8], {"north": face("#sign", [0, 0, 16, 16]), "south": face("#sign", [0, 0, 16, 16]),
                                              "up": face("#wood"), "down": face("#wood"), "east": face("#wood"),
                                              "west": face("#wood")}),
        cube([3, 11, 9], [7, 13, 12], sides("#bag", up="#bag")),
    ]
    model("dealer", {"particle": "crate_side", "crate": "crate_front", "crate2": "crate_side", "wood": "walnut",
                     "wood_v": "walnut_v", "register": "register", "cash": "cash_stack", "sign": "dealer_sign",
                     "bag": "pine"}, els)


def generate(g):
    global G
    G = g
    textures()
    lab_station()
    strain_maker()
    rolling_table()
    for v in ("empty", "fresh", "dry"):
        drying_rack(v)
    grow_lamp()
    planter()
    dealer()
