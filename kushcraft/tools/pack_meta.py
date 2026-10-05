"""pack.mcmeta, pack icon and the GUI font (negative spaces + backgrounds)."""
import json
import os

from PIL import Image

G = None

# Negative / positive space characters.  Must match dev.kushcraft.gui.GuiFont.
SPACES = {}
for i, n in enumerate((1, 2, 4, 8, 16, 32, 64, 128, 256)):
    SPACES[chr(0xF801 + i)] = -n
    SPACES[chr(0xF821 + i)] = n

GUI_GLYPHS = {"lab": "", "strain": "", "roller": "", "dealer": ""}


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

    meta = {
        "pack": {
            "description": "§aKushCraft §7textures & models",
            "pack_format": 46,
            "supported_formats": [46, 999],
            "min_format": 46,
            "max_format": 999,
        }
    }
    with open(os.path.join(G.PACK, "pack.mcmeta"), "w") as f:
        json.dump(meta, f, indent=1)

    # pack icon: the fresh bud scaled up on a dark circle
    icon = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for y in range(64):
        for x in range(64):
            if (x - 31.5) ** 2 + (y - 31.5) ** 2 < 31 ** 2:
                icon.putpixel((x, y), (24, 40, 20, 255))
    layers, _ = G.GENERATED["bud_fresh"]
    bud = G.composite(layers, 0x8ad04a).resize((48, 48), Image.NEAREST)
    icon.alpha_composite(bud, (8, 8))
    icon.save(os.path.join(G.PACK, "pack.png"))
