#!/usr/bin/env python3
"""Zips src/main/resources/pack/ into release/KushCraft-pack.zip (for hosting the
resource pack on GitHub/Dropbox when the server cannot open port 8163)."""
import os
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(HERE)
PACK = os.path.join(PROJECT, "src", "main", "resources", "pack")
OUT = os.path.join(PROJECT, "release", "KushCraft-pack.zip")

os.makedirs(os.path.dirname(OUT), exist_ok=True)
names = []
for root, _, files in os.walk(PACK):
    for f in files:
        full = os.path.join(root, f)
        names.append(os.path.relpath(full, PACK).replace(os.sep, "/"))
names.sort()
with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:
    for n in names:
        info = zipfile.ZipInfo(n, (1980, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        with open(os.path.join(PACK, n), "rb") as fh:
            z.writestr(info, fh.read())
print(f"wrote {OUT} ({len(names)} files, {os.path.getsize(OUT) // 1024} KB)")
