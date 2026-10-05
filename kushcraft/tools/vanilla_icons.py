"""Look-alike icons for the vanilla ingredients shown in the recipe images
(the handbook pages and docs/recipes_preview.png).  Drawn from scratch: the
real Minecraft textures are not part of this project.

ICONS maps a material name (lower case, as in Bukkit's Material) to a 16x16
RGBA image.  Blocks are drawn as small isometric cubes like in the inventory.
"""
import random

from PIL import Image

ICONS = {}


def rgba(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), int(h[6:8], 16) if len(h) == 8 else 255)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)


def art(name, rows, pal):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), (name, [len(r) for r in rows])
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), rgba(pal[ch]))
    ICONS[name] = img


# ---------------------------------------------------------------------------
# isometric blocks
# ---------------------------------------------------------------------------
def cube(name, top, left, right=None):
    """top/left/right: 16x16 face textures (PIL images)."""
    right = right or left
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # top rhombus: centre (8, 4), half width 8, half height 4
    for y in range(0, 9):
        for x in range(16):
            # inverse map to texture coords
            dx, dy = (x + 0.5 - 8) / 8.0, (y + 0.5 - 4) / 4.0
            u, v = (dx + dy) / 2 + 0.5, (dy - dx) / 2 + 0.5
            if 0 <= u < 1 and 0 <= v < 1:
                img.putpixel((x, y), top.getpixel((int(u * 16), int(v * 16))))
    for y in range(4, 16):
        for x in range(16):
            if x < 8:
                u = (x + 0.5) / 8.0
                v = (y + 0.5 - 4 - u * 4) / 8.0
                face, f = left, 0.78
            else:
                u = (x + 0.5 - 8) / 8.0
                v = (y + 0.5 - 8 + u * 4) / 8.0
                face, f = right, 0.62
            if 0 <= v < 1 and 0 <= u < 1:
                c = face.getpixel((int(u * 16), int(v * 16)))
                if c[3] > 0:
                    img.putpixel((x, y), shade(c, f))
    ICONS[name] = img


def texture(fn, seed):
    rng = random.Random(seed)
    t = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            t.putpixel((x, y), fn(x, y, rng))
    return t


def planks(x, y, rng):
    base = rgba("b08a50")
    if y % 4 == 3:
        return shade(base, 0.62)
    if (x + (y // 4) * 5) % 16 == 0:
        return shade(base, 0.7)
    return shade(base, rng.uniform(0.9, 1.08))


def dirt(x, y, rng):
    return shade(rgba("86603e"), rng.choice((0.75, 0.88, 1.0, 1.0, 1.12)))


def glass(x, y, rng):
    if x in (0, 15) or y in (0, 15):
        return rgba("d8f0f8")
    if x + y in (5, 6, 20, 21) and 2 < x < 14:
        return rgba("ffffffc0")
    return rgba("c8e8f040")


def ice(x, y, rng):
    c = rgba("8ab8f8")
    if (x * 3 + y * 5) % 11 == 0 or (x - y) % 9 == 0:
        return shade(c, 1.2)
    return shade(c, rng.uniform(0.92, 1.05))


def lamp(x, y, rng):
    if x in (0, 15) or y in (0, 15) or x in (5, 10) or y in (5, 10):
        return rgba("4a2a1a")
    return rgba("f8b040") if (x + y) % 3 else rgba("ffe08a")


def chest_side(x, y, rng):
    if x in (0, 15) or y in (0, 15) or y in (5, 6):
        return rgba("3a2410")
    if 6 <= x <= 9 and 4 <= y <= 8:
        return rgba("d8d8d8") if not (x in (6, 9) or y in (4, 8)) else rgba("5a5a5a")
    return shade(rgba("a06a2a"), 0.95 if y % 3 else 0.85)


def chest_top(x, y, rng):
    if x in (0, 15) or y in (0, 15):
        return rgba("3a2410")
    return shade(rgba("a06a2a"), 1.05 if x % 4 else 0.88)


cube("oak_planks", texture(planks, 1), texture(planks, 2))
cube("dirt", texture(dirt, 3), texture(dirt, 4))
cube("glass", texture(glass, 5), texture(glass, 5))
cube("ice", texture(ice, 6), texture(ice, 7))
cube("redstone_lamp", texture(lamp, 8), texture(lamp, 8))
cube("chest", texture(chest_top, 9), texture(chest_side, 10), texture(chest_top, 11))

# ---------------------------------------------------------------------------
# items
# ---------------------------------------------------------------------------
INGOT = [
    "................",
    "................",
    "................",
    "................",
    "......KKKKKKKK..",
    ".....KhhhhhhhmK.",
    "....KhhmmmmmmdK.",
    "...KhhmmmmmmdKK.",
    "..KKKKKKKKKKdK..",
    "..KhhhhhhhhKK...",
    "..KmmmmmmmdK....",
    "..KKKKKKKKKK....",
    "................",
    "................",
    "................",
    "................",
]
art("iron_ingot", INGOT, {"K": "3a3a3a", "h": "ffffff", "m": "d8d8d8", "d": "9a9a9a"})

art("iron_nugget", [
    "................",
    "................",
    "................",
    "................",
    "................",
    "......KKK.......",
    ".....KhhmK......",
    "....KhmmmdK.....",
    "....KmmmddK.....",
    ".....KddKK..KK..",
    "......KK...KhmK.",
    "...........KmdK.",
    "............KK..",
    "................",
    "................",
    "................",
], {"K": "3a3a3a", "h": "ffffff", "m": "d0d0d0", "d": "8a8a8a"})

GEM = [
    "................",
    "................",
    "................",
    "......KKKK......",
    ".....KhhmmK.....",
    "....KhhmmmdK....",
    "...KhmmmmmmdK...",
    "...KmmmmmmmdK...",
    "....KmmmmmdK....",
    ".....KmmmdK.....",
    "......KmdK......",
    ".......KK.......",
    "................",
    "................",
    "................",
    "................",
]
art("emerald", GEM, {"K": "0a3a1a", "h": "baffd0", "m": "3ad870", "d": "1a8a3a"})

art("lapis_lazuli", [
    "................",
    "................",
    "................",
    "........KK......",
    ".......KhmK.....",
    ".....KKmmmdK....",
    "....KhmmmmdK....",
    "...KhmmmmmmdK...",
    "...KmmmdmmmdK...",
    "....KmmmmmdK....",
    ".....KmmdKK.....",
    "......KKK.......",
    "................",
    "................",
    "................",
    "................",
], {"K": "0a1a4a", "h": "8ab0ff", "m": "2a5ad8", "d": "1a3a8a"})

art("amethyst_shard", [
    "................",
    "................",
    "...........KK...",
    "..........KhK...",
    ".........KhmK...",
    "....K...KhmdK...",
    "...KhK.KhmdK....",
    "...KhmKhmmdK....",
    "....KmmmmdK.....",
    "....KmmmddK.....",
    ".....KmmdK......",
    ".....KmddK......",
    "......KKK.......",
    "................",
    "................",
    "................",
], {"K": "2a1040", "h": "f0c8ff", "m": "b06ae8", "d": "6a3aa8"})


def dust(name, light, mid, dark, outline):
    art(name, [
        "................",
        "................",
        "................",
        "................",
        "................",
        "........h.......",
        "......KhmK...h..",
        ".....KhmmdK.....",
        "....KhmmmmdK....",
        "..h.KmmmmmdK....",
        "...KhmmdmmmdK...",
        "..KmmmmmmmmmdK..",
        "..KKKKKKKKKKKK..",
        "................",
        "................",
        "................",
    ], {"K": outline, "h": light, "m": mid, "d": dark})


dust("glowstone_dust", "fff8b0", "f8d050", "c89a20", "6a4a0a")
dust("sugar", "ffffff", "f0f0f0", "c8c8d0", "7a7a8a")
dust("redstone", "ff8a8a", "e01a1a", "8a0a0a", "3a0000")
dust("gunpowder", "c8c8c8", "7a7a7a", "4a4a4a", "1a1a1a")
dust("bone_meal", "ffffff", "e8e8f0", "a8a8c0", "5a5a70")


def bottle(name, liquid):
    pal = {"K": "2a3a40", "g": "d8f0f8", "c": "8a5a2e", "l": liquid or "d8f0f8", "L": liquid or "c0e0e8"}
    if liquid:
        pal["L"] = "%02x%02x%02x" % tuple(int(v * 0.75) for v in rgba(liquid)[:3])
    art(name, [
        "................",
        "......KKKK......",
        "......KccK......",
        "......KggK......",
        ".......KK.......",
        "......KggK......",
        ".....KgggK......",
        "....KgllllK.....",
        "...KgllllllK....",
        "...KglllllLK....",
        "...KglllllLK....",
        "...KgllllLLK....",
        "....KLLLLLK.....",
        ".....KKKKK......",
        "................",
        "................",
    ], pal)


bottle("glass_bottle", None)
bottle("honey_bottle", "f8a828")

art("paper", [
    "................",
    "................",
    "...KKKKKKKKK....",
    "...KwwwwwwwwK...",
    "...KwwwwwwwwK...",
    "...KwwwwwwwwK...",
    "...KwwwwwwwwK...",
    "...KwwwwwwwwK...",
    "...KwwwwwwwwK...",
    "...KwwwwwwwwK...",
    "...KwwwwwwwwK...",
    "...KwwwwwwwsK...",
    "...KwwwwwwssK...",
    "...KKKKKKKKK....",
    "................",
    "................",
], {"K": "8a8a80", "w": "f8f8f0", "s": "d0d0c8"})

art("book", [
    "................",
    "................",
    "...KKKKKKKKK....",
    "..KbbbbbbbbwK...",
    "..KbbbbbbbbwK...",
    "..KbbyybbbbwK...",
    "..KbbbbbbbbwK...",
    "..KbbbbbbbbwK...",
    "..KbbbbbbbbwK...",
    "..KbbbbbbbbwK...",
    "..KbbbbbbbbwK...",
    "..KBBBBBBBBwK...",
    "...KKKKKKKKK....",
    "................",
    "................",
    "................",
], {"K": "2a1a0a", "b": "8a4a2a", "B": "5a2a1a", "w": "f0e8d8", "y": "d8b84a"})

art("sugar_cane", [
    "................",
    "....K.....K.....",
    "...KgK...KgK....",
    "...KgK...KgK....",
    "...KdK...KdK..K.",
    "...KgK...KgK.KgK",
    "...KgKK..KgK.KgK",
    "...KgKlK.KdK.KdK",
    "...KdK.K.KgK.KgK",
    "...KgK...KgKKKgK",
    "...KgK...KgK.KdK",
    "...KgK...KdK.KgK",
    "...KdK...KgK.KgK",
    "...KgK...KgK.KgK",
    "...KKK...KKK.KKK",
    "................",
], {"K": "1a3a0a", "g": "8ad850", "d": "4a9a2a", "l": "6ac83a"})

art("wheat", [
    "................",
    ".........KK.....",
    "........KyyK....",
    ".......KyYyK....",
    "......KyYyK.....",
    ".....KyYyK......",
    "....KyYyK.......",
    "....KyyK........",
    "...KgKK.........",
    "...KgK..........",
    "..KgK...........",
    "..KgK...........",
    ".KgK............",
    ".KK.............",
    "................",
    "................",
], {"K": "4a3a0a", "y": "f0d060", "Y": "c8a030", "g": "8aa83a"})

art("wheat_seeds", [
    "................",
    "................",
    "................",
    "................",
    "........K.......",
    ".......KgK......",
    "....K...K..K....",
    "...KgK....KgK...",
    "....K..K...K....",
    ".......KgK......",
    "..K.....K...K...",
    ".KgK.......KgK..",
    "..K....K....K...",
    ".......KgK......",
    "........K.......",
    "................",
], {"K": "1a3a0a", "g": "6ac83a"})

art("cocoa_beans", [
    "................",
    "................",
    "................",
    "................",
    "....KKK.........",
    "...KbbbK........",
    "..KbBbbbK.......",
    "..KbbBbbK..KKK..",
    "..KbbbBbK.KbbbK.",
    "...KbbbK.KbBbbbK",
    "....KKK..KbbBbbK",
    "..........KbbBK.",
    "...........KKK..",
    "................",
    "................",
    "................",
], {"K": "2a1408", "b": "8a5028", "B": "5a3018"})

art("dried_kelp", [
    "................",
    "................",
    "..........KK....",
    ".........KgK....",
    "........KgdK....",
    ".......KgdK.....",
    "......KgdgK.....",
    ".....KgdgK......",
    ".....KdgdK......",
    "....KgdgK.......",
    "....KdgK........",
    "...KgdK.........",
    "...KgK..........",
    "...KK...........",
    "................",
    "................",
], {"K": "0a1a08", "g": "3a5a2a", "d": "2a3a1a"})

art("rotten_flesh", [
    "................",
    "................",
    "................",
    "......KKKK......",
    "....KKrrrrK.....",
    "...KrrgrrrrK....",
    "..KrrrrrgrrrK...",
    "..KrgrrrrrrrK...",
    "..KrrrrRrrgrK...",
    "...KrrRRrrrK....",
    "...KrrrrrrrK....",
    "....KrrgrrK.....",
    ".....KKKKK......",
    "................",
    "................",
    "................",
], {"K": "3a1a0a", "r": "b0583a", "R": "7a3020", "g": "6a8a3a"})

art("cauldron", [
    "................",
    "................",
    "..KKKKKKKKKKKK..",
    "..KmhhhhhhhhmK..",
    "..KKKKKKKKKKKK..",
    "..KmmKKKKKKmmK..",
    "..KmK......KmK..",
    "..KmmKKKKKKmmK..",
    "..KmmmmmmmmmmK..",
    "..KmmmmmmmmmdK..",
    "..KmmmmmmmmddK..",
    "..KdKddddddKdK..",
    "..KdK.KKKK.KdK..",
    "..KKK......KKK..",
    "................",
    "................",
], {"K": "1a1a1a", "h": "8a8a8a", "m": "5a5a5a", "d": "3a3a3a"})

art("brewing_stand", [
    "................",
    ".......KK.......",
    "......KhhK......",
    ".......KK.......",
    ".......Kd.......",
    "..KK...Kd...KK..",
    ".KggK..Kd..KggK.",
    ".KrrKKKKdKKKrrK.",
    ".KrrK..Kd..KrrK.",
    "..KK...Kd...KK..",
    ".......Kd.......",
    "....KKKKKKKK....",
    "...KmmmmmmmmK...",
    "...KddddddddK...",
    "....KKKKKKKK....",
    "................",
], {"K": "1a1a1a", "h": "f8d84a", "d": "6a4a2a", "g": "d8f0f8", "r": "d83a6a", "m": "8a8a8a"})


def get(material):
    """Icon for a lower-case material name, or None."""
    return ICONS.get(material)
