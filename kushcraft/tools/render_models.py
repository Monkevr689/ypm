"""Tiny isometric software renderer for the generated block/plant models.
Only used to produce preview images (docs/blocks_preview.png)."""
import json
import math
import os

from PIL import Image, ImageDraw

G = None
TEX_CACHE = {}


def load_tex(ref):
    ns, path = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
    if ref not in TEX_CACHE:
        p = os.path.join(G.PACK, "assets", ns, "textures", path + ".png")
        img = Image.open(p).convert("RGBA") if os.path.exists(p) else Image.new("RGBA", (16, 16), (255, 0, 255, 255))
        if os.path.exists(p + ".mcmeta") and img.height > img.width:
            img = img.crop((0, 0, img.width, img.width))  # first frame of an animation
        TEX_CACHE[ref] = img
    return TEX_CACHE[ref]


def resolve(textures, t):
    seen = 0
    while t.startswith("#") and seen < 10:
        t = textures.get(t[1:], "missing")
        seen += 1
    return t


def rot(p, origin, axis, angle, rescale):
    a = math.radians(angle)
    x, y, z = (p[0] - origin[0], p[1] - origin[1], p[2] - origin[2])
    c, s = math.cos(a), math.sin(a)
    if axis == "y":
        x, z = x * c + z * s, -x * s + z * c
    elif axis == "x":
        y, z = y * c - z * s, y * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    if rescale:
        k = 1 / math.cos(a) if abs(math.cos(a)) > 1e-6 else 1
        if axis == "y":
            x, z = x * k, z * k
        elif axis == "x":
            y, z = y * k, z * k
        else:
            x, y = x * k, y * k
    return (x + origin[0], y + origin[1], z + origin[2])


# face -> (corner function(u,v in 0..1) -> xyz, normal)
def face_point(f, fr, to, u, v):
    x0, y0, z0 = fr
    x1, y1, z1 = to
    if f == "north":
        return (x1 - (x1 - x0) * u, y1 - (y1 - y0) * v, z0), (0, 0, -1)
    if f == "south":
        return (x0 + (x1 - x0) * u, y1 - (y1 - y0) * v, z1), (0, 0, 1)
    if f == "east":
        return (x1, y1 - (y1 - y0) * v, z1 - (z1 - z0) * u), (1, 0, 0)
    if f == "west":
        return (x0, y1 - (y1 - y0) * v, z0 + (z1 - z0) * u), (-1, 0, 0)
    if f == "up":
        return (x0 + (x1 - x0) * u, y1, z0 + (z1 - z0) * v), (0, 1, 0)
    return (x0 + (x1 - x0) * u, y0, z1 - (z1 - z0) * v), (0, -1, 0)


def render(model_path, size=160, tint=0x9a4fd4, yaw=35):
    """tint: colour of tintindex 0, or {tintindex: colour}. model_path: one
    model or a list (drawn together, like a composite item model)."""
    paths = model_path if isinstance(model_path, list) else [model_path]
    tints = tint if isinstance(tint, dict) else {0: tint}
    elements = []
    for mp in paths:
        m = json.load(open(mp))
        for e in m.get("elements", []):
            elements.append((e, m.get("textures", {})))
    d = (-math.sin(math.radians(yaw)), -0.62, -math.cos(math.radians(yaw)))
    n = math.sqrt(sum(c * c for c in d))
    d = tuple(c / n for c in d)
    right = (math.cos(math.radians(yaw)), 0, -math.sin(math.radians(yaw)))
    up = (d[1] * right[2] - d[2] * right[1], d[2] * right[0] - d[0] * right[2], d[0] * right[1] - d[1] * right[0])
    up = tuple(-c for c in up)
    scale = size / 34.0
    polys = []
    for e, textures in elements:
        fr, to = e["from"], e["to"]
        r = e.get("rotation")
        for fname, f in e["faces"].items():
            _, normal = face_point(fname, fr, to, 0, 0)
            if r:
                normal = rot(normal, (0, 0, 0), r["axis"], r["angle"], False)
            if sum(normal[i] * d[i] for i in range(3)) >= 0:
                continue
            tex = load_tex(resolve(textures, f["texture"]))
            uv = f.get("uv", [0, 0, 16, 16])
            u0, v0, u1, v1 = uv
            tw, th = tex.size
            sx = tw / 16.0
            steps_u = max(1, int(abs(u1 - u0) * sx))
            steps_v = max(1, int(abs(v1 - v0) * sx))
            light = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}[fname]
            if e.get("shade") is False:
                light = 1.0
            for i in range(steps_u):
                for j in range(steps_v):
                    tu = u0 + (u1 - u0) * (i + 0.5) / steps_u
                    tv = v0 + (v1 - v0) * (j + 0.5) / steps_v
                    px = tex.getpixel((min(tw - 1, int(tu * sx)), min(th - 1, int(tv * sx))))
                    if px[3] < 20:
                        continue
                    col = px
                    ti = tints.get(f.get("tintindex"))
                    if ti is not None:
                        col = (px[0] * ((ti >> 16) & 255) // 255, px[1] * ((ti >> 8) & 255) // 255,
                               px[2] * (ti & 255) // 255, px[3])
                    col = (int(col[0] * light), int(col[1] * light), int(col[2] * light), col[3])
                    pts = []
                    for (a, b) in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                        p, _ = face_point(fname, fr, to, a / steps_u, b / steps_v)
                        if r:
                            p = rot(p, r["origin"], r["axis"], r["angle"], r.get("rescale", False))
                        pts.append(p)
                    cx = sum(p[0] for p in pts) / 4
                    cy = sum(p[1] for p in pts) / 4
                    cz = sum(p[2] for p in pts) / 4
                    depth = cx * d[0] + cy * d[1] + cz * d[2]
                    scr = []
                    for p in pts:
                        q = (p[0] - 8, p[1] - 8, p[2] - 8)
                        sxp = q[0] * right[0] + q[1] * right[1] + q[2] * right[2]
                        syp = q[0] * up[0] + q[1] * up[1] + q[2] * up[2]
                        scr.append((size / 2 + sxp * scale, size * 0.62 - syp * scale))
                    polys.append((depth, scr, col))
    polys.sort(key=lambda t: -t[0])
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    for _, scr, col in polys:
        dr.polygon(scr, fill=col)
    return img


def generate(g, names, out_name, tint=0x9a4fd4, size=160, bg=(70, 110, 160, 255)):
    global G
    G = g
    imgs = []
    for n in names:
        if isinstance(n, tuple):
            # (model names..., tints)
            *models, tn = n
            imgs.append(render([os.path.join(G.ASSETS, "models", x + ".json") for x in models], size, tn))
            continue
        p = os.path.join(G.ASSETS, "models", n + ".json")
        imgs.append(render(p, size, tint))
    sheet = Image.new("RGBA", (len(imgs) * (size + 8) + 8, size + 16), bg)
    for i, im in enumerate(imgs):
        sheet.alpha_composite(im, (8 + i * (size + 8), 8))
    path = os.path.join(G.PREVIEW_DIR, out_name)
    G.ensure(path)
    sheet.save(path)
