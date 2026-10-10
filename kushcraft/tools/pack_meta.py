"""pack.mcmeta, pack icon and the GUI font (negative spaces + backgrounds)."""
import json
import os

from PIL import Image

G = None

# Negative / positive space characters.  Must match dev.kushcraft.menus.GuiFont.
SPACES = {}
for i, n in enumerate((1, 2, 4, 8, 16, 32, 64, 128, 256)):
    SPACES[chr(0xF801 + i)] = -n
    SPACES[chr(0xF821 + i)] = n

# Menu backgrounds in glyph order: the n-th one is drawn by U+E000+n.
# Must be the same list as GUIS in dev.kushcraft.menus.GuiFont (validate_pack.py checks).
GUIS = ["shop", "drugs", "trade", "cartel", "top", "awards", "cook", "roll", "dry", "mix", "recipe", "list",
        "gear", "worker", "guide", "admin"]
GUI_GLYPHS = {name: chr(0xE000 + i) for i, name in enumerate(GUIS)}


def generate(g):
    global G
    G = g
    import gui
    providers = [{"type": "space", "advances": SPACES}]
    for name, ch in GUI_GLYPHS.items():
        w, h, rows = gui.LAYOUTS[name]
        providers.append({"type": "bitmap", "file": f"{G.NS}:gui/{name}.png", "ascent": 13, "height": h,
                          "chars": [ch]})
    G.save_json({"providers": providers}, "font/gui.json")

    # Minecraft 26.3 uses resource pack format 97.1 (version.json of the 26.3 server).
    # The new format has only min_format / max_format; pack_format and
    # supported_formats are the pre-1.21.9 fields and must not be mixed in.
    # max_format is open-ended so clients of later 26.x patches don't get an
    # "incompatible pack" warning.
    meta = {
        "pack": {
            "description": "\u00a7aKushCraft \u00a77textures & models",
            "min_format": 97,
            "max_format": 120,
        }
    }
    with open(os.path.join(G.PACK, "pack.mcmeta"), "w") as f:
        json.dump(meta, f, indent=1)

    # pack icon: the fresh bud on a dark circle
    icon = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for y in range(64):
        for x in range(64):
            if (x - 31.5) ** 2 + (y - 31.5) ** 2 < 31 ** 2:
                icon.putpixel((x, y), (24, 40, 20, 255))
    layers, _ = G.GENERATED["bud_fresh"]
    bud = G.composite(layers, 0x8ad04a)
    bud = bud.resize((bud.width * 48 // bud.width, 48), Image.NEAREST) if bud.width == 16 else bud.resize((64, 64), Image.NEAREST)
    icon.alpha_composite(bud, ((64 - bud.width) // 2, (64 - bud.height) // 2))
    icon.save(os.path.join(G.PACK, "pack.png"))
