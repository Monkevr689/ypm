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


def main():
    item_defs = set()
    seen = set()
    items_dir = os.path.join(ASSETS, "kush", "items")
    for fn in os.listdir(items_dir):
        item_defs.add(fn[:-5])
        d = json.load(open(os.path.join(items_dir, fn)))
        check_model(d["model"]["model"], seen)

    font = json.load(open(os.path.join(ASSETS, "kush", "font", "gui.json")))
    for p in font["providers"]:
        if p["type"] == "bitmap":
            _, path = res(p["file"], "textures", "")
            if not os.path.exists(path):
                errors.append(f"font bitmap missing {p['file']}")
                continue
            im = Image.open(path)
            if p["ascent"] > p["height"]:
                errors.append(f"font {p['file']}: ascent > height")
            if im.height != p["height"]:
                errors.append(f"font {p['file']}: height {p['height']} != image {im.height}")

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
    for kind in ("sativa", "indica", "hybrid"):
        for st in range(5):
            used.add(f"plant_{kind}_{st}")
    for st in range(4):
        for kind in ("mushroom", "coca", "poppy"):
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
