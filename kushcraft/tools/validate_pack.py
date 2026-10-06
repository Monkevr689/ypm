#!/usr/bin/env python3
"""Sanity checks for the generated resource pack:
 - every item definition points at an existing model
 - every model texture exists (following parents inside our namespace)
 - font bitmaps exist and ascent/height are valid
 - every item model id used by the Java code exists
Exit code 1 on problems."""
import json
import os
import re
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(HERE)
PACK = os.path.join(PROJECT, "src", "main", "resources", "pack")
ASSETS = os.path.join(PACK, "assets")
JAVA = os.path.join(PROJECT, "src", "main", "java")

errors = []


def res(ref, kind, ext):
    ns, path = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
    return ns, os.path.join(ASSETS, ns, kind, path + ext)


def check_model(ref, seen):
    if ref in seen:
        return
    seen.add(ref)
    ns, path = res(ref, "models", ".json")
    if ns == "minecraft":
        return
    if not os.path.exists(path):
        errors.append(f"missing model {ref}")
        return
    m = json.load(open(path))
    for k, t in m.get("textures", {}).items():
        if t.startswith("#"):
            continue
        tns, tpath = res(t, "textures", ".png")
        if tns != "minecraft" and not os.path.exists(tpath):
            errors.append(f"model {ref}: missing texture {t}")
        tpath_rel = t.split(":", 1)[1] if ":" in t else t
        if tns != "minecraft" and not (tpath_rel.startswith("block/") or tpath_rel.startswith("item/")):
            # the block/item atlas only stitches these folders: anything else shows up magenta/black
            errors.append(f"model {ref}: texture {t} is outside block/ or item/ (not in the texture atlas)")
    for e in m.get("elements", []):
        for c in e["from"] + e["to"]:
            if c < -16 or c > 32:
                errors.append(f"model {ref}: coordinate {c} out of range")
        r = e.get("rotation")
        if r and r["angle"] not in (-45, -22.5, 0, 22.5, 45):
            errors.append(f"model {ref}: rotation {r['angle']}")
        for f in e["faces"].values():
            tk = f["texture"]
            if tk.startswith("#") and tk[1:] not in m.get("textures", {}):
                errors.append(f"model {ref}: face texture {tk} undefined")
            for v in f.get("uv", []):
                if v < 0 or v > 16:
                    errors.append(f"model {ref}: uv {v} out of range")
    if "parent" in m:
        check_model(m["parent"], seen)


ITEM_MODEL_TYPES = ("minecraft:model", "minecraft:composite", "minecraft:select", "minecraft:empty")


def model_refs(node, where):
    """Every model an item definition can show (composite / select trees)."""
    t = node.get("type")
    if t not in ITEM_MODEL_TYPES:
        errors.append(f"items/{where}: unexpected item model type {t}")
        return []
    if t == "minecraft:model":
        tints = node.get("tints", [])
        for tint in tints:
            if tint.get("type") not in ("minecraft:constant", "minecraft:custom_model_data"):
                errors.append(f"items/{where}: unexpected tint {tint}")
        return [node["model"]]
    if t == "minecraft:composite":
        return [r for m in node["models"] for r in model_refs(m, where)]
    if t == "minecraft:select":
        if node.get("property") != "minecraft:custom_model_data":
            errors.append(f"items/{where}: select on {node.get('property')}")
        out = []
        for case in node.get("cases", []):
            out += model_refs(case["model"], where)
        if "fallback" in node:
            out += model_refs(node["fallback"], where)
        return out
    return []


def check_animations():
    """Animated textures (Mythic looks) have a valid .mcmeta and whole frames."""
    tex_root = os.path.join(ASSETS, "kush", "textures")
    count = 0
    for root, _, files in os.walk(tex_root):
        for fn in files:
            if not fn.endswith(".png.mcmeta"):
                continue
            count += 1
            meta = json.load(open(os.path.join(root, fn)))
            anim = meta.get("animation")
            img = Image.open(os.path.join(root, fn[:-7]))
            fw = anim.get("width", img.width) if anim else 0
            fh = anim.get("height", img.width) if anim else 0
            if not anim or img.height % fh or img.width != fw or img.height // fh < 2:
                errors.append(f"bad animation {fn}")
    if count == 0:
        errors.append("no animated Mythic textures")


def check_strain_looks():
    """Bud items pick a shape for every BudShape and an overlay for every Mythic look."""
    strain_dir = os.path.join(JAVA, "dev", "kushcraft", "strain")
    shapes = [s.lower() for s in re.findall(r"^    ([A-Z]+)\(", open(os.path.join(strain_dir, "BudShape.java")).read(), re.M)]
    exotics = [s.lower() for s in re.findall(r"^    ([A-Z]+)\(", open(os.path.join(strain_dir, "Exotic.java")).read(), re.M)]
    exotics = [e for e in exotics if e != "none"]

    def cases(node):
        return sorted(c["when"] for c in node.get("cases", []))

    for item in ("bud_fresh", "bud_dried", "seed_pack", "plant_sativa_4", "plant_indica_3", "plant_hybrid_4"):
        path = os.path.join(ASSETS, "kush", "items", item + ".json")
        if not os.path.exists(path):
            errors.append(f"items/{item}.json missing")
            continue
        root = json.load(open(path))["model"]
        if root.get("type") != "minecraft:composite":
            errors.append(f"items/{item}.json has no Mythic overlay")
            continue
        overlay = root["models"][1]
        if overlay.get("index") != 1 or cases(overlay) != sorted(exotics):
            errors.append(f"items/{item}.json Mythic looks {cases(overlay)} != Exotic {exotics}")
        if item.startswith("bud_"):
            shape = root["models"][0]
            if shape.get("index") != 0 or sorted(cases(shape) + ["classic"]) != sorted(shapes):
                errors.append(f"items/{item}.json bud shapes {cases(shape)} != BudShape {shapes}")


def check_menus():
    """Every menu background used by Java has a glyph in the same order as
    tools/pack_meta.py, and the image has the right number of rows."""
    sys.path.insert(0, HERE)
    import pack_meta
    gui_src = open(os.path.join(JAVA, "dev", "kushcraft", "gui", "GuiFont.java"), encoding="utf-8").read()
    m = re.search(r"GUIS = List\.of\(([^;]*)\);", gui_src)
    java_guis = re.findall(r'"([a-z_]+)"', m.group(1)) if m else []
    if java_guis != pack_meta.GUIS:
        errors.append(f"GuiFont.GUIS {java_guis} != pack_meta.GUIS {pack_meta.GUIS}")
    gui_dir = os.path.join(JAVA, "dev", "kushcraft", "gui")
    import gui

    def rows_of(name):
        path = os.path.join(ASSETS, "kush", "textures", "gui", name + ".png")
        if name not in pack_meta.GUIS or not os.path.exists(path):
            errors.append(f"menu '{name}' has no background")
            return None
        return (Image.open(path).height - 114) // 18

    # /kush tab pages: TabMenu.Tab, 6 rows, same order as the tab bar drawn by gui.py
    tab_src = open(os.path.join(gui_dir, "TabMenu.java"), encoding="utf-8").read()
    tabs = re.findall(r'^        ([A-Z]+)\("', tab_src, re.M)
    if tabs != gui.TABS:
        errors.append(f"TabMenu tabs {tabs} != gui.TABS {gui.TABS}")
    for t in tabs:
        if rows_of(t.lower()) not in (None, 6):
            errors.append(f"tab page '{t.lower()}' background is not 6 rows")
    # Drug Lab pages: LabTabMenu.Tab, LabTabMenu.ROWS rows
    lab_src = open(os.path.join(gui_dir, "LabTabMenu.java"), encoding="utf-8").read()
    lab_tabs = re.findall(r'^        ([A-Z]+)\("', lab_src, re.M)
    lab_rows = int(re.search(r"static final int ROWS = (\d+);", lab_src).group(1))
    if lab_tabs != gui.LAB_TABS:
        errors.append(f"LabTabMenu tabs {lab_tabs} != gui.LAB_TABS {gui.LAB_TABS}")
    for t in lab_tabs:
        if rows_of(t.lower()) not in (None, lab_rows):
            errors.append(f"lab page '{t.lower()}' background is not {lab_rows} rows")
    # other menus: super(player, rows, "name", ...) (rows may be the class's ROWS constant)
    for fn in sorted(os.listdir(gui_dir)):
        src = open(os.path.join(gui_dir, fn), encoding="utf-8").read()
        const = re.search(r"static final int ROWS = (\d+);", src)
        for rows, name in re.findall(r'super\(player, (\w+), "([a-z_]+)"', src):
            if rows.startswith("Tab."):
                rows = "6"  # a page inside a /kush tab
            elif rows == "ROWS" and const:
                rows = const.group(1)
            if not rows.isdigit():
                continue
            r = rows_of(name)
            if r is not None and r != int(rows):
                errors.append(f"{fn}: {rows}-row menu but gui/{name}.png is drawn for {r} rows")

    def java_int(fn, const):
        src = open(os.path.join(gui_dir, fn), encoding="utf-8").read()
        m = re.search(r"static final int " + const + r" = (\d+);", src)
        return int(m.group(1)) if m else None

    for fn, const, expect in (("ShopMenu.java", "SEEDS", gui.SHOP_SEEDS), ("GearMenu.java", "GEAR", gui.GEAR_SLOTS),
                              ("AwardsMenu.java", "SLOTS", gui.AWARD_SLOTS)):
        if java_int(fn, const) != expect:
            errors.append(f"{fn} {const} = {java_int(fn, const)} but tools/gui.py draws {expect}")
    # framed slots that Java and the backgrounds must agree on
    gear_src = open(os.path.join(gui_dir, "GearMenu.java"), encoding="utf-8").read()
    hire = re.search(r"static final int\[\] HIRE = \{([^}]*)\};", gear_src)
    hire_slots = tuple((int(a), int(b)) for a, b in re.findall(r"at\((\d+), (\d+)\)", hire.group(1))) if hire else ()
    if hire_slots != tuple(gui.HIRE_SLOTS):
        errors.append(f"GearMenu HIRE {hire_slots} but tools/gui.py draws {gui.HIRE_SLOTS}")
    worker_types = len(re.findall(r'^    [A-Z]+\("', open(os.path.join(JAVA, "dev", "kushcraft", "worker", "WorkerType.java"),
                                                        encoding="utf-8").read(), re.M))
    if worker_types != len(gui.HIRE_SLOTS):
        errors.append(f"{worker_types} worker types but {len(gui.HIRE_SLOTS)} hiring posters")
    trade_src = open(os.path.join(gui_dir, "TradeMenu.java"), encoding="utf-8").read()
    shelves = re.search(r"static final int\[\] SHELVES = \{([^}]*)\};", trade_src)
    shelf_slots = re.findall(r"at\((\d+), (\d+)\)", shelves.group(1)) if shelves else []
    if len(shelf_slots) != gui.TRADE_SHELVES or {int(r) for r, _ in shelf_slots} != {1, 5}:
        errors.append(f"TradeMenu SHELVES {shelf_slots} but tools/gui.py draws {gui.TRADE_SHELVES} in rows 1 and 5")
    cartel_src = open(os.path.join(gui_dir, "CartelMenu.java"), encoding="utf-8").read()
    for const, cell in (("HELP", (3, 1)), ("SHIPMENT", (3, 4)), ("TOP", (3, 7))):
        m = re.search(r"static final int " + const + r" = at\((\d+), (\d+)\);", cartel_src)
        if not m or (int(m.group(1)), int(m.group(2))) != cell:
            errors.append(f"CartelMenu {const} should be at {cell} (tools/gui.py cartel())")
    starter = open(os.path.join(JAVA, "dev", "kushcraft", "award", "Starter.java"), encoding="utf-8").read()
    steps = len(re.findall(r'^    [A-Z]+\("', starter, re.M))
    if steps != gui.GUIDE_STEPS:
        errors.append(f"{steps} starter steps but tools/gui.py guide() draws {gui.GUIDE_STEPS}")
    # slot frames drawn for the right number of things
    cat_src = open(os.path.join(JAVA, "dev", "kushcraft", "catalog", "Catalog.java"), encoding="utf-8").read()
    counts = [len(re.findall(r"Category\." + c + r"[,)]", cat_src)) for c in ("WEED", "PSYCH", "UPPERS", "DOWNERS", "GEAR")]
    if counts != gui.DRUG_ROWS:
        errors.append(f"Catalog rows {counts} != gui.DRUG_ROWS {gui.DRUG_ROWS}")
    recipe_src = open(os.path.join(JAVA, "dev", "kushcraft", "lab", "LabRecipe.java"), encoding="utf-8").read()
    enums = re.findall(r"^    ([A-Z_]+)\(ItemType\.", recipe_src, re.M)
    if len(enums) != gui.COOK_RECIPES:
        errors.append(f"{len(enums)} lab recipes but gui.COOK_RECIPES = {gui.COOK_RECIPES}")
    import recipe_images
    if [c[0] for c in recipe_images.COOK] != enums:
        errors.append(f"recipe_images.COOK order {[c[0] for c in recipe_images.COOK]} != LabRecipe {enums}")


def check_workers():
    """Every worker has a 64x64 skin and a hat model (dev.kushcraft.worker.WorkerType)."""
    src = open(os.path.join(JAVA, "dev", "kushcraft", "worker", "WorkerType.java"), encoding="utf-8").read()
    for name in re.findall(r'^    ([A-Z]+)\("', src, re.M):
        t = name.lower()
        skin = os.path.join(ASSETS, "kush", "textures", "entity", "worker", t + ".png")
        if not os.path.exists(skin):
            errors.append(f"worker {t} has no skin")
        elif Image.open(skin).size != (64, 64):
            errors.append(f"worker {t} skin is not 64x64")
        if not os.path.exists(os.path.join(ASSETS, "kush", "items", f"worker_hat_{t}.json")):
            errors.append(f"worker {t} has no hat item")
        if not os.path.exists(os.path.join(ASSETS, "kush", "items", f"worker_{t}.json")):
            errors.append(f"worker {t} has no contract item")


def check_recipe_book():
    """Recipe images used in the handbook exist in the book font."""
    index_path = os.path.join(PROJECT, "src", "main", "resources", "recipe_book.json")
    if not os.path.exists(index_path):
        errors.append("recipe_book.json missing (run tools/gen_assets.py)")
        return
    index = json.load(open(index_path, encoding="utf-8"))
    font = json.load(open(os.path.join(ASSETS, "kush", "font", "book.json"), encoding="utf-8"))
    chars = set()
    for p in font["providers"]:
        chars.update(p.get("chars", []))
    for e in index["recipes"]:
        if e["char"] not in chars:
            errors.append(f"recipe image for {e['id']} has no glyph in font/book.json")


def main():
    item_defs = set()
    seen = set()
    items_dir = os.path.join(ASSETS, "kush", "items")
    for fn in os.listdir(items_dir):
        item_defs.add(fn[:-5])
        d = json.load(open(os.path.join(items_dir, fn)))
        refs = model_refs(d["model"], fn)
        if not refs:
            errors.append(f"items/{fn} draws nothing")
        for ref in refs:
            check_model(ref, seen)

    font_dir = os.path.join(ASSETS, "kush", "font")
    for fn in sorted(os.listdir(font_dir)):
        font = json.load(open(os.path.join(font_dir, fn)))
        for p in font["providers"]:
            if p["type"] != "bitmap":
                continue
            _, path = res(p["file"], "textures", "")
            if not os.path.exists(path):
                errors.append(f"font bitmap missing {p['file']}")
                continue
            im = Image.open(path)
            if p["ascent"] > p["height"]:
                errors.append(f"font {p['file']}: ascent > height")
            if im.height != p["height"]:
                errors.append(f"font {p['file']}: height {p['height']} != image {im.height}")
            if im.width > 256 or im.height > 256:
                errors.append(f"font {p['file']}: glyph larger than 256px")

    check_menus()
    check_workers()
    check_recipe_book()
    check_animations()
    check_strain_looks()
    if not os.path.exists(os.path.join(ASSETS, "kush", "textures", "gui", "advancements", "kush.png")):
        errors.append("advancement tab background gui/advancements/kush.png missing")

    meta = json.load(open(os.path.join(PACK, "pack.mcmeta")))
    pk = meta.get("pack")
    if not isinstance(pk, dict):
        errors.append("pack.mcmeta has no pack section")
    else:
        if "min_format" not in pk or "max_format" not in pk:
            errors.append("pack.mcmeta needs min_format and max_format")
        for legacy in ("pack_format", "supported_formats"):
            if legacy in pk:
                errors.append(f"pack.mcmeta must not mix legacy '{legacy}' with min/max_format")
        if "description" not in pk:
            errors.append("pack.mcmeta has no description")

    # model ids used by Java
    used = set()
    for root, _, files in os.walk(JAVA):
        for fn in files:
            src = open(os.path.join(root, fn)).read()
            for m in re.finditer(r'(?:icon|tintedIcon)\("([a-z0-9_]+)"', src):
                if m.group(1).endswith("_"):
                    continue  # built from a number, e.g. "gauge_" + step
                used.add(m.group(1))
            for m in re.finditer(r'"(machine_[a-z_]+)"', src):
                used.add(m.group(1))
            for m in re.finditer(r'Keys\.model\("([a-z0-9_]+)"\)', src):
                used.add(m.group(1))
    enum_src = open(os.path.join(JAVA, "dev", "kushcraft", "item", "ItemType.java")).read()
    for m in re.finditer(r'\("[^"]+", "([a-z0-9_]+)"', enum_src):
        used.add(m.group(1))
    effects = open(os.path.join(JAVA, "dev", "kushcraft", "effect", "EffectType.java")).read()
    for m in re.finditer(r'"(effect_[a-z_]+)"', effects):
        used.add(m.group(1))
    types = open(os.path.join(JAVA, "dev", "kushcraft", "strain", "StrainType.java")).read()
    for m in re.finditer(r'"(type_[a-z]+)"', types):
        used.add(m.group(1))
    for src_name in ("gui/TabMenu.java", "gui/LabTabMenu.java"):
        src = open(os.path.join(JAVA, "dev", "kushcraft", src_name)).read()
        for m in re.finditer(r'^        [A-Z]+\("[^"]+", "([a-z0-9_]+)"', src, re.M):
            used.add(m.group(1))
    awards = open(os.path.join(JAVA, "dev", "kushcraft", "award", "Award.java")).read()
    for m in re.finditer(r'\("[^"]+", "[^"]+", "([a-z0-9_]+)", \d+', awards):
        used.add(m.group(1))
        used.add(m.group(1) + "_locked")
    for kind in ("sativa", "indica", "hybrid"):
        for st in range(5):
            used.add(f"plant_{kind}_{st}")
    dry_src = open(os.path.join(JAVA, "dev", "kushcraft", "gui", "DryMenu.java")).read()
    steps = int(re.search(r"GAUGE_STEPS = (\d+);", dry_src).group(1))
    for st in range(steps + 1):
        used.add(f"gauge_{st}")
    for st in range(4):
        for kind in ("mushroom", "coca", "poppy", "peyote"):
            used.add(f"plant_{kind}_{st}")
    for u in sorted(used):
        if u not in item_defs:
            errors.append(f"Java uses item model kush:{u} but items/{u}.json is missing")

    print(f"checked {len(item_defs)} item definitions, {len(seen)} models, {len(used)} model ids used by Java")
    if errors:
        for e in errors:
            print("ERROR:", e)
        sys.exit(1)
    print("resource pack OK")


if __name__ == "__main__":
    main()
