"""Recipe pictures: a crafting-table style image for every KushCraft recipe.

They are used three ways:
 - glyphs of the kush:book font, shown on the handbook's recipe pages
 - docs/recipes_preview.png for the README
 - src/main/resources/recipe_book.json tells the plugin which character
   shows which recipe, plus a signature of the recipe that the plugin's
   self test compares with the real recipes (so the pictures can't drift
   away from the game).

RECIPES below MUST match dev.kushcraft.recipe.Recipes and
dev.kushcraft.lab.LabRecipe; the self test fails if they don't.
"""
import json
import os

from PIL import Image

import vanilla_icons

G = None
FIRST_CHAR = 0xE100
W, H = 112, 58

# ---------------------------------------------------------------------------
# recipe data
# ---------------------------------------------------------------------------
# crafting: (result id, amount, shape rows or None for shapeless, key/ingredients)
CRAFTING = [
    ("lab_station", 1, None, ["crafting_table", "furnace", "iron_ingot", "iron_ingot", "iron_ingot", "glass_bottle",
                              "glass_bottle"]),
    ("grow_lamp", 1, None, ["lantern", "iron_ingot", "iron_ingot", "redstone", "glowstone_dust"]),
    ("planter_box", 2, None, ["dirt", "dirt", "bone_meal", "planks", "planks", "planks"]),
    ("dealer", 1, None, ["chest", "gold_ingot", "gold_ingot"]),
    ("bong", 1, None, ["glass_bottle", "glass", "glass", "glass"]),
    ("rolling_papers", 3, None, ["paper"]),
    ("blunt_wrap", 2, None, ["paper", "cocoa_beans"]),
    ("fertilizer", 4, None, ["bone_meal", "bone_meal", "rotten_flesh"]),
    ("lab_solvent", 3, None, ["glass_bottle", "sugar"]),
    ("grower_guide", 1, None, ["book", "wheat_seeds"]),
]

# cook: (enum name, result id, amount, seconds, [(ingredient, count)]); "kush:" = KushCraft item.
# Same order as dev.kushcraft.lab.LabRecipe.
COOK = [
    ("VAPE_PEN", "vape_pen", 1, 40, [("kush:bud_dried", 4), ("kush:lab_solvent", 1), ("iron_nugget", 2),
                                     ("glass_pane", 1)]),
    ("SHROOM_TEA", "shroom_tea", 2, 20, [("kush:magic_mushroom", 3), ("potion", 1)]),
    ("SHROOM_CHOCOLATE", "shroom_chocolate", 3, 35, [("kush:magic_mushroom", 2), ("cocoa_beans", 2), ("sugar", 1)]),
    ("ERGOT", "ergot", 2, 15, [("wheat", 4)]),
    ("ERGOT_EXTRACT", "ergot_extract", 2, 45, [("kush:ergot", 3), ("kush:lab_solvent", 1)]),
    ("LUCID_TAB", "lucid_tab", 6, 30, [("kush:ergot_extract", 1), ("paper", 2)]),
    ("MESCALINE", "mescaline", 3, 40, [("kush:peyote_button", 4), ("kush:lab_solvent", 1)]),
    ("DMT", "dmt", 3, 50, [("glow_berries", 3), ("kush:lab_solvent", 1)]),
    ("AYAHUASCA", "ayahuasca", 2, 50, [("kush:dmt", 1), ("vine", 2), ("potion", 1)]),
    ("COCA_PASTE", "coca_paste", 2, 40, [("kush:coca_leaves", 6), ("kush:lab_solvent", 1)]),
    ("COCAINE", "cocaine", 3, 50, [("kush:coca_paste", 2), ("kush:lab_solvent", 1)]),
    ("CRACK", "crack", 2, 30, [("kush:cocaine", 1), ("potion", 1), ("bone_meal", 1)]),
    ("BLUE_CRYSTAL", "blue_crystal", 4, 60, [("lapis_lazuli", 3), ("kush:lab_solvent", 2), ("redstone", 1)]),
    ("ECSTASY", "ecstasy", 4, 40, [("pink_dye", 2), ("sugar", 2), ("kush:lab_solvent", 1)]),
    ("PIXIE_DUST", "pixie_dust", 4, 40, [("glowstone_dust", 2), ("sugar", 2)]),
    ("ANGEL_DUST", "angel_dust", 3, 50, [("gunpowder", 3), ("kush:lab_solvent", 1)]),
    ("SPEED", "speed", 4, 45, [("redstone", 2), ("sugar", 2), ("kush:lab_solvent", 1)]),
    ("OPIUM", "opium", 2, 30, [("kush:poppy_pod", 3)]),
    ("MORPHINE", "morphine", 2, 45, [("kush:opium", 2), ("kush:lab_solvent", 1)]),
    ("HEROIN", "heroin", 2, 60, [("kush:morphine", 2), ("kush:lab_solvent", 1)]),
    ("OXY", "oxy", 3, 45, [("kush:morphine", 1), ("sugar", 1), ("kush:lab_solvent", 1)]),
    ("COUGH_SYRUP", "cough_syrup", 2, 30, [("kush:opium", 1), ("honey_bottle", 1)]),
    ("LEAN", "lean", 2, 30, [("kush:cough_syrup", 1), ("sugar", 1), ("purple_dye", 1)]),
    ("KETAMINE", "ketamine", 4, 45, [("nether_wart", 3), ("kush:lab_solvent", 1)]),
    ("XANNY_BARS", "xanny_bars", 6, 40, [("white_dye", 2), ("sugar", 1), ("kush:lab_solvent", 1)]),
    ("MOONSHINE", "moonshine", 3, 50, [("wheat", 4), ("sugar", 2), ("potion", 1)]),
    ("LAUGHING_GAS", "laughing_gas", 4, 30, [("slime_ball", 1), ("iron_nugget", 2), ("kush:lab_solvent", 1)]),
]

# other Drug Lab tabs: (id, station text, result, amount, [(ingredient, count)])
OTHER = [
    ("roll_joint", ["DRUG LAB", "ROLL"], "joint", 1, [("kush:bud_dried", 1), ("kush:rolling_papers", 1)]),
    ("roll_blunt", ["DRUG LAB", "ROLL"], "blunt", 1, [("kush:bud_dried", 2), ("kush:blunt_wrap", 1)]),
    ("dry_bud", ["DRUG LAB", "DRY 30S"], "bud_dried", 1, [("kush:bud_fresh", 1)]),
    ("mix_strain", ["DRUG LAB", "MIX: RANDOM"], "seed_pack", 3, [("kush:seed_pack", 1), ("kush:seed_pack", 1)]),
]

NAMES = {
    "lab_station": "Drug Lab", "grow_lamp": "Grow Lamp", "planter_box": "Planter", "dealer": "Dealer Stand",
    "bong": "Bong", "rolling_papers": "Rolling Papers", "blunt_wrap": "Blunt Wrap", "fertilizer": "Fertilizer",
    "lab_solvent": "Lab Solvent", "catalyst": "Catalyst", "grower_guide": "KushCraft Menu", "hash": "Hash",
    "moon_rock": "Moon Rock", "space_brownie": "Space Brownie", "shroom_tea": "Shroom Tea", "lucid_tab": "LSD",
    "blue_crystal": "Meth", "cocaine": "Cocaine", "heroin": "Heroin", "pixie_dust": "Pixie Dust",
    "joint": "Joint", "blunt": "Blunt", "bud_dried": "Dried Bud", "seed_pack": "Your own strain",
    "gummies": "THC Gummies", "opium": "Opium", "lean": "Lean", "ecstasy": "Ecstasy", "crack": "Crack Rock",
    "ketamine": "Ketamine", "mescaline": "Mescaline", "dmt": "DMT", "angel_dust": "Angel Dust",
    "wax": "Wax", "vape_pen": "Vape Pen", "ergot": "Ergot", "kief": "Kief", "canna_butter": "Canna Butter",
    "ergot_extract": "Ergot Extract", "coca_paste": "Coca Paste", "morphine": "Morphine Base",
    "cough_syrup": "Cough Syrup", "shroom_chocolate": "Shroom Chocolate", "ayahuasca": "Ayahuasca",
    "speed": "Speed", "oxy": "Oxy Pills", "xanny_bars": "Xanny Bars", "moonshine": "Moonshine",
    "laughing_gas": "Laughing Gas",
}

MACHINES = {"lab_station": "block/lab_station", "grow_lamp": "block/grow_lamp",
            "planter_box": "block/planter_box", "dealer": "block/dealer"}


# ---------------------------------------------------------------------------
# signatures (same format as RecipeBook.signature in Java)
# ---------------------------------------------------------------------------
def rows3(shape):
    """A crafting shape padded to 3 rows of 3 (like the game's grid)."""
    return [r.ljust(3) for r in shape] + ["   "] * (3 - len(shape))


def craft_sig(result, amount, shape, key):
    if shape:
        cells = []
        for row in rows3(shape):
            for ch in row.ljust(3):
                cells.append(key[ch] if ch != " " else "-")
        body = ",".join(cells)
    else:
        body = ",".join(sorted(key))
    return f"craft:kush:{result}x{amount}:{body}"


def cook_sig(result, amount, seconds, ingredients):
    return f"cook:kush:{result}x{amount}:{seconds}s:" + ",".join(f"{n}*{c}" for n, c in ingredients)


# ---------------------------------------------------------------------------
# drawing
# ---------------------------------------------------------------------------
_ICON_CACHE = {}


def icon(name):
    """16x16 icon for 'kush:<id>', a KushCraft result id, or a vanilla material."""
    if name in _ICON_CACHE:
        return _ICON_CACHE[name]
    kid = name[5:] if name.startswith("kush:") else name
    img = None
    if kid in MACHINES:
        import render_models
        render_models.G = G
        big = render_models.render(os.path.join(G.ASSETS, "models", MACHINES[kid] + ".json"), 96, 0x6abe3a)
        big = big.crop(big.getbbox())
        f = 16 / max(big.width, big.height)
        small = big.resize((max(1, round(big.width * f)), max(1, round(big.height * f))), Image.LANCZOS)
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        img.alpha_composite(small, ((16 - small.width) // 2, (16 - small.height) // 2))
    elif kid in G.GENERATED and (name.startswith("kush:") or vanilla_icons.get(kid) is None):
        layers, tinted = G.GENERATED[kid]
        img = G.composite(layers, 0x6abe3a if tinted else None)
        if img.width != 16:
            img = shrink16(img)
    elif kid == "planks":
        img = vanilla_icons.get("oak_planks")
    else:
        img = vanilla_icons.get(kid)
    if img is None:
        raise SystemExit(f"recipe_images: no icon for {name}")
    _ICON_CACHE[name] = img
    return img


def shrink16(img):
    """32px art -> 16px: average colours, keep a crisp silhouette."""
    small = img.resize((16, 16), Image.BOX)
    px = small.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            # Pillow resizes RGBA with premultiplied alpha, so the colours are fine as they are
            px[x, y] = (0, 0, 0, 0) if a < 90 else (r, g, b, 255 if a > 160 else a)
    return small


GRAY = (198, 198, 198, 255)
DARK = (55, 55, 55, 255)
WHITE = (255, 255, 255, 255)
SLOT = (139, 139, 139, 255)


def slot(img, x, y, w=18):
    for yy in range(y, y + w):
        for xx in range(x, x + w):
            c = SLOT
            if xx == x or yy == y:
                c = DARK
            elif xx == x + w - 1 or yy == y + w - 1:
                c = WHITE
            img.putpixel((xx, yy), c)


def number(img, x, y, n):
    """Item count in the bottom-right corner (white with shadow)."""
    import gui
    s = str(n)
    x -= len(s) * 4 - 1
    gui.text(img, x, y, s, WHITE, shadow=(63, 63, 63, 255))


def panel():
    img = Image.new("RGBA", (W, H), GRAY)
    for x in range(W):
        img.putpixel((x, 0), WHITE)
        img.putpixel((x, H - 1), (85, 85, 85, 255))
    for y in range(H):
        img.putpixel((0, y), WHITE)
        img.putpixel((W - 1, y), (85, 85, 85, 255))
    for p in ((0, 0), (W - 1, 0), (0, H - 1), (W - 1, H - 1)):
        img.putpixel(p, (0, 0, 0, 0))
    return img


def arrow(img, x, y):
    col = (139, 139, 139, 255)
    for i in range(12):
        for j in range(3):
            img.putpixel((x + i, y + 6 + j), col)
    for k in range(7):
        for j in range(-k, k + 1):
            if 0 <= 7 + j < 16:
                img.putpixel((x + 12 + (6 - k), y + 7 + j), col)


def draw(station, cells, result, amount):
    """cells: 9 entries of (icon name, count) or None."""
    import gui
    img = panel()
    for i, cell in enumerate(cells):
        x, y = 2 + (i % 3) * 18, 2 + (i // 3) * 18
        slot(img, x, y)
        if cell:
            img.alpha_composite(icon(cell[0]), (x + 1, y + 1))
            if cell[1] > 1:
                number(img, x + 17, y + 12, cell[1])
    for i, line in enumerate(station):
        gui.text(img, 60, 4 + i * 7, line, (63, 63, 63, 255))
    arrow(img, 60, 22)
    slot(img, 82, 16, 26)
    img.alpha_composite(icon("kush:" + result), (87, 21))
    if amount > 1:
        number(img, 106, 36, amount)
    return img


def spread(ingredients):
    pos = {1: [4], 2: [3, 5], 3: [3, 4, 5], 4: [1, 3, 5, 7]}.get(len(ingredients), list(range(9)))
    cells = [None] * 9
    for p, ing in zip(pos, ingredients):
        cells[p] = ing
    return cells


def all_recipes():
    """[(id, title, image, sig)]"""
    out = []
    for result, amount, shape, key in CRAFTING:
        if shape:
            cells = [None if ch == " " else (key[ch], 1) for row in rows3(shape) for ch in row]
        else:
            cells = [(n, 1) for n in key] + [None] * (9 - len(key))
        out.append(("craft_" + result, NAMES[result], draw(["CRAFTING", "TABLE"], cells, result, amount),
                    craft_sig(result, amount, shape, key)))
    for enum, result, amount, seconds, ings in COOK:
        out.append(("cook_" + enum.lower(), NAMES[result],
                    draw(["DRUG LAB", f"COOK {seconds}S"], spread(ings), result, amount),
                    cook_sig(result, amount, seconds, ings)))
    for rid, station, result, amount, ings in OTHER:
        out.append((rid, NAMES[result], draw(station, spread(ings), result, amount), None))
    return out


def generate(g):
    global G
    G = g
    recipes = all_recipes()
    providers = []
    index = []
    for i, (rid, title, img, sig) in enumerate(recipes):
        G.save_png(img, f"book/{rid}")
        ch = chr(FIRST_CHAR + i)
        # ascent 7 puts the top of the picture at the top of its line in a book
        providers.append({"type": "bitmap", "file": f"{G.NS}:book/{rid}.png", "ascent": 7, "height": H,
                          "chars": [ch]})
        entry = {"id": rid, "char": ch, "title": title}
        if sig:
            entry["sig"] = sig
        index.append(entry)
    G.save_json({"providers": providers}, "font/book.json")
    path = os.path.join(G.PROJECT, "src", "main", "resources", "recipe_book.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump({"lines": (H + 8) // 9, "recipes": index}, f, indent=1, ensure_ascii=False)

    # docs sheet: 4 per row with the title above each picture
    import gui
    cols = 4
    cw, chh = W + 12, H + 16
    rows = (len(recipes) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cw + 12, rows * chh + 12), (46, 52, 44, 255))
    for i, (rid, title, img, _) in enumerate(recipes):
        x, y = 12 + (i % cols) * cw, 12 + (i // cols) * chh
        gui.text(sheet, x, y, title, (240, 230, 160, 255), shadow=(20, 20, 20, 255))
        sheet.alpha_composite(img, (x, y + 8))
    sheet = sheet.resize((sheet.width * 3, sheet.height * 3), Image.NEAREST)
    out = os.path.join(G.PREVIEW_DIR, "recipes_preview.png")
    G.ensure(out)
    sheet.save(out)
