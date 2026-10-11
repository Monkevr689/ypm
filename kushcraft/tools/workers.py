"""Hired workers: their 64x64 player skins and their 3D hats.

The skins go to assets/kush/textures/entity/worker/<type>.png. The plugin
puts them on a mannequin through a profile skin patch
(body = kush:entity/worker/<type>, which the game resolves to
textures/entity/worker/<type>.png). The hats are item models worn in the
head slot (kush:worker_hat_<type>): the head slot draws an item at 0.625x
centred on the head, so the head spans 1.6..14.4 of the model's 0..16 box
and north is the face.

Must match dev.kushcraft.workers.WorkerType.
"""
import os
import random

from PIL import Image

G = None
TYPES = ("farmhand", "dryer", "cook", "runner")


def rgba(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), int(h[6:8], 16) if len(h) == 8 else 255)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3] if len(c) > 3 else 255,)


def face_of(skin):
    """The 8x8 face (with the hat layer on top) of a skin."""
    face = skin.crop((8, 8, 16, 16))
    face.alpha_composite(skin.crop((40, 8, 48, 16)))
    return face


def skin(t):
    return {"farmhand": farmhand_skin, "dryer": dryer_skin, "cook": cook_skin, "runner": runner_skin}[t]()


# ---------------------------------------------------------------------------
# skin layout: a box of w x h x d with its texture corner at (u, v)
# ---------------------------------------------------------------------------
def faces(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h),
    }


# base boxes and their overlay (second layer) boxes
PARTS = {
    "head": ((0, 0), (32, 0), (8, 8, 8)),
    "body": ((16, 16), (16, 32), (8, 12, 4)),
    "right_arm": ((40, 16), (40, 32), (4, 12, 4)),
    "left_arm": ((32, 48), (48, 48), (4, 12, 4)),
    "right_leg": ((0, 16), (0, 32), (4, 12, 4)),
    "left_leg": ((16, 48), (0, 48), (4, 12, 4)),
}


def paint(img, part, fn, overlay=False):
    """fn(face, x, y, w, h) -> colour or None, for every pixel of every face."""
    base, over, (w, h, d) = PARTS[part]
    u, v = over if overlay else base
    for name, (fx, fy, fw, fh) in faces(u, v, w, h, d).items():
        for y in range(fh):
            for x in range(fw):
                c = fn(name, x, y, fw, fh)
                if c is not None:
                    img.putpixel((fx + x, fy + y), c)


def noisy(col, rng, var=0.06):
    return shade(rgba(col), 1 + rng.uniform(-var, var))


# ---------------------------------------------------------------------------
# the farmhand: straw-hat farmer in a plaid shirt and dungarees
# ---------------------------------------------------------------------------
def farmhand_skin():
    rng = random.Random(81)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin, hair = "e0a878", "6a4428"

    def head(f, x, y, w, h):
        if f == "top":
            return noisy(hair, rng)
        if f == "bottom":
            return rgba("c88a5a")
        c = noisy(skin, rng, 0.03)
        if f == "front":
            if y == 0 or (y == 1 and x not in (2, 5)):
                return noisy(hair, rng)
            if y == 3 and x in (1, 2, 5, 6):
                return rgba("4a2a14")  # brows
            if y == 4 and x in (1, 6):
                return rgba("ffffff")
            if y == 4 and x in (2, 5):
                return rgba("3a5a8a")  # eyes
            if y == 5 and x in (3, 4):
                return shade(rgba(skin), 0.85)  # nose
            if y == 6 and 2 <= x <= 5:
                return rgba("a85a4a") if x in (3, 4) else shade(rgba(skin), 0.9)
            if y == 7:
                return shade(rgba("8a6a4a"), 1 + rng.uniform(-0.1, 0.1)) if rng.random() < 0.5 else c  # stubble
            return c
        if f == "back":
            return noisy(hair, rng) if y < 6 else c
        # sides: hair above the ear and a sideburn in front of it
        front_edge = 7 if f == "right" else 0
        if y < 3 or (y < 5 and x == front_edge):
            return noisy(hair, rng)
        if f in ("right", "left") and y in (4, 5) and x == (2 if f == "right" else 5):
            return shade(rgba(skin), 0.82)  # ear
        return c

    def plaid(x, y):
        r = "c83a3a" if (x // 2 + y // 2) % 2 == 0 else "a82a2a"
        if x % 4 == 1 or y % 4 == 1:
            r = "8a1a1a" if (x % 4 == 1 and y % 4 == 1) else "e8d8c8" if (x + y) % 7 == 0 else "6a1414"
        return shade(rgba(r), 1 + rng.uniform(-0.04, 0.04))

    def body(f, x, y, w, h):
        denim = noisy("3a6ab0", rng, 0.05)
        if f in ("top", "bottom"):
            return plaid(x, y) if f == "top" else denim
        if f == "front":
            if y >= 7:
                if x in (3, 4) and y >= 9:
                    return shade(rgba("3a6ab0"), 0.7)
                return denim
            if 2 <= x <= 5 and y >= 3:
                if y == 3:
                    return shade(rgba("3a6ab0"), 1.15)
                if 3 <= x <= 4 and 4 <= y <= 5:
                    return shade(rgba("3a6ab0"), 0.8)  # bib pocket
                return denim
            if x in (1, 6) and y <= 3:
                return rgba("2a5aa0")  # straps
            if x in (1, 6) and y == 4:
                return rgba("f0c84a")  # buttons
            return plaid(x, y)
        if f == "back":
            if y >= 7:
                return denim
            if (x == y + 1 or x == 6 - y) and y < 7:
                return rgba("2a5aa0")  # crossed straps
            return plaid(x, y)
        return denim if y >= 7 else plaid(x, y)

    def arm(f, x, y, w, h):
        if f == "bottom" or (y >= 10 and f != "top"):
            return noisy(skin, rng, 0.03) if y < 11 or f == "bottom" else shade(rgba(skin), 0.9)
        if f == "top":
            return plaid(x, y)
        if y == 6:
            return shade(rgba("e8d8c8"), 0.95)  # rolled-up cuff
        if y > 6:
            return noisy(skin, rng, 0.03)
        return plaid(x + 1, y)

    def leg(f, x, y, w, h):
        if f == "bottom":
            return rgba("3a2414")
        if y >= 9:
            return noisy("6a4428", rng, 0.08) if y < 11 else rgba("3a2414")  # boots
        if f == "top":
            return noisy("3a6ab0", rng)
        c = noisy("3a6ab0", rng, 0.05)
        if f == "front" and x == 3 and y < 9:
            return shade(rgba("3a6ab0"), 0.75)  # seam
        if y == 8:
            return shade(rgba("3a6ab0"), 1.2)  # rolled hem
        return c

    paint(img, "head", head)
    paint(img, "body", body)
    paint(img, "right_arm", arm)
    paint(img, "left_arm", arm)
    paint(img, "right_leg", leg)
    paint(img, "left_leg", leg)
    return img


# ---------------------------------------------------------------------------
# the dryer: green apron, rubber gloves and lab goggles
# ---------------------------------------------------------------------------
def dryer_skin():
    rng = random.Random(82)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin, hair = "9a6440", "1e1a1a"

    def head(f, x, y, w, h):
        if f == "top":
            return noisy(hair, rng, 0.1)
        if f == "bottom":
            return shade(rgba(skin), 0.8)
        c = noisy(skin, rng, 0.03)
        if f == "front":
            if y == 0:
                return noisy(hair, rng, 0.1)
            if y == 5 and x in (3, 4):
                return shade(rgba(skin), 0.82)
            if y == 6 and 2 <= x <= 5:
                return rgba("5a2a24") if x in (3, 4) else shade(rgba(skin), 0.9)
            return c
        if f == "back":
            return noisy(hair, rng, 0.1) if y < 5 else c
        if y < 2:
            return noisy(hair, rng, 0.1)
        return c

    def goggles(f, x, y, w, h):
        """Hat layer: goggles and their strap around the head."""
        if f in ("top", "bottom"):
            return None
        if y not in (3, 4):
            return None
        if f == "front":
            if x in (0, 7) or x in (3, 4):
                return rgba("2a2a30")
            return rgba("8ae8f8") if y == 3 and x in (1, 5) else rgba("4ab8d0")
        return rgba("3a3a44") if y == 3 else rgba("2a2a30")

    def body(f, x, y, w, h):
        shirt = noisy("e8e8e0", rng, 0.03)
        apron = noisy("3a8a4a", rng, 0.05)
        if f == "top":
            return shirt
        if f == "bottom":
            return apron
        if f == "front":
            if y == 0 and x in (2, 5):
                return rgba("2a6a3a")  # neck strap
            if y >= 2 and 1 <= x <= 6:
                if y == 2:
                    return shade(rgba("3a8a4a"), 1.15)
                if 6 <= y <= 8 and 2 <= x <= 5:
                    if y == 6:
                        return shade(rgba("3a8a4a"), 0.75)  # pocket
                    if (x, y) in ((3, 7), (4, 7), (3, 8)):
                        return rgba("8ae85a")  # leaf logo
                    return shade(rgba("3a8a4a"), 0.9)
                return apron
            if y >= 2 and x in (0, 7) and y > 8:
                return apron
            return shirt
        if f == "back":
            if y == 7:
                return rgba("2a6a3a")  # apron ties
            if y == 8 and x in (3, 4):
                return rgba("2a6a3a")
            return shirt
        return apron if y > 8 else (rgba("2a6a3a") if y == 7 else shirt)

    def arm(f, x, y, w, h):
        if f == "top":
            return noisy("e8e8e0", rng, 0.03)
        if f == "bottom":
            return rgba("e0c030")
        if y < 3:
            return noisy("e8e8e0", rng, 0.03)
        if y < 6:
            return noisy(skin, rng, 0.03)
        if y == 6:
            return shade(rgba("f0d040"), 1.1)  # glove cuff
        return noisy("f0d040", rng, 0.06)

    def leg(f, x, y, w, h):
        if f == "bottom" or y >= 10:
            return rgba("141418")
        if f == "top":
            return noisy("3a3a44", rng)
        if f == "front" and x == 3:
            return shade(rgba("3a3a44"), 0.8)
        return noisy("3a3a44", rng, 0.05)

    paint(img, "head", head)
    paint(img, "head", goggles, overlay=True)
    paint(img, "body", body)
    paint(img, "right_arm", arm)
    paint(img, "left_arm", arm)
    paint(img, "right_leg", leg)
    paint(img, "left_leg", leg)
    return img


# ---------------------------------------------------------------------------
# the cook: white chef's jacket, red neckerchief, checked trousers, moustache
# ---------------------------------------------------------------------------
def cook_skin():
    rng = random.Random(85)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin, hair = "f0c09a", "3a2a20"

    def head(f, x, y, w, h):
        if f == "top":
            return noisy(hair, rng, 0.08)
        if f == "bottom":
            return shade(rgba(skin), 0.82)
        c = noisy(skin, rng, 0.03)
        if f == "front":
            if y == 0:
                return noisy(hair, rng, 0.08)
            if y == 3 and x in (1, 2, 5, 6):
                return rgba("2a1a10")  # brows
            if y == 4 and x in (1, 6):
                return rgba("ffffff")
            if y == 4 and x in (2, 5):
                return rgba("4a7a3a")  # eyes
            if y == 5 and x in (3, 4):
                return shade(rgba(skin), 0.84)
            if y == 6 and 1 <= x <= 6:
                return rgba("3a2416") if x not in (3, 4) or rng.random() < 0.7 else rgba("4a2e1c")  # moustache
            if y == 7 and x in (3, 4):
                return rgba("b8645a")
            return c
        if f == "back":
            return noisy(hair, rng, 0.08) if y < 5 else c
        if y < 2:
            return noisy(hair, rng, 0.08)
        if y in (4, 5) and x == (2 if f == "right" else 5):
            return shade(rgba(skin), 0.84)
        return c

    def jacket(x, y):
        return shade(rgba("f6f6f2"), (0.94 if (x + y) % 5 == 0 else 1.0) + rng.uniform(-0.02, 0.02))

    def body(f, x, y, w, h):
        if f in ("top", "bottom"):
            return jacket(x, y) if f == "top" else rgba("2a2a2a")
        if f == "front":
            if y == 0 and 2 <= x <= 5:
                return rgba("d83a2a") if x in (3, 4) else rgba("b82a20")  # neckerchief
            if y == 1 and x in (3, 4):
                return rgba("b82a20")
            if y >= 10:
                return checks(x, y)
            if x in (2, 5) and y in (3, 5, 7):
                return rgba("2a2a2a")  # buttons
            if x == 4 and 2 <= y <= 9:
                return shade(rgba("f6f6f2"), 0.86)  # the jacket's fold
            return jacket(x, y)
        if f == "back":
            return checks(x, y) if y >= 10 else jacket(x, y)
        return checks(x, y) if y >= 10 else jacket(x, y)

    def checks(x, y):
        dark = ((x // 1) + (y // 1)) % 2 == 0
        return shade(rgba("2a2a30" if dark else "e8e8e8"), 1 + rng.uniform(-0.04, 0.04))

    def arm(f, x, y, w, h):
        if f == "bottom" or (y >= 10 and f != "top"):
            return noisy(skin, rng, 0.03)
        if y == 9:
            return shade(rgba("f6f6f2"), 0.88)  # cuff
        return jacket(x, y)

    def leg(f, x, y, w, h):
        if f == "bottom" or y >= 10:
            return rgba("1a1a1e")
        if f == "top":
            return checks(x, y)
        return checks(x, y)

    paint(img, "head", head)
    paint(img, "body", body)
    paint(img, "right_arm", arm)
    paint(img, "left_arm", arm)
    paint(img, "right_leg", leg)
    paint(img, "left_leg", leg)
    return img


# ---------------------------------------------------------------------------
# the runner: purple tracksuit with white stripes, gold chain, shades, sneakers
# ---------------------------------------------------------------------------
def runner_skin():
    rng = random.Random(87)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    skin, hair, suit = "a8704a", "1a1410", "6a3aa8"

    def head(f, x, y, w, h):
        if f == "top":
            return noisy(hair, rng, 0.1)
        if f == "bottom":
            return shade(rgba(skin), 0.82)
        c = noisy(skin, rng, 0.03)
        if f == "front":
            if y == 0:
                return noisy(hair, rng, 0.1)
            if y == 3 and 1 <= x <= 6:
                return rgba("1a1a1e")  # the shades' top bar
            if y == 4 and x in (1, 2, 5, 6):
                return rgba("2a2a34") if x in (2, 5) else rgba("4a4a5a")  # lenses
            if y == 4 and x in (3, 4):
                return rgba("1a1a1e")
            if y == 5 and x in (3, 4):
                return shade(rgba(skin), 0.84)
            if y == 6 and 2 <= x <= 5:
                return rgba("f4f4f0") if x in (3, 4) else rgba("7a3a2a")  # grin
            return c
        if f == "back":
            return noisy(hair, rng, 0.1) if y < 4 else c
        if y < 2:
            return noisy(hair, rng, 0.1)
        if y == 3:
            return rgba("1a1a1e")  # the shades' arms
        return c

    def track(x, y):
        return shade(rgba(suit), 1 + rng.uniform(-0.05, 0.05))

    def body(f, x, y, w, h):
        if f in ("top", "bottom"):
            return track(x, y)
        if f == "front":
            if x in (3, 4) and y <= 9:
                return rgba("e8e8ee") if x == 4 else shade(rgba(suit), 0.8)  # zip
            if (y, x) in ((1, 1), (2, 2), (3, 3), (1, 6), (2, 5), (3, 4)):
                return rgba("f0c83a")  # gold chain
            if y >= 10:
                return shade(rgba(suit), 0.8)
            return track(x, y)
        if f == "back" and y in (2, 3) and 1 <= x <= 6:
            return rgba("f0f0f0")  # a white stripe across the back
        return track(x, y)

    def arm(f, x, y, w, h):
        if f == "bottom" or (y >= 10 and f != "top"):
            return noisy(skin, rng, 0.03)
        if f in ("right", "left") and x in (1, 2):
            return rgba("f4f4f4")  # stripes down the sleeve
        if y == 9:
            return shade(rgba(suit), 0.78)  # cuff
        return track(x, y)

    def leg(f, x, y, w, h):
        if f == "bottom":
            return rgba("e8e8e8")
        if y >= 10:
            return rgba("f4f4f4") if y == 10 or x % 3 else rgba("d83a3a")  # white sneakers
        if f in ("right", "left") and x in (1, 2):
            return rgba("f4f4f4")
        return track(x, y)

    paint(img, "head", head)
    paint(img, "body", body)
    paint(img, "right_arm", arm)
    paint(img, "left_arm", arm)
    paint(img, "right_leg", leg)
    paint(img, "left_leg", leg)
    return img


# ---------------------------------------------------------------------------
# hats: item models worn in the head slot
# ---------------------------------------------------------------------------
def straw_tex():
    rng = random.Random(83)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            weave = (x + y) % 4 < 2 if (y // 2) % 2 == 0 else (x - y) % 4 < 2
            c = rgba("ecd078") if weave else rgba("c8a850")
            img.putpixel((x, y), shade(c, 1 + rng.uniform(-0.06, 0.06)))
    return img


def ribbon_tex():
    img = Image.new("RGBA", (16, 16), rgba("c8302a"))
    for x in range(16):
        img.putpixel((x, 0), rgba("e8504a"))
        img.putpixel((x, 15), rgba("8a1a1a"))
    return img


def cap_tex():
    rng = random.Random(84)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = rgba("2e8a44")
            if x % 4 == 0:
                c = shade(c, 0.86)  # panels
            img.putpixel((x, y), shade(c, 1 + rng.uniform(-0.05, 0.05)))
    # leaf patch on the front
    for (x, y) in ((7, 5), (8, 5), (6, 6), (7, 6), (8, 6), (9, 6), (7, 7), (8, 7), (5, 7), (10, 7), (7, 8), (8, 8),
                   (7, 9), (8, 9), (7, 10)):
        img.putpixel((x, y), rgba("c8f05a"))
    return img


def visor_tex():
    img = Image.new("RGBA", (16, 16), rgba("1e5a2e"))
    for x in range(16):
        img.putpixel((x, 0), rgba("3aa85a"))
    return img


HEAD_DISPLAY = {
    "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "gui": {"rotation": [30, 225, 0], "translation": [0, -2, 0], "scale": [0.7, 0.7, 0.7]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]},
}


def box(frm, to, tex, faces=("north", "south", "east", "west", "up", "down")):
    f = {}
    for d in faces:
        (x0, y0, z0), (x1, y1, z1) = frm, to
        if d in ("up", "down"):
            uv = [x0, z0, x1, z1]
        elif d in ("north", "south"):
            uv = [x0, 16 - y1, x1, 16 - y0]
        else:
            uv = [z0, 16 - y1, z1, 16 - y0]
        uv = [max(0, min(16, v)) for v in uv]
        if uv[0] == uv[2]:
            uv[2] = min(16, uv[0] + 1)
        if uv[1] == uv[3]:
            uv[3] = min(16, uv[1] + 1)
        f[d] = {"texture": tex, "uv": uv}
    return {"from": frm, "to": to, "faces": f}


def hat_model(name, textures, elements):
    G.save_json({"textures": {k: f"{G.NS}:{v}" for k, v in textures.items()}, "display": HEAD_DISPLAY,
                 "elements": elements}, f"models/item/{name}.json")
    G.save_json(G.item_definition(f"{G.NS}:item/{name}"), f"items/{name}.json")


def snapback_tex():
    rng = random.Random(88)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = rgba("f0c83a")
            if x % 5 == 0:
                c = shade(c, 0.86)
            img.putpixel((x, y), shade(c, 1 + rng.uniform(-0.05, 0.05)))
    for x in range(16):
        img.putpixel((x, 15), rgba("2a2a2a"))
    return img


def snapback_visor_tex():
    img = Image.new("RGBA", (16, 16), rgba("2a2a2a"))
    for x in range(16):
        img.putpixel((x, 0), rgba("4a4a4a"))
    return img


def toque_tex():
    rng = random.Random(86)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = rgba("fafaf6")
            if x % 3 == 0:
                c = shade(c, 0.9)  # pleats
            img.putpixel((x, y), shade(c, 1 + rng.uniform(-0.03, 0.02)))
    return img


def toque_band_tex():
    img = Image.new("RGBA", (16, 16), rgba("f0f0ea"))
    for x in range(16):
        img.putpixel((x, 0), rgba("ffffff"))
        img.putpixel((x, 15), rgba("c8c8c0"))
    return img


def hats():
    G.save_png(toque_tex(), "item/worker/toque")
    G.save_png(toque_band_tex(), "item/worker/toque_band")
    G.save_png(straw_tex(), "item/worker/straw")
    G.save_png(ribbon_tex(), "item/worker/ribbon")
    G.save_png(cap_tex(), "item/worker/cap")
    G.save_png(visor_tex(), "item/worker/visor")
    G.save_png(snapback_tex(), "item/worker/snapback")
    G.save_png(snapback_visor_tex(), "item/worker/snapback_visor")
    # a snapback worn backwards: the visor sticks out at the back (south)
    hat_model("worker_hat_runner", {"particle": "item/worker/snapback", "cap": "item/worker/snapback",
                                    "visor": "item/worker/snapback_visor"}, [
        box([1.2, 11.2, 1.2], [14.8, 15.4, 14.8], "#cap"),
        box([2.4, 15.4, 2.4], [13.6, 16.4, 13.6], "#cap"),
        box([3.4, 11.2, 14.8], [12.6, 11.9, 18.4], "#visor"),
    ])
    # straw sun hat: a wide brim at forehead height and a round-ish crown
    hat_model("worker_hat_farmhand", {"particle": "item/worker/straw", "straw": "item/worker/straw",
                                      "ribbon": "item/worker/ribbon"}, [
        box([-2.5, 12.6, -2.5], [18.5, 13.6, 18.5], "#straw"),
        box([2.6, 13.6, 2.6], [13.4, 15.2, 13.4], "#ribbon"),
        box([2.8, 15.2, 2.8], [13.2, 18.4, 13.2], "#straw"),
        box([4, 18.4, 4], [12, 19.2, 12], "#straw"),
    ])
    # a cap with a short visor over the face (north)
    hat_model("worker_hat_dryer", {"particle": "item/worker/cap", "cap": "item/worker/cap",
                                   "visor": "item/worker/visor"}, [
        box([1.2, 11.2, 1.2], [14.8, 15.4, 14.8], "#cap"),
        box([2.4, 15.4, 2.4], [13.6, 16.4, 13.6], "#cap"),
        box([2.4, 11.2, -2.6], [13.6, 11.9, 1.2], "#visor"),
    ])
    # a tall chef's toque: a band round the head and a puffy top
    hat_model("worker_hat_cook", {"particle": "item/worker/toque", "toque": "item/worker/toque",
                                  "band": "item/worker/toque_band"}, [
        box([1.4, 12.4, 1.4], [14.6, 15.6, 14.6], "#band"),
        box([0.8, 15.6, 0.8], [15.2, 20.4, 15.2], "#toque"),
        box([2.2, 20.4, 2.2], [13.8, 21.4, 13.8], "#toque"),
    ])


# ---------------------------------------------------------------------------
# preview: each worker from the front, hat drawn on top
# ---------------------------------------------------------------------------
def front_view(skin, hat=None):
    """32x34 picture of the skin from the front (head, body, arms, legs)."""
    out = Image.new("RGBA", (16, 34), (0, 0, 0, 0))

    def blit(part, dx, dy, overlay=False):
        base, over, (w, h, d) = PARTS[part]
        u, v = over if overlay else base
        fx, fy, fw, fh = faces(u, v, w, h, d)["front"]
        crop = skin.crop((fx, fy, fx + fw, fy + fh))
        out.alpha_composite(crop, (dx, dy))

    blit("head", 4, 2)
    blit("head", 4, 2, overlay=True)
    blit("body", 4, 10)
    blit("right_arm", 0, 10)
    blit("left_arm", 12, 10)
    blit("right_leg", 4, 22)
    blit("left_leg", 8, 22)
    if hat == "farmhand":
        for x in range(1, 15):
            out.putpixel((x, 3), rgba("d8b860"))
        for y in range(0, 3):
            for x in range(5, 11):
                out.putpixel((x, y), rgba("ecd078") if y else rgba("c8a850"))
        for x in range(5, 11):
            out.putpixel((x, 2), rgba("c8302a"))
    elif hat == "cook":
        for y in range(0, 4):
            for x in range(3 if y < 3 else 4, 13 if y < 3 else 12):
                out.putpixel((x, y), rgba("fafaf6") if (x + y) % 3 else rgba("e0e0da"))
    elif hat == "runner":
        for y in range(1, 4):
            for x in range(4, 12):
                out.putpixel((x, y), rgba("f0c83a"))
        for x in range(4, 12):
            out.putpixel((x, 4), rgba("c8a020"))
    elif hat == "dryer":
        for y in range(1, 4):
            for x in range(4, 12):
                out.putpixel((x, y), rgba("2e8a44"))
        for x in range(4, 12):
            out.putpixel((x, 4), rgba("1e5a2e"))
        out.putpixel((7, 2), rgba("c8f05a"))
        out.putpixel((8, 2), rgba("c8f05a"))
    return out


def generate(g):
    global G
    G = g
    skins = {t: skin(t) for t in TYPES}
    for t, img in skins.items():
        G.save_png(img, f"entity/worker/{t}")
    hats()
    sheet = Image.new("RGBA", (4 + (64 + 4) * len(TYPES) + (16 + 4) * len(TYPES), 72), (44, 52, 40, 255))
    x = 4
    for t in TYPES:
        sheet.alpha_composite(skins[t], (x, 4))
        x += 68
    for t in TYPES:
        sheet.alpha_composite(front_view(skins[t], t), (x, 20))
        x += 20
    sheet = sheet.resize((sheet.width * 5, sheet.height * 5), Image.NEAREST)
    path = os.path.join(G.PREVIEW_DIR, "workers_preview.png")
    G.ensure(path)
    sheet.save(path)
