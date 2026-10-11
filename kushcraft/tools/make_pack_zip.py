#!/usr/bin/env python3
"""Zips src/main/resources/pack/ into release/KushCraft-pack-<version>.zip and
release/KushCraft-pack.zip (the same bytes). With "resource-pack.url: auto" the
plugin sends players the versioned file from GitHub, so every jar gets exactly
its own pack; a released versioned zip must never change afterwards."""
import os
import re
import shutil
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(HERE)
PACK = os.path.join(PROJECT, "src", "main", "resources", "pack")
VERSION = re.search(r"<artifactId>KushCraft</artifactId>\s*<version>([^<]+)</version>",
                    open(os.path.join(PROJECT, "pom.xml"), encoding="utf-8").read()).group(1)
OUT = os.path.join(PROJECT, "release", f"KushCraft-pack-{VERSION}.zip")
LATEST = os.path.join(PROJECT, "release", "KushCraft-pack.zip")

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
shutil.copyfile(OUT, LATEST)
print(f"wrote {OUT} and {os.path.basename(LATEST)} ({len(names)} files, {os.path.getsize(OUT) // 1024} KB)")
