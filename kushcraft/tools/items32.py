"""32x32 art for the drugs, weed products, harvest and supplies.

Each function returns an art.Sprite. Layer 1 (tinted with the strain colour
in game) is drawn in greys. Register with @item("name").
"""
import colorsys
import math

from art import (Sprite, c, mix, scale, alpha, grey, ramp, grey_ramp, poly, ellipse, rect, rounded,
                 thick_line, line_px, shift, edge, inside)

ITEMS = {}


def item(name):
    def deco(fn):
        ITEMS[name] = fn
        return fn
    return deco


# ---------------------------------------------------------------------------
# shared pieces
# ---------------------------------------------------------------------------
def baggie(s, powder, sticker=None, seal="d8443a", tilt=0, chunky=False, sparkle=True, fill=0.55):
    """A zip-lock baggie with a pile of powder in it."""
    top, bot = 7, 27
    left, right = 8, 24
    bag = poly([(left + tilt, top), (right + tilt, top), (right + 1, bot - 2), (right - 1, bot),
                (left + 1, bot), (left - 1, bot - 2)])
    plastic = ramp("c8d8e4", 5, spread=0.3)
    s.shade(bag, [alpha(x, 150) for x in plastic], dither=0.3)
    # powder pile in the bottom part of the bag
    level = int(bot - (bot - top) * fill)
    pile = {(x, y) for (x, y) in bag if y >= level + abs(x - (left + right) / 2 - tilt / 2) * 0.25}
    pr = ramp(powder, 5, spread=0.35)
    s.shade(pile, pr, dither=0.6)
    if chunky:
        s.speckle(pile, [pr[0], pr[1]], 0.18)
    if sparkle:
        s.speckle(pile, [alpha(c("ffffff"), 255)], 0.06, layer=2)
    # zip seal
    for x in range(left + tilt, right + tilt + 1):
        s.put(x, top + 1, c(seal))
        s.put(x, top + 2, scale(c(seal), 0.7))
    # plastic shine
    s.line(left + 2 + tilt, top + 4, left + 1, bot - 6, alpha(c("ffffff"), 200), layer=2)
    s.put(left + 3 + tilt, top + 4, alpha(c("ffffff"), 230), layer=2)
    if sticker:
        sticker(s)
    s.outline(0.42)


def heart(s, x, y, col):
    for (a, b) in ((0, 0), (2, 0), (-1, 1), (0, 1), (1, 1), (2, 1), (3, 1), (0, 2), (1, 2), (2, 2), (1, 3)):
        s.put(x + a, y + b, col, 2)


def star(s, x, y, col):
    for (a, b) in ((1, 0), (0, 1), (1, 1), (2, 1), (1, 2), (-1, 1), (3, 1)):
        s.put(x + a, y + b, col, 2)


def sugar_leaf(s, x, y, ang, length, col="4f9a32", width=2.2):
    """A small pointed leaf from (x, y) at angle ang (degrees, 0 = right, 90 = up)."""
    a = math.radians(ang)
    dx, dy = math.cos(a), -math.sin(a)
    nx, ny = -dy, dx
    tip = (x + dx * length, y + dy * length)
    mid = (x + dx * length * 0.45, y + dy * length * 0.45)
    m = poly([(x, y), (mid[0] + nx * width, mid[1] + ny * width), tip, (mid[0] - nx * width, mid[1] - ny * width)])
    s.shade(m, ramp(col, 4, spread=0.35), 0, dither=0.3)
    s.line(x, y, x + dx * length * 0.75, y + dy * length * 0.75, scale(c(col), 0.7))
    return m


def bump(s, x, y, r, tones, layer=1, frost=0.0):
    """One calyx: a round bump, dark crease bottom-right, light top-left."""
    dark, mid, light, hi = tones
    m = ellipse(x, y, r, r * 0.9)
    for (px, py) in m:
        dx, dy = (px + 0.5 - x) / r, (py + 0.5 - y) / r
        d = dx * -0.62 + dy * -0.78  # towards the light
        e = math.hypot(dx, dy)
        col = mid
        if d > 0.35 and e > 0.35:
            col = light
        if d < -0.25 and e > 0.55:
            col = dark
        s.put(px, py, col, layer)
    hx, hy = int(math.floor(x - r * 0.35)), int(math.floor(y - r * 0.4))
    s.put(hx, hy, hi, layer)
    if frost and s.rng.random() < frost:
        s.put(hx, hy, alpha(c("fbfff4"), 240), 2)
    return m


def nug(s, cx, top, bottom, width, layer=1, frost=0.5, hairs="ef8a32", leaves=3, dry=False, r=2.6):
    """A cannabis bud: a teardrop made of round calyxes (tinted), each with
    its own highlight so the bud looks bumpy, sugar leaves, a few curly
    orange pistils and frost on the highlights."""
    rng = s.rng
    h = bottom - top
    tones = (grey(86), grey(172), grey(222), grey(252)) if not dry else (grey(74), grey(152), grey(204), grey(240))
    if leaves:
        g = "4c9430" if not dry else "7d8a3e"
        spots = [(-1, 0.62, 205), (1, 0.55, -25), (-1, 0.85, 228), (1, 0.82, -48), (0, 0.98, 270)]
        for (side, t, ang) in spots[:leaves]:
            sugar_leaf(s, cx + side * width * 0.2, top + h * t, ang + rng.uniform(-8, 8), width * 0.48, g, 2.0)
    rows = max(3, int(round(h / (r * 1.45))))
    cells = []
    for i in range(rows):
        t = i / (rows - 1)
        w = width * (0.3 + 0.7 * math.sin(math.pi * min(1.0, 0.12 + t * 0.92)))
        n = max(1, int(round(w / (r * 1.7))))
        for j in range(n):
            ox = (j - (n - 1) / 2) * (w / n) + (0.9 if i % 2 else -0.4) + rng.uniform(-0.4, 0.4)
            cells.append((cx + ox, top + r + t * (h - 2 * r), r + rng.uniform(-0.3, 0.5)))
    body = set()
    for (x, y, rr) in sorted(cells, key=lambda p: (p[1], -p[0])):
        body |= bump(s, x, y, rr, tones, layer, frost)
    # pistils curl out from the edge and top
    hc, hd = c(hairs), scale(c(hairs), 0.78)
    outer = sorted(edge(body))
    for _ in range(max(4, int(width * h / 55))):
        (x, y) = rng.choice(outer)
        dx = -1 if x < cx else 1
        path = [(0, 0), (dx, -1), (dx * 2, -1), (dx * 2, -2)] if rng.random() < 0.6 else [(0, 0), (0, -1), (dx, -2)]
        for k, (a, b) in enumerate(path):
            s.put(x + a, y + b, hc if k < 2 else hd, 2)
    return body


# ---------------------------------------------------------------------------
# weed
# ---------------------------------------------------------------------------
@item("bud_fresh")
def bud_fresh():
    s = Sprite(seed=14)
    stem = thick_line(16, 25, 15, 31, 2.4)
    s.shade(stem, ramp("6a8a3a", 3))
    cannabis_leaf(s, 16, 25, 9, layer=0, col="4a9a32", fingers=5)
    nug(s, 16, 3, 26, 14, frost=0.25, leaves=0, r=2.7)
    s.outline(0.38)
    return s


@item("bud_dried")
def bud_dried():
    s = Sprite(seed=12)
    nug(s, 16, 6, 27, 13, frost=0.45, hairs="d0782e", dry=True, leaves=2, r=2.4)
    s.outline(0.38)
    return s



# ---------------------------------------------------------------------------
# more shared pieces
# ---------------------------------------------------------------------------
GLASS = [c("8ab4c8", 120), c("a8cad8", 110), c("c8e0ea", 100), c("e4f2f8", 120), c("ffffff", 200)]


def glass_shine(s, x0, y0, x1, y1):
    s.line(x0, y0, x1, y1, alpha(c("ffffff"), 210), 2)


def cannabis_leaf(s, cx, cy, size, layer=1, col=None, fingers=7):
    """A cannabis fan leaf: slim pointed leaflets fanning out from (cx, cy)
    (tinted by default)."""
    shades = grey_ramp(120, 250, 4) if col is None else ramp(col, 4)
    spec = [(90, 1.0), (58, 0.86), (122, 0.86), (28, 0.64), (152, 0.64), (2, 0.4), (178, 0.4)][:fingers]
    m = set()
    for ang, ln in spec:
        a = math.radians(ang)
        L = size * ln
        dx, dy = math.cos(a), -math.sin(a)
        nx, ny = -dy, dx
        w = max(0.9, size * 0.12 * (0.6 + ln * 0.4))
        m |= poly([(cx, cy), (cx + dx * L * 0.3 + nx * w * 0.8, cy + dy * L * 0.3 + ny * w * 0.8),
                   (cx + dx * L * 0.6 + nx * w, cy + dy * L * 0.6 + ny * w),
                   (cx + dx * L, cy + dy * L),
                   (cx + dx * L * 0.6 - nx * w, cy + dy * L * 0.6 - ny * w),
                   (cx + dx * L * 0.3 - nx * w * 0.8, cy + dy * L * 0.3 - ny * w * 0.8)])
    s.shade(m, shades, layer, dither=0.2, rim=False)
    for ang, ln in spec:
        a = math.radians(ang)
        s.line(cx, cy, cx + math.cos(a) * size * ln * 0.75, cy - math.sin(a) * size * ln * 0.75, shades[0], layer)
    s.line(cx, cy, cx, cy + size * 0.3, shades[0], layer)
    return m


def tablet(s, x, y, rx, ry, col, stamp=None):
    """A pill seen from the side-top: face ellipse + edge band."""
    r = ramp(col, 5)
    side = ellipse(x, y + 1.6, rx, ry)
    s.fill(side, r[0])
    s.fill(shift(edge(side), 0, 0), scale(r[0], 0.8))
    face = ellipse(x, y, rx, ry)
    s.shade(face, r[1:], dither=0.4)
    if stamp:
        stamp(s, int(x), int(y), scale(r[1], 0.62))


def rock(s, pts, col, seed_facets=None):
    """An irregular faceted rock from its outline points."""
    r = ramp(col, 5, spread=0.4)
    m = poly(pts)
    s.fill(m, r[2])
    cx = sum(p[0] for p in pts) / len(pts)
    cy = sum(p[1] for p in pts) / len(pts)
    # facets: fan from a point slightly up-left of the centre
    hub = (cx - 1, cy - 1)
    for i in range(len(pts)):
        a, b = pts[i], pts[(i + 1) % len(pts)]
        mx, my = (a[0] + b[0]) / 2 - cx, (a[1] + b[1]) / 2 - cy
        d = (mx * -0.62 + my * -0.78) / (math.hypot(mx, my) or 1)
        col_i = r[4] if d > 0.55 else r[3] if d > 0.1 else r[2] if d > -0.35 else r[1]
        s.fill(poly([hub, a, b]), col_i)
    return m


def cork(s, x0, y0, x1, y1):
    s.shade(rect(x0, y0, x1, y1), ramp("b8875a", 4), dither=0.3)
    s.speckle(rect(x0, y0, x1, y1), [c("8a5a34")], 0.25)


# ---------------------------------------------------------------------------
# seeds & weed products
# ---------------------------------------------------------------------------
@item("seed_pack")
def seed_pack():
    s = Sprite(seed=1)
    body = rounded(6, 5, 24, 28, 2)
    s.shade(body, ramp("efe2bf", 5, spread=0.3), dither=0.3)
    # crimped top
    s.fill(rect(6, 5, 24, 7), c("c9b07c"))
    for x in range(6, 25, 2):
        s.put(x, 5, c("a88c5a"))
    s.line(6, 8, 24, 8, c("b49a68"))
    # window with the leaf (tinted)
    s.fill(rounded(9, 11, 21, 24, 2), c("fbf6e8"))
    cannabis_leaf(s, 15, 20, 8)
    # seeds spilling out
    for (x, y) in ((22, 25), (25, 27), (21, 29)):
        sd = ellipse(x + 0.5, y + 0.5, 2.2, 1.6)
        s.shade(sd, ramp("7a5a38", 4))
        s.put(x, y, c("c8a878"), 0)
    s.outline(0.4)
    return s


@item("joint")
def joint():
    s = Sprite(seed=61)
    # a fat cone: narrow at the filter (bottom-left), wide at the lit end
    paper = poly([(5, 25), (8, 28), (26, 10), (21, 5)])
    s.shade(paper, ramp("f4f0e2", 5, spread=0.22), dither=0.2)
    for k in range(4):
        x0 = 9 + k * 4
        s.line(x0, 25 - k * 4, x0 + 2, 22 - k * 4, c("ddd6c2"))
    crutch = poly([(4, 24), (8, 28), (10, 26), (6, 22)])
    s.shade(crutch, ramp("d8b078", 4))
    s.line(5, 24, 8, 27, c("a87c48"))
    # ash + ember at the wide end
    s.fill(poly([(21, 5), (26, 10), (28, 7), (24, 3)]), c("a8a8a0"))
    s.speckle(poly([(21, 5), (26, 10), (28, 7), (24, 3)]), [c("7a7a74"), c("d8d8d0")], 0.4)
    for (x, y) in ((25, 8), (26, 7), (24, 6), (25, 6)):
        s.put(x, y, c("ff6a14"))
    s.put(25, 7, c("ffd04a"), 2)
    s.outline(0.42)
    for (x, y) in ((28, 3), (29, 2), (28, 1), (30, 1), (29, 0)):
        s.put(x, y, alpha(c("e0e0e8"), 150), 2)
    return s


@item("blunt")
def blunt():
    s = Sprite(seed=62)
    body = thick_line(6, 26, 25, 7, 5.6)
    s.shade(body, ramp("8a5a32", 5, spread=0.3), dither=0.3)
    # leaf veins of the wrap
    for k in range(5):
        x0 = 8 + k * 4
        s.line(x0, 26 - k * 4, x0 + 3, 21 - k * 4, c("6a4024"))
    tip = thick_line(5, 27, 7, 25, 5)
    s.fill(tip, c("5a3a20"))
    end = thick_line(24, 8, 26, 6, 5.6)
    s.fill(end, c("a8a8a0"))
    s.speckle(end, [c("7a7a74"), c("dcdcd4")], 0.4)
    for (x, y) in ((25, 7), (26, 8), (24, 6)):
        s.put(x, y, c("ff6a14"))
    s.put(25, 6, c("ffd04a"), 2)
    s.outline(0.4)
    for (x, y) in ((28, 4), (29, 3), (28, 2), (30, 2)):
        s.put(x, y, alpha(c("e0e0e8"), 150), 2)
    return s


@item("hash")
def hash_():
    s = Sprite(seed=71)
    r = ramp("6a4428", 5, spread=0.35)
    top = poly([(5, 13), (19, 8), (27, 13), (13, 19)])
    front = poly([(5, 13), (13, 19), (13, 25), (5, 19)])
    right = poly([(13, 19), (27, 13), (27, 19), (13, 25)])
    s.fill(front, r[1])
    s.fill(right, r[0])
    s.shade(top, r[2:], dither=0.4, rim=False)
    # pressed stamp on top (a little leaf) + edges
    for (x, y) in ((16, 11), (15, 12), (17, 12), (16, 12), (14, 13), (18, 13), (16, 13), (16, 14), (16, 15)):
        s.put(x, y, r[1])
    s.line(5, 13, 13, 19, r[4])
    s.line(13, 19, 27, 13, r[3])
    s.speckle(front | right, [r[2]], 0.12)
    # broken off crumb
    s.fill(poly([(22, 22), (26, 21), (27, 24), (23, 26)]), r[1])
    s.fill(poly([(22, 22), (26, 21), (24, 23)]), r[3])
    s.outline(0.45)
    return s


@item("moon_rock")
def moon_rock():
    s = Sprite(seed=72)
    body = nug(s, 16, 6, 27, 16, frost=0.0, leaves=0, dry=True, r=2.8)
    # sticky hash oil + a thick coat of kief
    for (x, y) in sorted(body):
        lit = (x - 1, y) not in body or (x, y - 1) not in body
        v = s.rng.random()
        if v < 0.1:
            s.put(x, y, c("b8862e"), 2)
        elif v < (0.5 if lit else 0.22):
            s.put(x, y, c("ece4cc") if lit else c("c8c0a8"), 2)
    for (x, y) in ((11, 10), (14, 8), (18, 9), (12, 15)):
        s.glint(x, y, c("fff4c8"))
    s.outline(0.42)
    return s


@item("space_brownie")
def space_brownie():
    s = Sprite(seed=73)
    r = ramp("6a3c22", 5, spread=0.35)
    top = poly([(4, 15), (16, 9), (28, 15), (16, 21)])
    left = poly([(4, 15), (16, 21), (16, 27), (4, 21)])
    right = poly([(16, 21), (28, 15), (28, 21), (16, 27)])
    s.fill(left, r[1])
    s.fill(right, r[0])
    s.shade(top, r[2:], dither=0.5, rim=False)
    # crackly top
    for (x0, y0, x1, y1) in ((9, 14, 13, 16), (14, 12, 18, 13), (19, 15, 23, 17), (12, 18, 15, 18)):
        s.line(x0, y0, x1, y1, r[4])
    s.speckle(left | right, [r[2], scale(r[0], 0.8)], 0.15)
    s.line(4, 15, 16, 21, r[4])
    # a little leaf on top
    cannabis_leaf(s, 17, 15, 4.5, layer=0, col="5aaa3a", fingers=5)
    s.outline(0.45)
    return s


def gummy_bear(s, x, y, size=1.0, layer=1):
    k = size
    body = ellipse(x, y + 4.5 * k, 4.4 * k, 5.2 * k)          # belly
    body |= ellipse(x, y - 2.2 * k, 3.8 * k, 3.4 * k)          # head
    body |= ellipse(x - 3.1 * k, y - 5.0 * k, 1.5 * k, 1.5 * k)  # ears
    body |= ellipse(x + 3.1 * k, y - 5.0 * k, 1.5 * k, 1.5 * k)
    body |= ellipse(x - 4.6 * k, y + 2.4 * k, 1.8 * k, 1.5 * k)  # arms
    body |= ellipse(x + 4.6 * k, y + 2.4 * k, 1.8 * k, 1.5 * k)
    body |= ellipse(x - 3.0 * k, y + 9.2 * k, 2.2 * k, 1.6 * k)  # feet
    body |= ellipse(x + 3.0 * k, y + 9.2 * k, 2.2 * k, 1.6 * k)
    s.shade(body, [grey(118), grey(176), grey(218), grey(242), grey(255)], layer, dither=0.4)
    # translucent jelly: a lighter belly and a bright highlight
    s.fill(ellipse(x + 0.6, y + 5 * k, 2.2 * k, 2.6 * k), grey(236), layer)
    s.put(int(x - 2 * k), int(y - 3.4 * k), alpha(c("ffffff"), 255), 2)
    s.put(int(x - 1 * k), int(y - 4 * k), alpha(c("ffffff"), 220), 2)
    s.put(int(x - 3 * k), int(y + 3 * k), alpha(c("ffffff"), 210), 2)
    # face
    s.put(int(x - 1.4 * k), int(y - 2.2 * k), grey(90), layer)
    s.put(int(x + 1.2 * k), int(y - 2.2 * k), grey(90), layer)
    s.speckle(body, [alpha(c("ffffff"), 210)], 0.04, layer=2)
    return body


@item("gummies")
def gummies():
    s = Sprite(seed=74)
    gummy_bear(s, 21, 10, 0.85)
    gummy_bear(s, 11, 15, 1.05)
    s.outline(0.4)
    return s


@item("wax")
def wax():
    s = Sprite(seed=75)
    paper = poly([(3, 12), (24, 5), (29, 20), (8, 27)])
    s.shade(paper, ramp("f2ead2", 4, spread=0.18), dither=0.2, rim=False)
    s.line(7, 14, 25, 8, c("e2d8bc"))
    # glassy shards of shatter (tinted): flat facets with sharp shine
    shards = [
        ([(8, 15), (15, 10), (17, 17), (11, 21)], 205),
        ([(15, 10), (23, 11), (21, 17), (17, 17)], 240),
        ([(11, 21), (17, 17), (21, 17), (24, 18), (17, 24)], 170),
    ]
    for pts, v in shards:
        s.fill(poly(pts), grey(v), 1)
    for pts, v in shards:
        s.line(pts[0][0], pts[0][1], pts[1][0], pts[1][1], grey(min(255, v + 30)), 1)
    s.line(15, 10, 17, 17, grey(150), 1)
    s.line(17, 17, 11, 21, grey(140), 1)
    for (x, y) in ((10, 15), (11, 14), (17, 12), (18, 12), (19, 12), (20, 19)):
        s.put(x, y, alpha(c("ffffff"), 250), 2)
    # dab tool
    s.fill(thick_line(18, 28, 29, 23, 1.6), c("c8ccd0"))
    s.line(18, 27, 28, 23, c("ffffff"), 2)
    s.outline(0.4)
    return s


@item("vape_pen")
def vape_pen():
    s = Sprite(seed=76)
    battery = thick_line(5, 28, 17, 16, 4.6)
    s.shade(battery, ramp("2c2e36", 5, spread=0.35), dither=0.3)
    band = thick_line(16, 17, 18, 15, 4.8)
    s.fill(band, c("c8ccd4"))
    s.line(16, 16, 18, 14, c("ffffff"), 2)
    cart = thick_line(18, 15, 25, 8, 4.2)
    s.fill(cart, c("d8e8f0", 140))
    oil = thick_line(18, 15, 23, 10, 3.0)
    s.shade(oil, [grey(150), grey(190), grey(225), grey(245)], 1, dither=0.4)
    tip = thick_line(25, 8, 28, 5, 3.4)
    s.shade(tip, ramp("1c1c22", 3))
    s.line(19, 12, 23, 8, alpha(c("ffffff"), 230), 2)
    # power button glow
    s.put(9, 23, c("5af0ff"), 2)
    s.put(8, 24, c("2ab0d0"), 2)
    s.outline(0.45)
    return s


# ---------------------------------------------------------------------------
# psychedelics
# ---------------------------------------------------------------------------
def shroom(s, x, y, h, cap_w, lean=0):
    """A psilocybe mushroom: thin wavy stem, conical caramel cap with a nipple."""
    stem = set()
    for k in range(h):
        sx = x + math.sin(k * 0.6) * 0.6 + lean * k / h
        stem |= rect(int(round(sx)), y - k, int(round(sx)) + 1, y - k)
    s.shade(stem, ramp("eee6d4", 4, spread=0.25), dither=0.2)
    for k in range(2, h, 3):
        sx = x + math.sin(k * 0.6) * 0.6 + lean * k / h
        if s.rng.random() < 0.6:
            s.put(int(round(sx)) + 1, y - k, c("8aaad8"))
    top = y - h
    cx = x + lean + 0.5
    cap = poly([(cx - cap_w, top + 1), (cx - cap_w * 0.7, top - cap_w * 0.55), (cx - 1, top - cap_w * 0.95),
                (cx + 1, top - cap_w * 0.95), (cx + cap_w * 0.7, top - cap_w * 0.55), (cx + cap_w, top + 1)])
    s.shade(cap, ramp("c88a44", 5, spread=0.4), dither=0.5)
    s.fill(rect(int(cx - cap_w) + 1, top + 1, int(cx + cap_w) - 1, top + 1), c("6a4a30"))
    s.put(int(cx), int(top - cap_w * 0.95), c("8a5a2a"))
    s.speckle(cap, [c("f0d8a0")], 0.06)


@item("magic_mushroom")
def magic_mushroom():
    s = Sprite(seed=81)
    shroom(s, 20, 28, 15, 6, lean=1)
    shroom(s, 10, 29, 11, 5, lean=-1)
    s.outline(0.4)
    return s


@item("shroom_tea")
def shroom_tea():
    s = Sprite(seed=82)
    mug = rounded(6, 12, 22, 28, 2)
    s.shade(mug, ramp("e8e4dc", 5, spread=0.25), dither=0.3)
    handle = ellipse(23, 19, 4.2, 4.6) - ellipse(23, 19, 2.2, 2.6) - rect(0, 0, 21, 31)
    s.shade(handle, ramp("d8d4cc", 4))
    tea = ellipse(14, 12.5, 7.5, 2.2)
    s.shade(tea, ramp("9a5a2a", 4))
    s.fill(rect(8, 20, 20, 21), c("c8603a"))
    # tea bag tag with a tiny mushroom
    s.line(20, 12, 25, 7, c("d8d0c0"))
    s.fill(rect(24, 3, 28, 7), c("f2e8c8"))
    s.put(26, 4, c("c88a44"))
    s.put(25, 5, c("c88a44"))
    s.put(27, 5, c("c88a44"))
    s.put(26, 6, c("f0f0f0"))
    s.outline(0.42)
    for (x, y) in ((11, 8), (12, 6), (11, 4), (16, 8), (17, 6), (16, 4)):
        s.put(x, y, alpha(c("ffffff"), 140), 2)
    return s


RAINBOW = ["ff3a3a", "ff9a2a", "ffe23a", "5ae84a", "3ac8f0", "6a5af0", "d84af0"]


@item("lucid_tab")
def lucid_tab():
    s = Sprite(seed=83)
    sheet = poly([(4, 10), (22, 4), (28, 22), (10, 28)])
    for (x, y) in sorted(sheet):
        d = math.hypot(x - 16, y - 16)
        a = math.atan2(y - 16, x - 16)
        v = (d * 0.55 + a * 1.1) % len(RAINBOW)
        s.put(x, y, c(RAINBOW[int(v)]))
    # an eye in the middle
    eye = ellipse(16, 16, 6, 3.4)
    s.fill(eye, c("ffffff"))
    s.fill(ellipse(16, 16, 2.6, 2.6), c("3a3ad8"))
    s.fill(ellipse(16, 16, 1.2, 1.2), c("0a0a14"))
    s.put(15, 15, c("ffffff"), 2)
    # perforations
    for t in range(0, 26, 2):
        for (ax, ay, bx, by) in (((4, 10), (22, 4), (10, 28), (28, 22)),):
            pass
    for k in (1, 2):
        f = k / 3
        x0, y0 = 4 + (22 - 4) * f, 10 + (4 - 10) * f
        x1, y1 = 10 + (28 - 10) * f, 28 + (22 - 28) * f
        for (x, y) in line_px(x0, y0, x1, y1):
            if (x + y) % 2 == 0 and (x, y) in sheet:
                s.put(x, y, alpha(c("ffffff"), 200), 2)
        x0, y0 = 4 + (10 - 4) * f, 10 + (28 - 10) * f
        x1, y1 = 22 + (28 - 22) * f, 4 + (22 - 4) * f
        for (x, y) in line_px(x0, y0, x1, y1):
            if (x + y) % 2 == 0 and (x, y) in sheet:
                s.put(x, y, alpha(c("ffffff"), 200), 2)
    s.outline(0.4)
    return s


@item("peyote_button")
def peyote_button():
    s = Sprite(seed=84)
    body = ellipse(16, 19, 11, 8.5)
    s.shade(body, ramp("6aa890", 5, spread=0.35), dither=0.4)
    # ribs
    for k in range(7):
        a = math.radians(200 + k * 20)
        s.line(16, 16, 16 + math.cos(a) * 11, 16 - math.sin(a) * 7, scale(c("6aa890"), 0.72))
    # woolly tufts
    for (x, y) in ((9, 16), (12, 21), (16, 23), (20, 21), (23, 16), (13, 15), (19, 15)):
        s.put(x, y, c("f4f4ee"), 2)
        s.put(x + 1, y, c("dcdcd2"), 2)
    # pink flower on top
    for k in range(6):
        a = math.radians(k * 60 + 15)
        pet = ellipse(16 + math.cos(a) * 2.6, 11 + math.sin(a) * 1.8, 2.0, 1.4)
        s.fill(pet, c("f08ac0"))
    s.fill(ellipse(16, 11, 1.4, 1.2), c("ffe070"))
    s.put(14, 9, c("ffc8e4"), 2)
    s.outline(0.4)
    return s


def capsule(s, x0, y0, x1, y1, col_a, col_b, w=4.4):
    a = thick_line(x0, y0, (x0 + x1) / 2, (y0 + y1) / 2, w) | ellipse(x0, y0, w / 2, w / 2)
    b = thick_line((x0 + x1) / 2, (y0 + y1) / 2, x1, y1, w) | ellipse(x1, y1, w / 2, w / 2)
    s.shade(a, ramp(col_a, 4), dither=0.3)
    s.shade(b, ramp(col_b, 4), dither=0.3)


@item("mescaline")
def mescaline():
    s = Sprite(seed=85)
    capsule(s, 7, 22, 19, 12, "7ad85a", "f2f2ec", 5.6)
    capsule(s, 14, 27, 26, 19, "7ad85a", "f2f2ec", 5.6)
    s.glint(9, 19)
    s.glint(16, 23)
    s.outline(0.4)
    return s


@item("dmt")
def dmt():
    s = Sprite(seed=86)
    vial = rounded(10, 8, 21, 29, 3)
    s.fill(vial, c("c8e0ea", 110))
    crystals = rounded(11, 17, 20, 28, 2)
    s.shade(crystals, ramp("f0a83a", 5, spread=0.4), dither=0.7)
    s.speckle(crystals, [c("fff0a0"), c("c8781a")], 0.2)
    cork(s, 11, 3, 20, 8)
    s.fill(rect(10, 12, 21, 14), c("2a2a34"))
    s.line(12, 13, 19, 13, c("8a8aa0"))
    glass_shine(s, 12, 9, 12, 26)
    s.outline(0.42)
    return s


# ---------------------------------------------------------------------------
# uppers
# ---------------------------------------------------------------------------
@item("cocaine")
def cocaine():
    s = Sprite(seed=21)
    baggie(s, "f4f6fa", sticker=lambda s: heart(s, 17, 20, c("e8344a")), seal="d8443a")
    return s


@item("crack")
def crack():
    s = Sprite(seed=91)
    rock(s, [(4, 22), (8, 15), (14, 14), (17, 19), (14, 27), (7, 27)], "efe6c8")
    rock(s, [(14, 13), (19, 7), (25, 8), (28, 14), (23, 19), (17, 18)], "e8dcb4")
    rock(s, [(16, 24), (20, 19), (26, 20), (27, 26), (21, 29)], "f4eed8")
    s.glint(10, 17)
    s.glint(21, 10)
    s.glint(22, 22)
    s.outline(0.4)
    return s


@item("blue_crystal")
def blue_crystal():
    s = Sprite(seed=31)
    shards = [
        ([(5, 27), (8, 13), (12, 9), (14, 26)], [(8, 13), (12, 9), (11, 26)]),
        ([(11, 28), (14, 6), (19, 2), (21, 27)], [(14, 6), (19, 2), (17, 28)]),
        ([(19, 28), (22, 13), (27, 10), (28, 27)], [(22, 13), (27, 10), (24, 28)]),
        ([(8, 29), (11, 21), (16, 19), (16, 29)], [(11, 21), (16, 19), (13, 29)]),
    ]
    r = ramp("5ac8f0", 5, spread=0.5)
    for body, lit in shards:
        s.fill(poly(body), r[1])
        s.fill(poly(lit), r[3])
        s.line(lit[0][0], lit[0][1], lit[2][0], lit[2][1], r[4])
    for (x, y) in ((17, 7), (24, 14), (10, 15), (13, 23)):
        s.glint(x, y, big=True)
    s.outline(0.35)
    return s


def pill_heart(s, x, y, col):
    for (a, b) in ((-1, -1), (1, -1), (-2, 0), (-1, 0), (0, 0), (1, 0), (2, 0), (-1, 1), (0, 1), (1, 1), (0, 2)):
        s.put(x + a, y + b, col)


def pill_smile(s, x, y, col):
    for (a, b) in ((-2, -1), (2, -1), (-3, 1), (-2, 2), (-1, 2), (0, 2), (1, 2), (2, 2), (3, 1)):
        s.put(x + a, y + b, col)


@item("ecstasy")
def ecstasy():
    s = Sprite(seed=41)
    tablet(s, 21, 11, 7, 4.6, "5ab4f0", pill_heart)
    tablet(s, 12, 20, 8, 5.2, "f06aaa", pill_smile)
    s.glint(8, 17, big=True)
    s.glint(17, 8)
    s.outline(0.4)
    return s


@item("pixie_dust")
def pixie_dust():
    s = Sprite(seed=92)
    jar = rounded(8, 10, 23, 29, 4)
    s.fill(jar, c("d8e8f8", 100))
    dust = rounded(9, 17, 22, 28, 3)
    for (x, y) in sorted(dust):
        s.put(x, y, mix(c("f8c84a"), c("f07ad0"), (x + y * 0.5) / 40 + s.rng.uniform(-0.15, 0.15)))
    s.speckle(dust, [c("ffffff")], 0.12, layer=2)
    cork(s, 10, 5, 21, 10)
    glass_shine(s, 10, 12, 10, 25)
    for (x, y) in ((25, 4), (27, 9), (5, 6), (28, 15)):
        s.glint(x, y, c("ffe8a0"), big=True)
    s.outline(0.42)
    return s


def skull(s, x, y, col):
    for (a, b) in ((0, 0), (1, 0), (2, 0), (-1, 1), (0, 1), (2, 1), (3, 1), (-1, 2), (1, 2), (3, 2),
                   (0, 3), (1, 3), (2, 3), (0, 4), (2, 4)):
        s.put(x + a, y + b, col, 2)


@item("angel_dust")
def angel_dust():
    s = Sprite(seed=93)
    baggie(s, "e2d0a4", sticker=lambda s: skull(s, 15, 19, c("2a1a3a")), seal="8a4ad8", chunky=True)
    return s


# ---------------------------------------------------------------------------
# downers
# ---------------------------------------------------------------------------
@item("opium")
def opium():
    s = Sprite(seed=101)
    leaf = poly([(3, 24), (10, 15), (22, 12), (29, 17), (22, 25), (9, 28)])
    s.shade(leaf, ramp("5a9a3a", 4), dither=0.3)
    s.line(4, 24, 28, 17, c("3a6a24"))
    ball = ellipse(16, 17, 7.5, 6.5)
    s.shade(ball, ramp("4a2a16", 5, spread=0.3), dither=0.5)
    s.speckle(ball, [c("2a160a")], 0.12)
    s.glint(13, 13, c("c89a6a"))
    s.put(12, 14, c("8a6040"), 2)
    s.outline(0.45)
    return s


@item("heroin")
def heroin():
    s = Sprite(seed=102)
    def stamp(s):
        s.fill(ellipse(16.5, 21.5, 3, 3), c("f2d24a"), 2)
        s.fill(ellipse(16.5, 21.5, 1.6, 1.6), c("c89a2a"), 2)
    baggie(s, "b88a5a", sticker=stamp, seal="2a2a2a", fill=0.45)
    return s


@item("lean")
def lean():
    s = Sprite(seed=51)
    cup = poly([(7, 8), (25, 8), (22, 30), (10, 30)])
    s.shade(cup, ramp("f4f4f6", 5, spread=0.22), rim=False, dither=0.3)
    s.speckle(cup, [c("e2e2e8")], 0.15)
    # double cup: the rim of the outer cup
    s.fill(poly([(8, 14), (24, 14), (24, 15), (8, 15)]), c("d8d8e0"))
    s.line(8, 16, 24, 16, c("eeeef2"))
    s.fill(ellipse(16, 8.5, 9, 2.2), c("e8e8ee"))
    s.fill(ellipse(16, 8.7, 7.6, 1.6), c("7a2ab8"))
    s.fill(rect(11, 8, 19, 8), c("a85ae0"))
    s.fill(thick_line(18, 8, 24, 0, 2.2), c("e83a3a"))
    s.line(19, 7, 23, 1, c("ff8a8a"), 2)
    s.outline(0.45)
    return s


@item("ketamine")
def ketamine():
    def k(s):
        for (x, y) in ((14, 18), (14, 19), (14, 20), (14, 21), (14, 22), (15, 20), (16, 19), (17, 18), (16, 21),
                       (17, 22)):
            s.put(x, y, c("3a7ad8"), 2)
    s = Sprite(seed=103)
    baggie(s, "e8eef4", sticker=k, seal="3a7ad8", chunky=True)
    return s


# ---------------------------------------------------------------------------
# in-between steps (cooked at the Drug Lab on the way to a drug)
# ---------------------------------------------------------------------------
@item("kief")
def kief():
    """Golden-green sifted trichome dust in a little round tin."""
    s = Sprite(seed=131)
    tin = ellipse(16, 21, 12, 7)
    s.shade(tin, ramp("a8b0b8", 5, spread=0.4), dither=0.3)
    rim = ellipse(16, 18, 12, 6)
    s.shade(rim, ramp("c8d0d8", 4, spread=0.3), rim=False)
    inner = ellipse(16, 18, 10.5, 4.8)
    s.fill(inner, c("6a7078"))
    pile = ellipse(16, 17, 9.5, 4.2) | ellipse(15, 14.5, 6, 3.6) | ellipse(17, 12.5, 3.4, 2.4)
    s.shade(pile, ramp("c8c46a", 5, spread=0.38), dither=0.7)
    s.speckle(pile, [c("e8e8a0"), c("8a9a3a")], 0.22)
    s.speckle(pile, [alpha(c("ffffff"), 230)], 0.07, layer=2)
    # a few grains on the rim
    for (x, y) in ((6, 19), (25, 18), (24, 21)):
        s.put(x, y, c("c8c46a"))
    s.outline(0.42)
    return s


@item("canna_butter")
def canna_butter():
    """A stick of green-tinted butter on its paper wrapper, with a leaf stamp."""
    s = Sprite(seed=132)
    wrap = poly([(3, 20), (17, 13), (29, 18), (15, 26)])
    s.shade(wrap, ramp("f2ecd8", 4, spread=0.2), rim=False, dither=0.2)
    top = poly([(6, 15), (17, 9), (26, 13), (15, 19)])
    front = poly([(6, 15), (15, 19), (15, 24), (6, 20)])
    right = poly([(15, 19), (26, 13), (26, 18), (15, 24)])
    r = ramp("d8d870", 5, spread=0.3)
    s.fill(front, r[2])
    s.fill(right, r[1])
    s.shade(top, r[2:], dither=0.4, rim=False)
    s.line(6, 15, 15, 19, r[4])
    s.line(15, 19, 26, 13, r[3])
    # leaf stamp on top
    for (x, y) in ((16, 12), (15, 13), (16, 13), (17, 13), (14, 14), (16, 14), (18, 14), (16, 15)):
        s.put(x, y, c("6a9a2a"))
    # a knife-cut slice on the end
    s.fill(poly([(22, 15), (26, 13), (26, 16), (22, 18)]), c("eef0a0"))
    s.outline(0.45)
    return s


@item("coca_paste")
def coca_paste():
    """Off-white paste lumps on a coca leaf."""
    s = Sprite(seed=133)
    oval_leaf(s, 3, 25, 18, 26, 6.5, "3f9a3a")
    lump = ellipse(15, 17, 7.5, 5.5) | ellipse(21, 14, 4.8, 4) | ellipse(10, 15, 4, 3.2)
    s.shade(lump, ramp("e2d6a8", 5, spread=0.32), dither=0.6)
    s.speckle(lump, [c("c8b47a"), c("f4ecd0")], 0.2)
    s.glint(12, 13)
    s.glint(20, 11)
    s.outline(0.42)
    return s


@item("morphine")
def morphine():
    """Brown morphine base powder in a stoppered lab jar."""
    s = Sprite(seed=134)
    jar = rounded(8, 9, 23, 29, 3)
    s.fill(jar, c("d8e8f4", 100))
    powder = rounded(9, 17, 22, 28, 2)
    s.shade(powder, ramp("9a6a42", 5, spread=0.35), dither=0.6)
    s.speckle(powder, [c("c89a6a"), c("6a4428")], 0.2)
    s.fill(rect(8, 5, 23, 9), c("4a3a30"))
    s.fill(rect(8, 5, 23, 5), c("6a5a4a"))
    label = rect(11, 12, 20, 15)
    s.fill(label, c("f4ecd8"))
    s.line(12, 13, 19, 13, c("8a3a2a"))
    s.line(12, 14, 16, 14, c("8a8a8a"))
    glass_shine(s, 10, 11, 10, 26)
    s.outline(0.42)
    return s


@item("ergot_extract")
def ergot_extract():
    """A small amber dropper bottle of violet ergot extract."""
    s = Sprite(seed=135)
    bottle = rounded(9, 13, 22, 29, 3)
    s.shade(bottle, ramp("b8742a", 5, spread=0.4), dither=0.3)
    liquid = rounded(10, 18, 21, 28, 2)
    s.shade(liquid, ramp("8a3ab8", 4, spread=0.35), dither=0.3)
    s.fill(rect(13, 8, 18, 13), c("2a2a30"))
    bulb = ellipse(15.5, 5.5, 3, 3)
    s.shade(bulb, ramp("2a2a34", 3, spread=0.4))
    label = rect(11, 20, 20, 24)
    s.fill(label, c("f4ecd8"))
    for (x, y) in ((13, 22), (14, 21), (15, 22), (16, 21), (17, 22), (18, 21)):
        s.put(x, y, c("8a3ab8"))
    glass_shine(s, 11, 15, 11, 27)
    # a drop falling
    s.put(26, 20, c("b86ae8"))
    s.put(26, 21, c("8a3ab8"))
    s.outline(0.42)
    return s


@item("cough_syrup")
def cough_syrup():
    """A pharmacy bottle of purple syrup with a measuring cup on top."""
    s = Sprite(seed=136)
    body = rounded(8, 11, 23, 29, 3)
    s.shade(body, ramp("7a2ab0", 5, spread=0.35), dither=0.3)
    s.fill(rect(12, 7, 19, 11), c("7a2ab0"))
    cup = poly([(10, 2), (21, 2), (20, 7), (11, 7)])
    s.shade(cup, ramp("e8e8f0", 4, spread=0.2), rim=False)
    for x in (13, 16, 19):
        s.put(x, 4, c("a8a8b8"))
    label = rect(10, 16, 21, 24)
    s.fill(label, c("f8f4ec"))
    s.fill(rect(10, 16, 21, 17), c("e83a3a"))
    s.fill(rect(14, 19, 17, 22), c("e83a3a"))
    s.fill(rect(13, 20, 18, 21), c("e83a3a"))
    glass_shine(s, 10, 12, 10, 27)
    s.outline(0.42)
    return s


# ---------------------------------------------------------------------------
# harvest & seeds
# ---------------------------------------------------------------------------
def oval_leaf(s, x, y, ang, length, width, col="3fae3a"):
    a = math.radians(ang)
    dx, dy = math.cos(a), -math.sin(a)
    nx, ny = -dy, dx
    pts = []
    for k in range(9):
        t = k / 8
        w = math.sin(math.pi * t) * width
        pts.append((x + dx * length * t + nx * w, y + dy * length * t + ny * w))
    for k in range(8, -1, -1):
        t = k / 8
        w = math.sin(math.pi * t) * width
        pts.append((x + dx * length * t - nx * w, y + dy * length * t - ny * w))
    m = poly(pts)
    s.shade(m, ramp(col, 5, spread=0.38), dither=0.4)
    s.line(x, y, x + dx * length * 0.9, y + dy * length * 0.9, scale(c(col), 0.68))
    return m


@item("coca_leaves")
def coca_leaves():
    s = Sprite(seed=111)
    oval_leaf(s, 8, 27, 62, 20, 5.0)
    oval_leaf(s, 9, 27, 28, 19, 4.6, "4cbc44")
    oval_leaf(s, 7, 27, 95, 17, 4.4, "36a034")
    s.line(4, 30, 8, 27, c("6a5434"))
    s.outline(0.42)
    return s


@item("coca_seeds")
def coca_seeds():
    s = Sprite(seed=112)
    oval_leaf(s, 9, 22, 40, 14, 3.6)
    for (x, y) in ((15, 19), (20, 21), (16, 25), (22, 16), (11, 24)):
        b = ellipse(x + 0.5, y + 0.5, 2.6, 2.6)
        s.shade(b, ramp("d8302a", 4), dither=0.3)
        s.put(x - 1, y - 1, c("ffc0b0"), 2)
    s.outline(0.42)
    return s


@item("poppy_pod")
def poppy_pod():
    s = Sprite(seed=113)
    stem = thick_line(16, 22, 13, 31, 2.4)
    s.shade(stem, ramp("7aa86a", 3))
    pod = ellipse(16, 15, 8, 8.5)
    s.shade(pod, ramp("9ec8a4", 5, spread=0.3), dither=0.4)
    for x in (12, 16, 20):
        s.line(x, 9, x - 0.5, 21, scale(c("9ec8a4"), 0.75))
    # the crown
    crown = poly([(10, 8), (13, 5), (16, 7), (19, 5), (22, 8), (16, 9)])
    s.shade(crown, ramp("8a7aa8", 3))
    # latex weeping from a score
    for (x, y) in ((17, 14), (17, 15), (17, 16), (18, 17)):
        s.put(x, y, c("fbf6e8"), 2)
    s.outline(0.42)
    return s


@item("poppy_seeds")
def poppy_seeds():
    s = Sprite(seed=114)
    pouch = poly([(7, 13), (25, 13), (27, 26), (22, 30), (10, 30), (5, 26)])
    s.shade(pouch, ramp("d8c49a", 5, spread=0.3), dither=0.4)
    s.fill(ellipse(16, 13, 9, 2.6), c("3a3a48"))
    s.speckle(ellipse(16, 12.5, 8, 2.2), [c("6a6a88"), c("2a2a38")], 0.6)
    s.line(8, 16, 24, 16, c("a8945a"))
    s.fill(rect(7, 15, 9, 17), c("c84a3a"))
    # a tiny red poppy on the pouch
    for (x, y) in ((15, 21), (17, 21), (14, 22), (15, 22), (16, 22), (17, 22), (18, 22), (15, 23), (17, 23)):
        s.put(x, y, c("e8302a"))
    s.put(16, 22, c("1a1a1a"))
    s.outline(0.42)
    return s


@item("peyote_seeds")
def peyote_seeds():
    s = Sprite(seed=115)
    fruit = poly([(16, 4), (21, 10), (22, 20), (16, 28), (10, 20), (11, 10)])
    s.shade(fruit, ramp("e86aa0", 5), dither=0.4)
    split = poly([(16, 9), (19, 14), (19, 21), (16, 25), (13, 21), (13, 14)])
    s.fill(split, c("f8e0ea"))
    for (x, y) in ((15, 13), (17, 15), (15, 17), (17, 19), (15, 21), (16, 23)):
        s.put(x, y, c("1a1a1a"))
        s.put(x + 1, y, c("4a4a4a"))
    s.fill(rect(15, 2, 17, 4), c("6a9a5a"))
    s.outline(0.42)
    return s


@item("mushroom_spores")
def mushroom_spores():
    s = Sprite(seed=116)
    dish = ellipse(16, 18, 13, 9)
    s.fill(dish, c("c8dcea", 140))
    agar = ellipse(16, 18, 11, 7.4)
    s.shade(agar, ramp("e8d8a0", 4, spread=0.2), dither=0.3)
    myc = ellipse(15, 18, 7, 4.6)
    s.speckle(myc, [c("ffffff"), c("eeeeea")], 0.7)
    for (x, y, h) in ((12, 18, 3), (17, 17, 4), (20, 20, 2)):
        s.line(x, y, x, y - h, c("f0ead8"))
        s.fill(ellipse(x + 0.5, y - h, 1.8, 1.2), c("c88a44"))
    s.line(5, 14, 9, 11, alpha(c("ffffff"), 220), 2)
    s.outline(0.45)
    return s


# ---------------------------------------------------------------------------
# supplies
# ---------------------------------------------------------------------------
@item("rolling_papers")
def rolling_papers():
    s = Sprite(seed=121)
    sheets = poly([(9, 4), (24, 4), (24, 12), (9, 12)])
    s.shade(sheets, ramp("f8f6ee", 4, spread=0.15), rim=False)
    s.line(10, 4, 23, 4, c("ffffff"))
    cover = rounded(6, 9, 26, 27, 2)
    s.shade(cover, ramp("e07a2a", 5, spread=0.3), dither=0.3)
    s.fill(rect(6, 9, 26, 11), c("b85a1a"))
    s.fill(rounded(10, 15, 22, 24, 2), c("fbe6c8"))
    cannabis_leaf(s, 16, 21, 5.2, layer=0, col="4c9a30", fingers=5)
    s.outline(0.42)
    return s


@item("blunt_wrap")
def blunt_wrap():
    s = Sprite(seed=122)
    pouch = poly([(6, 6), (26, 6), (25, 29), (7, 29)])
    s.shade(pouch, ramp("3ab46a", 5, spread=0.4), dither=0.5)
    s.fill(rect(6, 6, 26, 8), c("d8b84a"))
    for x in range(6, 27, 2):
        s.put(x, 6, c("a88a2a"))
    s.put(26, 11, (0, 0, 0, 0))
    s.put(25, 11, scale(c("3ab46a"), 0.5))
    cannabis_leaf(s, 16, 20, 6, layer=0, col="f2e07a", fingers=5)
    s.line(9, 10, 9, 26, alpha(c("ffffff"), 170), 2)
    s.outline(0.42)
    return s


@item("bong")
def bong():
    s = Sprite(seed=123)
    tube = rect(13, 2, 19, 20)
    s.fill(tube, c("a8d8e8", 120))
    base = ellipse(16, 24, 9, 6.5)
    s.fill(base, c("a8d8e8", 120))
    water = ellipse(16, 25.5, 8, 4.5)
    s.shade(water, [c("3a8ac8", 170), c("5aa8e0", 170), c("8acaf0", 170)], dither=0.3)
    # stem + bowl
    s.fill(thick_line(10, 17, 6, 13, 1.6), c("9aa4ac"))
    s.fill(poly([(3, 11), (9, 11), (8, 14), (4, 14)]), c("c8ced4"))
    s.fill(rect(4, 11, 8, 11), c("5a8a3a"))
    # mouthpiece ring + highlights
    s.fill(rect(13, 2, 19, 3), c("d8ecf4", 200))
    glass_shine(s, 14, 4, 14, 18)
    glass_shine(s, 10, 21, 12, 20)
    s.outline(0.4)
    return s


@item("lab_solvent")
def lab_solvent():
    s = Sprite(seed=124)
    flask = poly([(13, 3), (19, 3), (19, 12), (27, 28), (5, 28), (13, 12)])
    s.fill(flask, c("d8ecf4", 110))
    liquid = poly([(10, 18), (22, 18), (27, 28), (5, 28)])
    s.shade(liquid, ramp("5ad87a", 5, spread=0.35), dither=0.5)
    s.fill(rect(9, 18, 23, 18), c("9af0aa"))
    for (x, y) in ((12, 23), (17, 21), (20, 25), (15, 26)):
        s.put(x, y, c("d8ffe0"), 2)
    s.fill(rect(12, 2, 20, 4), c("b8c4cc"))
    glass_shine(s, 14, 6, 9, 24)
    s.outline(0.42)
    return s


@item("fertilizer")
def fertilizer():
    s = Sprite(seed=125)
    sack = poly([(7, 9), (25, 9), (27, 26), (23, 30), (9, 30), (5, 26)])
    s.shade(sack, ramp("c8a870", 5, spread=0.32), dither=0.5)
    s.speckle(sack, [c("a8885a")], 0.12)
    s.fill(ellipse(16, 9, 9, 2.6), c("5a3a1e"))
    s.speckle(ellipse(16, 8.6, 8, 2), [c("8a6a3a"), c("3a2614")], 0.5)
    # sprout logo
    s.line(16, 26, 16, 18, c("3a8a2a"))
    oval_leaf(s, 16, 20, 145, 6, 2.2, "5ac83a")
    oval_leaf(s, 16, 19, 35, 6, 2.2, "5ac83a")
    s.outline(0.42)
    return s


@item("grower_guide")
def grower_guide():
    s = Sprite(seed=126)
    pages = poly([(8, 6), (26, 4), (26, 27), (8, 29)])
    s.fill(pages, c("f2ead8"))
    cover = poly([(5, 5), (23, 3), (23, 26), (5, 28)])
    s.shade(cover, ramp("2e7a3a", 5, spread=0.3), dither=0.4)
    s.fill(poly([(5, 5), (7, 5), (7, 28), (5, 28)]), c("1e5a2a"))
    cannabis_leaf(s, 15, 17, 6.5, layer=0, col="f2d24a", fingers=7)
    s.fill(rect(19, 24, 20, 31), c("e83a3a"))
    s.outline(0.42)
    return s


@item("cash")
def cash():
    s = Sprite(seed=127)
    r = ramp("6ac06a", 5, spread=0.32)
    # the stack: top face + a thick side made of bill edges
    top = poly([(3, 13), (20, 7), (29, 13), (12, 19)])
    side_l = poly([(3, 13), (12, 19), (12, 25), (3, 19)])
    side_r = poly([(12, 19), (29, 13), (29, 19), (12, 25)])
    s.fill(side_l, r[1])
    s.fill(side_r, r[0])
    for k in range(1, 6, 2):
        s.line(3, 13 + k, 12, 19 + k, r[2])
        s.line(12, 19 + k, 29, 13 + k, r[1])
    s.shade(top, r[2:], dither=0.3, rim=False)
    s.line(3, 13, 12, 19, r[4])
    # paper band across the middle
    band = poly([(9, 10), (13, 9), (22, 15), (18, 17)])
    s.fill(band, c("f0e2b0"))
    s.fill(poly([(12, 19), (18, 17), (18, 23), (12, 25)]), c("d8c890"))
    # $ on the top bill
    for (x, y) in ((22, 11), (23, 11), (21, 12), (22, 13), (23, 14), (21, 15), (22, 15), (22, 10), (22, 16)):
        s.put(x, y, c("2a6a2a"))
    s.outline(0.42)
    return s


# ---------------------------------------------------------------------------
# menu tabs, buttons and award badges (drawn into item/icon/)
# ---------------------------------------------------------------------------
ICONS = {}


def icon(name):
    def deco(fn):
        ICONS[name] = fn
        return fn
    return deco


GOLD = "f2c23a"


def coin(s, x, y, r=4.0, col=GOLD):
    m = ellipse(x, y, r, r)
    s.shade(m, ramp(col, 5, spread=0.4), dither=0.3)
    s.fill(ellipse(x, y, r * 0.55, r * 0.55), scale(c(col), 0.85))
    s.put(int(x - r * 0.4), int(y - r * 0.5), c("fff4c8"), 2)


def diamond(s, cx, cy, w, h, col="5ae8e0"):
    r = ramp(col, 5, spread=0.45)
    top = cy - h * 0.25
    s.fill(poly([(cx - w, top), (cx + w, top), (cx, cy + h * 0.75)]), r[1])
    s.fill(poly([(cx - w, top), (cx, cy + h * 0.75), (cx - w * 0.3, top)]), r[2])
    s.fill(poly([(cx - w * 0.6, cy - h * 0.6), (cx + w * 0.6, cy - h * 0.6), (cx + w, top), (cx - w, top)]), r[3])
    s.fill(poly([(cx - w * 0.6, cy - h * 0.6), (cx - w * 0.1, cy - h * 0.6), (cx - w * 0.35, top)]), r[4])
    s.line(cx - w, top, cx + w, top, r[4])


def crown(s, cx, cy, w=11, h=9, col=GOLD):
    pts = [(cx - w, cy + h * 0.5), (cx - w, cy - h * 0.4), (cx - w * 0.5, cy + h * 0.05), (cx, cy - h * 0.6),
           (cx + w * 0.5, cy + h * 0.05), (cx + w, cy - h * 0.4), (cx + w, cy + h * 0.5)]
    m = poly(pts)
    s.shade(m, ramp(col, 5, spread=0.4), dither=0.3)
    s.fill(rect(int(cx - w), int(cy + h * 0.3), int(cx + w), int(cy + h * 0.5) + 1), scale(c(col), 0.8))
    for (x, y, g) in ((cx - w, cy - h * 0.4, "e83a3a"), (cx, cy - h * 0.6, "3ab4f0"), (cx + w, cy - h * 0.4, "e83a3a")):
        s.fill(ellipse(x, y - 0.5, 1.6, 1.6), c(g))
    s.fill(ellipse(cx, cy + h * 0.1, 1.8, 1.8), c("5ae85a"))


def arrow(s, x0, y0, x1, y1, col, w=2.2, head=3.5):
    s.fill(thick_line(x0, y0, x1, y1, w), c(col))
    a = math.atan2(y1 - y0, x1 - x0)
    tip = (x1 + math.cos(a) * head * 0.6, y1 + math.sin(a) * head * 0.6)
    l = (x1 + math.cos(a + 2.3) * head, y1 + math.sin(a + 2.3) * head)
    r = (x1 + math.cos(a - 2.3) * head, y1 + math.sin(a - 2.3) * head)
    s.fill(poly([tip, l, r]), c(col))


def medal(s, cx, cy, r, col, ribbon=("e83a3a", "3a6ae8")):
    s.fill(poly([(cx - 6, 2), (cx - 1, 2), (cx + 1, cy - r + 2), (cx - 3, cy - r + 3)]), c(ribbon[0]))
    s.fill(poly([(cx + 6, 2), (cx + 1, 2), (cx - 1, cy - r + 2), (cx + 3, cy - r + 3)]), c(ribbon[1]))
    coin(s, cx, cy, r, col)


@icon("tab_shop")
def tab_shop():
    s = Sprite(seed=201)
    # market stall: striped awning over a counter with goods
    for k in range(6):
        col = c("e83a3a") if k % 2 == 0 else c("f8f2e8")
        s.fill(poly([(4 + k * 4, 6), (8 + k * 4, 6), (8 + k * 4, 12), (4 + k * 4, 12)]), col)
        s.fill(ellipse(6 + k * 4, 12.5, 2, 1.6), col)
    s.fill(rect(4, 4, 27, 6), c("b82a2a"))
    s.fill(rect(6, 14, 7, 27), c("8a5a32"))
    s.fill(rect(24, 14, 25, 27), c("8a5a32"))
    counter = rect(4, 20, 27, 27)
    s.shade(counter, ramp("a8703c", 4), dither=0.3)
    s.line(4, 20, 27, 20, c("d8a060"))
    s.fill(ellipse(12, 18, 3, 2.2), c("5ab43a"))
    coin(s, 20, 17, 2.6)
    s.outline(0.4)
    return s


@icon("tab_drugs")
def tab_drugs():
    s = Sprite(seed=202)
    cannabis_leaf(s, 14, 17, 13, layer=0, col="4caa32")
    tablet(s, 22, 23, 6, 4, "f06aaa", pill_heart)
    s.outline(0.4)
    return s


@icon("tab_trade")
def tab_trade():
    s = Sprite(seed=203)
    diamond(s, 11, 12, 7, 9)
    coin(s, 22, 21, 5)
    arrow(s, 19, 8, 25, 13, "5ae85a", 1.8, 3.2)
    arrow(s, 13, 26, 7, 21, "5ae85a", 1.8, 3.2)
    s.outline(0.4)
    return s


@icon("tab_top")
def tab_top():
    s = Sprite(seed=204)
    crown(s, 16, 17, 12, 13)
    s.outline(0.4)
    return s


@icon("tab_awards")
def tab_awards():
    s = Sprite(seed=205)
    medal(s, 16, 19, 8.5, GOLD)
    for (x, y) in ((16, 15), (14, 18), (18, 18), (15, 21), (17, 21), (16, 19)):
        s.put(x, y, c("fff0a0"))
    s.outline(0.4)
    return s


@icon("tab_cook")
def tab_cook():
    s = Sprite(seed=206)
    flask = poly([(13, 4), (19, 4), (19, 11), (25, 22), (7, 22), (13, 11)])
    s.fill(flask, c("d8ecf4", 120))
    s.shade(poly([(10, 15), (22, 15), (25, 22), (7, 22)]), ramp("5ad87a", 4), dither=0.3)
    for (x, y) in ((13, 18), (18, 17), (16, 20)):
        s.put(x, y, c("d8ffe0"), 2)
    s.fill(rect(12, 3, 20, 4), c("b8c4cc"))
    # flame
    s.fill(poly([(12, 30), (16, 23), (20, 30)]), c("ff8a2a"))
    s.fill(poly([(14, 30), (16, 26), (18, 30)]), c("ffe04a"))
    s.outline(0.4)
    return s


@icon("tab_roll")
def tab_roll():
    s = Sprite(seed=207)
    paper = poly([(4, 14), (22, 6), (28, 18), (10, 26)])
    s.shade(paper, ramp("f4f0e2", 4, spread=0.2), rim=False)
    s.fill(thick_line(7, 22, 24, 13, 3.4), c("6ab43a"))
    s.speckle(thick_line(7, 22, 24, 13, 3.4), [c("4a8a2a"), c("8ad05a")], 0.35)
    s.outline(0.4)
    return s


@icon("tab_dry")
def tab_dry():
    s = Sprite(seed=208)
    s.fill(rect(2, 6, 29, 6), c("8a6a4a"))
    s.fill(rect(3, 4, 4, 30), c("6a4a2a"))
    s.fill(rect(27, 4, 28, 30), c("6a4a2a"))
    for k, x in enumerate((9, 16, 23)):
        s.line(x, 7, x, 10, c("c8c0b0"))
        b = ellipse(x + 0.5, 15, 3.2, 4.8)
        s.shade(b, ramp("7aa83a" if k != 1 else "a8a83a", 4), dither=0.4)
        s.put(x - 1, 13, c("e8822e"), 2)
        s.put(x + 1, 16, c("e8822e"), 2)
    s.outline(0.4)
    return s


@icon("tab_mix")
def tab_mix():
    s = Sprite(seed=209)
    for k in range(26):
        y = 3 + k
        a = k * 0.42
        x1 = 16 + math.sin(a) * 7
        x2 = 16 - math.sin(a) * 7
        s.put(int(round(x1)), y, c("e85ad0"))
        s.put(int(round(x2)), y, c("5ac8f0"))
        if k % 3 == 0:
            s.line(x1, y, x2, y, c("f4f4f4"))
    s.outline(0.4)
    return s


@icon("ui_upgrade")
def ui_upgrade():
    s = Sprite(seed=210)
    arrow(s, 16, 27, 16, 9, "5ae85a", 6, 8)
    for (x, y) in ((6, 8), (26, 12), (7, 22)):
        s.glint(x, y, c("ffe070"), big=True)
    s.outline(0.4)
    return s


# -- award badges -------------------------------------------------------------
@icon("award_farmer")
def award_farmer():
    s = Sprite(seed=301)
    can = rounded(6, 14, 20, 27, 3)
    s.shade(can, ramp("5a8ad8", 5), dither=0.3)
    s.fill(thick_line(19, 18, 27, 11, 2.4), c("4a7ac8"))
    s.fill(ellipse(27.5, 10.5, 2.2, 2.2), c("3a6ab8"))
    s.fill(ellipse(13, 13.5, 6, 2), c("2a4a88"))
    for (x, y) in ((28, 15), (29, 18), (27, 20)):
        s.put(x, y, c("8ad8ff"), 2)
    s.line(9, 11, 17, 11, c("3a5a98"))
    s.outline(0.4)
    return s


@icon("award_plantation")
def award_plantation():
    s = Sprite(seed=302)
    soil = poly([(2, 22), (30, 22), (30, 29), (2, 29)])
    s.shade(soil, ramp("6a4428", 4), dither=0.3)
    for x in (6, 16, 26):
        cannabis_leaf(s, x, 17, 7, layer=0, col="4caa32", fingers=5)
        s.line(x, 17, x, 22, c("3a7a24"))
    for x in range(3, 30, 4):
        s.line(x, 24, x + 2, 24, c("8a5a34"))
    s.outline(0.4)
    return s


@icon("award_lab")
def award_lab():
    s = Sprite(seed=303)
    table = rect(3, 22, 28, 25)
    s.shade(table, ramp("8a8e96", 4), dither=0.2)
    s.fill(rect(5, 26, 6, 30), c("5a5e66"))
    s.fill(rect(25, 26, 26, 30), c("5a5e66"))
    f1 = poly([(8, 6), (12, 6), (12, 12), (16, 21), (4, 21), (8, 12)])
    s.fill(f1, c("d8ecf4", 120))
    s.shade(poly([(6, 16), (14, 16), (16, 21), (4, 21)]), ramp("e85ad0", 4))
    f2 = rounded(18, 9, 25, 21, 2)
    s.fill(f2, c("d8ecf4", 120))
    s.shade(rect(19, 14, 24, 20), ramp("5ac8f0", 4))
    s.put(21, 12, c("ffffff"), 2)
    s.put(22, 6, alpha(c("ffffff"), 150), 2)
    s.outline(0.4)
    return s


@icon("award_chemist")
def award_chemist():
    s = Sprite(seed=304)
    for (x, col) in ((7, "5ad87a"), (16, "f0c83a"), (25, "e85ad0")):
        tube = rounded(x - 3, 5, x + 3, 27, 3)
        s.fill(tube, c("d8ecf4", 120))
        s.shade(rounded(x - 2, 14, x + 2, 26, 2), ramp(col, 4))
        s.fill(rect(x - 3, 4, x + 3, 5), c("b8c4cc"))
    s.glint(16, 2, c("fff4a0"), big=True)
    s.outline(0.4)
    return s


@icon("award_cash_stack")
def award_cash_stack():
    s = cash()
    coin(s, 25, 26, 3.4)
    return s


@icon("award_money_bag")
def award_money_bag():
    s = Sprite(seed=306)
    bag = ellipse(16, 20, 11, 9.5) | poly([(11, 6), (21, 6), (19, 12), (13, 12)])
    s.shade(bag, ramp("c8a060", 5, spread=0.35), dither=0.4)
    s.fill(rect(11, 11, 21, 12), c("8a5a2a"))
    for (x, y) in ((16, 15), (17, 15), (15, 16), (16, 17), (17, 18), (15, 19), (16, 19), (16, 14), (16, 20)):
        s.put(x, y, c("2a7a2a"))
    s.outline(0.4)
    return s


@icon("award_gold_bars")
def award_gold_bars():
    s = Sprite(seed=307)
    r = ramp(GOLD, 5, spread=0.4)
    for (x, y) in ((4, 20), (16, 20), (10, 12)):
        top = poly([(x, y), (x + 10, y), (x + 12, y + 3), (x + 2, y + 3)])
        front = poly([(x + 2, y + 3), (x + 12, y + 3), (x + 12, y + 8), (x + 2, y + 8)])
        s.fill(front, r[1])
        s.fill(top, r[3])
        s.line(x + 2, y + 3, x + 12, y + 3, r[4])
        s.put(x + 4, y + 1, c("fffbe0"), 2)
    s.outline(0.4)
    return s


@icon("award_crown")
def award_crown():
    s = Sprite(seed=308)
    crown(s, 16, 18, 12, 13)
    for (x, y) in ((5, 5), (27, 6), (16, 3)):
        s.glint(x, y, c("fff4a0"), big=True)
    s.outline(0.4)
    return s


@icon("award_order")
def award_order():
    s = Sprite(seed=309)
    board = rounded(6, 4, 25, 29, 2)
    s.shade(board, ramp("a8703c", 4), dither=0.3)
    s.fill(rect(8, 8, 23, 27), c("f8f6ee"))
    s.fill(rect(12, 3, 19, 6), c("b8c4cc"))
    for y in (12, 17, 22):
        s.line(13, y, 21, y, c("a8a8b0"))
        s.fill(rect(9, y - 1, 11, y + 1), c("5ac83a"))
    s.outline(0.4)
    return s


@icon("award_orders")
def award_orders():
    s = Sprite(seed=310)
    body = rect(3, 11, 19, 24)
    s.shade(body, ramp("e8e8ec", 4, spread=0.25), dither=0.3)
    cab = poly([(19, 15), (25, 15), (29, 20), (29, 24), (19, 24)])
    s.shade(cab, ramp("e83a3a", 4), dither=0.3)
    s.fill(poly([(21, 16), (24, 16), (27, 20), (21, 20)]), c("a8d8f0"))
    for x in (8, 24):
        s.fill(ellipse(x, 25, 3, 3), c("2a2a30"))
        s.put(x, 25, c("a8a8b0"))
    cannabis_leaf(s, 11, 19, 4.5, layer=0, col="4caa32", fingers=5)
    s.outline(0.4)
    return s


@icon("award_trade")
def award_trade():
    s = tab_trade()
    return s


@icon("award_diamond")
def award_diamond():
    s = Sprite(seed=312)
    diamond(s, 16, 15, 12, 15)
    for (x, y) in ((8, 6), (25, 9)):
        s.glint(x, y, big=True)
    s.outline(0.38)
    return s


@icon("award_dna")
def award_dna():
    return tab_mix()


@icon("award_epic")
def award_epic():
    s = Sprite(seed=314)
    diamond(s, 16, 16, 11, 14, "b45af0")
    s.glint(9, 7, big=True)
    s.outline(0.38)
    return s


@icon("award_legendary")
def award_legendary():
    s = Sprite(seed=315)
    for k in range(8):
        a = math.radians(k * 45)
        s.fill(thick_line(16, 16, 16 + math.cos(a) * 14, 16 + math.sin(a) * 14, 2.0), c("ffd84a"))
    seed = ellipse(16, 16, 7, 9)
    s.shade(seed, ramp(GOLD, 5, spread=0.4), dither=0.3)
    s.line(16, 8, 16, 24, scale(c(GOLD), 0.7))
    s.glint(13, 11, big=True)
    s.outline(0.38)
    return s


@icon("award_jackpot")
def award_jackpot():
    s = Sprite(seed=316)
    box = rounded(3, 7, 28, 27, 3)
    s.shade(box, ramp("d83a3a", 5), dither=0.3)
    for k, x in enumerate((6, 13, 20)):
        s.fill(rect(x, 11, x + 5, 22), c("f8f6ee"))
        for (a, b) in ((0, 0), (1, 0), (2, 0), (3, 0), (3, 1), (2, 2), (2, 3), (1, 4), (1, 5), (1, 6)):
            s.put(x + 1 + a, 13 + b, c("e83a3a"))
    s.fill(rect(29, 9, 30, 15), c("a8a8b0"))
    s.fill(ellipse(29.5, 8, 1.8, 1.8), c("e83a3a"))
    s.outline(0.4)
    return s


@icon("award_smoke")
def award_smoke():
    s = Sprite(seed=317)
    cloud = ellipse(11, 18, 6, 5) | ellipse(18, 14, 7, 6) | ellipse(23, 20, 5.5, 4.5) | ellipse(16, 21, 7, 4)
    s.shade(cloud, ramp("d8d8e4", 5, spread=0.25), dither=0.5)
    s.fill(thick_line(3, 29, 11, 24, 2.4), c("f4f0e2"))
    s.fill(rect(10, 23, 12, 25), c("ff6a14"))
    s.outline(0.42)
    return s


@icon("award_rainbow")
def award_rainbow():
    s = Sprite(seed=318)
    for k, col in enumerate(RAINBOW):
        ring = ellipse(16, 24, 15 - k * 1.6, 15 - k * 1.6) - ellipse(16, 24, 13.4 - k * 1.6, 13.4 - k * 1.6)
        s.fill({p for p in ring if p[1] < 24}, c(col))
    s.fill(ellipse(5, 24, 4, 2.6) | ellipse(27, 24, 4, 2.6), c("f8f8fc"))
    s.outline(0.4)
    return s


@icon("award_upgrade")
def award_upgrade():
    s = Sprite(seed=319)
    # an anvil with a star
    top = poly([(5, 12), (26, 12), (23, 16), (9, 16)])
    s.shade(top, ramp("6a6e78", 5), dither=0.3)
    s.fill(rect(12, 16, 19, 21), c("4a4e58"))
    s.fill(poly([(8, 21), (23, 21), (25, 26), (6, 26)]), c("5a5e68"))
    s.line(5, 12, 26, 12, c("a8acb8"))
    for (x, y) in ((15, 3), (14, 5), (15, 5), (16, 5), (13, 6), (17, 6), (15, 7), (15, 4)):
        s.put(x, y, c("ffe070"), 2)
    s.glint(22, 6, c("ffe070"), big=True)
    s.outline(0.4)
    return s


@icon("ui_wallet")
def ui_wallet():
    s = Sprite(seed=401)
    # a leather wallet with bills sticking out and a coin
    bills = poly([(7, 6), (24, 4), (25, 13), (8, 14)])
    s.shade(bills, ramp("6ac06a", 4, spread=0.25), rim=False)
    s.line(9, 8, 22, 6, c("aaf0a0"))
    body = rounded(4, 11, 27, 27, 3)
    s.shade(body, ramp("8a5230", 5, spread=0.35), dither=0.4)
    s.line(5, 13, 26, 13, c("5a321a"))
    flap = rounded(16, 15, 28, 23, 2)
    s.shade(flap, ramp("6a3a20", 4), dither=0.3)
    s.fill(ellipse(22, 19, 1.8, 1.8), c("f2c23a"))
    for x in range(6, 26, 3):
        s.put(x, 25, c("c8905a"))
    s.outline(0.4)
    coin(s, 8, 26, 3.6)
    s.outline(0.4)
    return s


@icon("ui_sell")
def ui_sell():
    s = Sprite(seed=402)
    for k in range(5):
        disc = ellipse(11, 26 - k * 3, 7, 2.6)
        s.shade(disc, ramp(GOLD, 5, spread=0.35), dither=0.2, rim=False)
        s.line(5, 27 - k * 3, 17, 27 - k * 3, scale(c(GOLD), 0.6))
    s.fill(ellipse(11, 13.5, 6, 2), c("ffe890"))
    bills = poly([(16, 10), (28, 8), (29, 22), (17, 24)])
    s.shade(bills, ramp("6ac06a", 4, spread=0.25), rim=False)
    for (x, y) in ((22, 13), (23, 13), (21, 14), (22, 15), (23, 16), (21, 17), (22, 17), (22, 12), (22, 18)):
        s.put(x, y, c("2a6a2a"))
    s.outline(0.4)
    return s


# ---------------------------------------------------------------------------
# 3.0: ergot, cartel, drying racks, new awards
# ---------------------------------------------------------------------------
@item("ergot")
def ergot():
    """A wheat ear with the dark purple ergot fungus growing out of it."""
    s = Sprite(seed=501)
    s.fill(thick_line(7, 31, 13, 18, 1.6), c("a89048"))
    s.line(13, 18, 20, 4, c("8a7434"))
    for k in range(7):
        t = k / 6
        x, y = 13 + t * 7, 19 - t * 14
        for side in (-1, 1):
            grain = ellipse(x + side * 2.4, y + 1, 2.6, 2.0)
            s.shade(grain, ramp("f0cc58", 4, spread=0.4), dither=0.3)
        s.line(x + 2, y - 1, x + 5, y - 4, c("d8c070"))
    # ergot horns: dark curved sclerotia sticking out of the ear
    for (x0, y0, x1, y1) in ((17, 10, 24, 6), (14, 15, 8, 10), (16, 13, 22, 16)):
        horn = thick_line(x0, y0, x1, y1, 2.2)
        s.shade(horn, ramp("4a2a5a", 4, spread=0.3), dither=0.3)
        s.put(int(x1), int(y1), c("9a6ab8"), 2)
    for (x, y) in ((23, 6), (9, 10), (21, 15)):
        s.put(x, y, c("d8b0f0"), 2)
    s.outline(0.4)
    return s


@icon("tab_cartel")
def tab_cartel():
    s = Sprite(seed=502)
    # a fedora over crossed money bags
    for x0 in (5, 17):
        bag = ellipse(x0 + 5, 22, 6, 6)
        s.shade(bag, ramp("c8a060", 5, spread=0.35), dither=0.3)
        s.fill(rect(x0 + 3, 15, x0 + 7, 16), c("8a6a3a"))
        s.put(x0 + 5, 22, c("3a8a2a"), 2)
        s.put(x0 + 5, 21, c("3a8a2a"), 2)
        s.put(x0 + 5, 23, c("3a8a2a"), 2)
    brim = ellipse(16, 13, 13, 3)
    s.shade(brim, ramp("3a3a44", 4, spread=0.35), rim=False)
    crown_m = poly([(9, 13), (10, 5), (16, 3), (22, 5), (23, 13)])
    s.shade(crown_m, ramp("4a4a56", 5, spread=0.35), dither=0.3)
    s.fill(rect(10, 10, 22, 11), c("c83a3a"))
    s.outline(0.4)
    return s


@icon("cartel_banner")
def cartel_banner():
    """A hanging banner (tinted with the cartel colour) on a gold pole."""
    s = Sprite(seed=503)
    s.fill(rect(4, 3, 27, 4), c("c8a040"))
    s.fill(rect(3, 2, 4, 5), c("f8d870"))
    s.fill(rect(27, 2, 28, 5), c("f8d870"))
    cloth = poly([(7, 5), (24, 5), (24, 27), (15.5, 22), (7, 27)])
    s.shade(cloth, grey_ramp(110, 250, 5), 1, dither=0.3)
    # emblem: a white skull-ish cannabis leaf
    cannabis_leaf(s, 15.5, 16, 6.5, layer=2, col="f4f4f4", fingers=5)
    s.outline(0.4)
    return s


@icon("ui_bank")
def ui_bank():
    s = Sprite(seed=504)
    door = ellipse(16, 16, 13, 13)
    s.shade(door, ramp("8a949e", 5, spread=0.35), dither=0.3)
    s.fill(ellipse(16, 16, 9, 9), c("5a626c"))
    s.shade(ellipse(16, 16, 8, 8), ramp("a8b0b8", 4, spread=0.3), dither=0.2)
    for k in range(6):
        a = k * math.pi / 3
        s.fill(thick_line(16, 16, 16 + math.cos(a) * 6.5, 16 + math.sin(a) * 6.5, 1.6), c("4a525c"))
    s.fill(ellipse(16, 16, 2.2, 2.2), c("f2c23a"))
    for k in range(8):
        a = k * math.pi / 4 + 0.3
        s.put(int(16 + math.cos(a) * 11), int(16 + math.sin(a) * 11), c("dce4ec"))
    s.outline(0.4)
    return s


@icon("ui_members")
def ui_members():
    s = Sprite(seed=505)
    # two members behind, the boss in front
    for (x, y, r, col, hat) in ((8, 12, 3.4, "6a5a8a", False), (24, 12, 3.4, "5a7a6a", False),
                                (16, 15, 4.4, "c83a3a", True)):
        body = ellipse(x, y + r + 6, r * 1.9, r * 1.5) - rect(0, int(y + r + 7), 31, 31)
        body |= rect(int(x - r * 1.9) + 1, int(y + r + 6), int(x + r * 1.9) - 1, min(28, int(y + r * 2 + 7)))
        s.shade(body, ramp(col, 4), dither=0.3)
        head = ellipse(x, y, r, r)
        s.shade(head, ramp("e8b890", 4), dither=0.2)
        if hat:
            s.fill(rect(int(x - r - 2), int(y - r + 1), int(x + r + 2), int(y - r + 1)), c("2a2a30"))
            s.fill(rect(int(x - r + 1), int(y - r - 3), int(x + r - 1), int(y - r)), c("3a3a44"))
            s.fill(rect(int(x - r + 1), int(y - r - 1), int(x + r - 1), int(y - r - 1)), c("c83a3a"))
    s.outline(0.4)
    return s


@icon("ui_shipment")
def ui_shipment():
    s = Sprite(seed=506)
    box = rect(4, 9, 27, 27)
    s.shade(box, ramp("b88a50", 5, spread=0.3), dither=0.3)
    for y in (14, 20):
        s.line(4, y, 27, y, c("7a5430"))
    s.fill(rect(4, 9, 27, 10), c("d8a868"))
    s.line(5, 26, 26, 11, c("8a6034"))
    s.fill(rect(11, 15, 20, 19), c("f4f0e2"))
    cannabis_leaf(s, 15.5, 18, 3.2, layer=0, col="4caa32", fingers=5)
    s.outline(0.4)
    return s


@icon("ui_rack")
def ui_rack():
    s = Sprite(seed=507)
    s.fill(rect(4, 6, 27, 7), c("8a6a4a"))
    for x in (9, 16, 23):
        s.line(x, 8, x, 12, c("c8c0b0"))
        s.fill(rect(x - 2, 12, x + 2, 13), c("a8a090"))
    s.fill(rect(15, 2, 16, 5), c("6a6a70"))
    s.put(15, 1, c("a8a8b0"))
    s.outline(0.4)
    return s


def gauge(step, steps=8):
    """A small glass tube filling up with green (drying progress)."""
    s = Sprite(seed=600 + step)
    tube = rounded(10, 3, 21, 28, 4)
    s.fill(tube, c("d8ecf4", 90))
    level = 26 - int(round(22 * step / steps))
    if step > 0:
        liquid = {(x, y) for (x, y) in tube if y >= level and 11 <= x <= 20}
        s.shade(liquid, ramp("5ae85a" if step == steps else "9ad84a", 4), dither=0.3)
    s.line(12, 6, 12, 25, alpha(c("ffffff"), 170), 2)
    s.outline(0.4)
    return s


for _step in range(9):
    ICONS[f"gauge_{_step}"] = (lambda st: (lambda: gauge(st)))(_step)


@icon("award_racks")
def award_racks():
    s = tab_dry()
    for (x, y) in ((5, 3), (27, 4)):
        s.glint(x, y, c("ffe070"), big=True)
    return s


@icon("award_climate")
def award_climate():
    s = Sprite(seed=510)
    for k in range(8):
        a = math.radians(k * 45 + 22)
        s.fill(thick_line(11, 11, 11 + math.cos(a) * 9, 11 + math.sin(a) * 9, 1.6), c("ffd84a"))
    s.shade(ellipse(11, 11, 5, 5), ramp("f8c83a", 4), dither=0.2)
    cannabis_leaf(s, 20, 24, 9, layer=0, col="4caa32", fingers=7)
    s.outline(0.4)
    return s


@icon("award_globe")
def award_globe():
    s = Sprite(seed=511)
    g = ellipse(16, 15, 12, 12)
    s.shade(g, ramp("3a8ad8", 5, spread=0.35), dither=0.3)
    for blob in (ellipse(11, 11, 5, 4), ellipse(20, 17, 5, 6), ellipse(13, 21, 3, 2)):
        s.shade(blob & g, ramp("5ab43a", 4), dither=0.3)
    s.fill(ellipse(16, 4.5, 6, 1.6) & g, c("f4f8ff"))
    s.fill(rect(14, 27, 17, 29), c("8a6a3a"))
    s.fill(rect(10, 29, 21, 30), c("6a4a2a"))
    s.outline(0.4)
    return s


@icon("award_seeds")
def award_seeds():
    s = Sprite(seed=512)
    for k, (x, col) in enumerate(((4, "e85a5a"), (12, "5aa8e8"), (20, "f0c850"))):
        bag = rounded(x, 8 + (k % 2) * 3, x + 9, 27, 2)
        s.shade(bag, ramp("efe2bf", 4, spread=0.3), dither=0.2)
        s.fill(rect(x + 2, 14 + (k % 2) * 3, x + 7, 21 + (k % 2) * 3), c(col))
        s.fill(rect(x, 8 + (k % 2) * 3, x + 9, 9 + (k % 2) * 3), c("c9b07c"))
    s.outline(0.4)
    return s


@icon("award_cartel")
def award_cartel():
    s = Sprite(seed=513)
    cloth = poly([(7, 5), (24, 5), (24, 27), (15.5, 22), (7, 27)])
    s.shade(cloth, ramp("c83a3a", 5), dither=0.3)
    s.fill(rect(4, 3, 27, 4), c("c8a040"))
    cannabis_leaf(s, 15.5, 16, 6.5, layer=0, col="f4f4f4", fingers=5)
    s.outline(0.4)
    return s


@icon("award_shipment")
def award_shipment():
    s = ui_shipment()
    for (x, y) in ((4, 4), (27, 5)):
        s.glint(x, y, c("ffe070"), big=True)
    return s


@icon("award_empire")
def award_empire():
    s = Sprite(seed=515)
    # a gold skyscraper skyline with a crown on top
    for (x0, x1, top, col) in ((4, 10, 16, "a8801a"), (11, 20, 10, "f2c23a"), (21, 27, 14, "c89a2a")):
        b = rect(x0, top, x1, 28)
        s.shade(b, ramp(col, 4, spread=0.3), dither=0.3)
        for y in range(top + 2, 27, 3):
            for x in range(x0 + 1, x1, 2):
                s.put(x, y, c("fff0a0"))
    crown(s, 15.5, 6, 6, 6)
    s.outline(0.4)
    return s


@icon("award_mythic")
def award_mythic():
    s = Sprite(seed=516)
    for k in range(12):
        a = math.radians(k * 30)
        hx = "%02x%02x%02x" % tuple(int(v * 255) for v in colorsys.hsv_to_rgb(k / 12, 0.75, 1.0))
        s.fill(thick_line(16, 16, 16 + math.cos(a) * 14, 16 + math.sin(a) * 14, 2.2), c(hx))
    gem = poly([(16, 6), (25, 15), (16, 27), (7, 15)])
    s.shade(gem, ramp("e86af0", 5, spread=0.4), dither=0.3)
    s.fill(poly([(16, 6), (19, 15), (16, 27), (13, 15)]), c("f8b4ff"))
    s.glint(13, 11, big=True)
    s.outline(0.4)
    return s


# ---------------------------------------------------------------------------
# 4.0: workers, the guide, the admin panel, market news, new effects
# ---------------------------------------------------------------------------
def worker_face(s, kind, x0, y0, k=2):
    """Pastes a worker's face from their skin (tools/workers.py), k times bigger, with their hat."""
    import workers
    face = workers.face_of(workers.skin(kind))
    for y in range(8):
        for x in range(8):
            col = face.getpixel((x, y))
            if col[3]:
                for dy in range(k):
                    for dx in range(k):
                        s.put(x0 + x * k + dx, y0 + y * k + dy, col)
    w = 8 * k
    if kind == "cook":
        # a tall white toque
        top = rect(x0 - 1, y0 - 7, x0 + w, y0 - 1)
        s.shade(top, ramp("f4f4ee", 4, spread=0.15), dither=0.2)
        s.fill(rect(x0, y0 - 1, x0 + w - 1, y0 + 1), c("e6e6e0"))
        for xx in range(x0 + 1, x0 + w, 3):
            s.line(xx, y0 - 6, xx, y0 - 2, c("d0d0c8"))
        for (x, y) in edge(top | rect(x0, y0 - 1, x0 + w - 1, y0 + 1)):
            s.put(x, y, c("8a8a84"))
    elif kind == "farmhand":
        # straw hat: wide brim and crown with a red band
        s.shade(rect(x0 - 3, y0 - 1, x0 + w + 2, y0), ramp("e8c870", 3, spread=0.3), rim=False)
        s.shade(rect(x0 + 2, y0 - 6, x0 + w - 3, y0 - 2), ramp("ecd078", 4, spread=0.3), dither=0.3)
        s.fill(rect(x0 + 2, y0 - 3, x0 + w - 3, y0 - 2), c("c8302a"))
    else:
        s.shade(rect(x0, y0 - 4, x0 + w - 1, y0 + 1), ramp("2e8a44", 4, spread=0.3), dither=0.3)
        s.fill(rect(x0 - 1, y0 + 1, x0 + w + 1, y0 + 2), c("1e5a2e"))
        s.fill(rect(x0 + w // 2 - 1, y0 - 3, x0 + w // 2, y0 - 2), c("c8f05a"))


def contract(kind, ribbon):
    s = Sprite(seed={"farmhand": 701, "dryer": 702, "cook": 708}[kind])
    card = rounded(4, 5, 27, 29, 2)
    s.shade(card, ramp("f0e6c8", 4, spread=0.2), dither=0.3, rim=False)
    s.fill(rect(4, 25, 27, 29), c(ribbon))
    s.line(5, 25, 26, 25, scale(c(ribbon), 1.25))
    worker_face(s, kind, 8, 9, 2)
    for x in range(7, 25, 2):
        s.put(x, 27, scale(c(ribbon), 0.6))
    s.outline(0.4)
    return s


@item("worker_farmhand")
def worker_farmhand():
    return contract("farmhand", "4caa32")


@item("worker_dryer")
def worker_dryer():
    return contract("dryer", "e8a832")


@item("worker_cook")
def worker_cook():
    return contract("cook", "3ac8e8")


@icon("ui_workers")
def ui_workers():
    s = Sprite(seed=703)
    worker_face(s, "dryer", 15, 13, 2)
    worker_face(s, "farmhand", 2, 10, 2)
    s.outline(0.4)
    return s


@icon("award_worker")
def award_worker():
    s = Sprite(seed=704)
    medal(s, 16, 20, 10, "e8c870", ribbon=("4caa32", "e8a832"))
    worker_face(s, "farmhand", 12, 17, 1)
    s.outline(0.4)
    return s


@icon("award_workforce")
def award_workforce():
    s = Sprite(seed=705)
    for (kind, x, y) in (("dryer", 3, 9), ("farmhand", 17, 9), ("farmhand", 3, 22), ("dryer", 17, 22)):
        worker_face(s, kind, x + 2, y - 1, 1)
    for (x, y) in ((11, 4), (24, 4)):
        s.glint(x, y, c("ffe070"), big=True)
    s.outline(0.4)
    return s


@icon("ui_gear")
def ui_gear():
    """A red toolbox with a wrench sticking out."""
    s = Sprite(seed=706)
    wrench = thick_line(8, 5, 18, 16, 2.4)
    s.shade(wrench, ramp("c8d0d8", 4, spread=0.35), dither=0.2)
    s.fill(ellipse(8, 5, 3.2, 3.2) - ellipse(7, 4, 1.4, 1.4), c("b8c0c8"))
    box = rounded(3, 14, 28, 28, 2)
    s.shade(box, ramp("d83a2a", 5, spread=0.35), dither=0.3)
    s.fill(rect(3, 18, 28, 19), c("8a1e14"))
    s.fill(rect(14, 17, 17, 20), c("e8e8e8"))
    s.fill(rect(10, 10, 21, 11) | rect(10, 10, 11, 14) | rect(20, 10, 21, 14), c("3a3a44"))
    s.outline(0.4)
    return s


@icon("ui_market")
def ui_market():
    """A newspaper page with a price chart: one line up, one down."""
    s = Sprite(seed=707)
    paper = rect(4, 4, 27, 27)
    s.shade(paper, ramp("f0ecdc", 3, spread=0.15), rim=False)
    s.fill(rect(6, 6, 25, 8), c("3a3a3a"))
    for y in (11, 13):
        s.line(6, y, 13, y, c("9a9a9a"))
    pts = [(6, 24), (10, 21), (13, 22), (17, 16), (21, 17), (25, 11)]
    for a, b in zip(pts, pts[1:]):
        s.fill(thick_line(a[0], a[1], b[0], b[1], 1.6), c("3ab43a"))
    pts = [(15, 12), (18, 15), (21, 14), (25, 21)]
    for a, b in zip(pts, pts[1:]):
        s.line(a[0], a[1], b[0], b[1], c("d83a2a"))
    s.outline(0.4)
    return s


@icon("ui_guide")
def ui_guide():
    """A glowing light bulb: the next step."""
    s = Sprite(seed=708)
    for k in range(8):
        a = math.radians(k * 45)
        x0, y0 = 16 + math.cos(a) * 11, 12 + math.sin(a) * 11
        x1, y1 = 16 + math.cos(a) * 14, 12 + math.sin(a) * 14
        if y1 < 22:
            s.fill(thick_line(x0, y0, x1, y1, 1.5), c("ffe680"), 2)
    bulb = ellipse(16, 12, 7.5, 7.5) | poly([(11, 15), (21, 15), (19, 21), (13, 21)])
    s.shade(bulb, ramp("ffd83a", 5, spread=0.4), dither=0.3)
    s.fill(ellipse(13.5, 9.5, 2, 2.5), c("fffbe0"))
    s.line(14, 14, 15, 18, c("c88a1a"))
    s.line(18, 14, 17, 18, c("c88a1a"))
    base = rect(12, 22, 20, 27)
    s.shade(base, ramp("a8b0b8", 4, spread=0.3), rim=False)
    for y in (23, 25):
        s.line(12, y, 20, y, c("6a727a"))
    s.fill(rect(14, 28, 18, 28), c("4a525a"))
    s.outline(0.4)
    return s


@icon("ui_admin")
def ui_admin():
    """A red shield with a gold gavel."""
    s = Sprite(seed=709)
    shield = poly([(5, 4), (27, 4), (27, 15), (16, 29), (5, 15)])
    s.shade(shield, ramp("c82a2a", 5, spread=0.35), dither=0.3)
    s.fill(poly([(7, 6), (25, 6), (25, 8), (7, 8)]), c("e84a4a"))
    s.fill(thick_line(11, 22, 19, 13, 1.8), c("8a5a2a"))
    head = poly([(15, 10), (19, 6), (25, 12), (21, 16)])
    s.shade(head, ramp(GOLD, 4, spread=0.35), dither=0.2)
    s.outline(0.4)
    return s


@icon("ui_rename")
def ui_rename():
    """A name tag."""
    s = Sprite(seed=710)
    tag = poly([(4, 13), (10, 7), (28, 7), (28, 21), (10, 21)])
    s.shade(tag, ramp("e8d8b0", 4, spread=0.25), dither=0.2)
    s.fill(ellipse(9, 14, 1.6, 1.6), c("6a5a3a"))
    s.fill(thick_line(2, 10, 8, 14, 1.0), c("c8c8c8"))
    for y in (11, 14, 17):
        s.line(14, y, 25 - (y % 3), y, c("8a7a5a"))
    s.outline(0.4)
    return s


def round_button(seed, col):
    s = Sprite(seed=seed)
    s.shade(ellipse(16, 16, 12, 12), ramp(col, 5, spread=0.4), dither=0.3)
    return s


@icon("ui_pause")
def ui_pause():
    s = round_button(711, "f0a03a")
    s.fill(rect(11, 9, 14, 23), c("fff4e0"))
    s.fill(rect(18, 9, 21, 23), c("fff4e0"))
    s.outline(0.4)
    return s


@icon("ui_play")
def ui_play():
    s = round_button(712, "4cc84a")
    s.fill(poly([(12, 8), (24, 16), (12, 24)]), c("f4fff0"))
    s.outline(0.4)
    return s


@icon("ui_dismiss")
def ui_dismiss():
    """A pink slip with a red X stamp."""
    s = Sprite(seed=713)
    slip = poly([(6, 4), (26, 6), (24, 29), (5, 27)])
    s.shade(slip, ramp("f8c8d0", 3, spread=0.2), rim=False)
    for y in (9, 12):
        s.line(9, y, 22, y + 1, c("c890a0"))
    s.fill(thick_line(10, 15, 21, 25, 2.4), c("d82a2a"))
    s.fill(thick_line(21, 15, 10, 25, 2.4), c("d82a2a"))
    s.outline(0.4)
    return s


@icon("ui_take")
def ui_take():
    """A sack with an arrow coming out: take everything."""
    s = Sprite(seed=714)
    sack = ellipse(14, 21, 10, 8) | poly([(9, 12), (19, 12), (22, 18), (6, 18)])
    s.shade(sack, ramp("b8945a", 5, spread=0.35), dither=0.4)
    s.fill(rect(9, 11, 19, 12), c("6a4a2a"))
    s.speckle(sack, [scale(c("b8945a"), 0.8)], 0.12)
    arrow(s, 18, 14, 28, 4, "5ae85a", w=2.6, head=4.2)
    s.outline(0.4)
    return s


# ---- the new effects ------------------------------------------------------
@icon("effect_green_thumb")
def effect_green_thumb():
    """A green thumbs-up with a sprout."""
    s = Sprite(seed=720)
    thumb = rounded(8, 14, 22, 28, 3) | rounded(12, 6, 17, 17, 2)
    s.shade(thumb, ramp("5ad84a", 5, spread=0.4), dither=0.3)
    for y in (18, 21, 24):
        s.line(16, y, 21, y, c("2a8a2a"))
    s.fill(thick_line(24, 16, 24, 9, 1.2), c("3a8a2a"))
    s.fill(ellipse(21, 8, 3, 1.8), c("7ae85a"))
    s.fill(ellipse(27, 7, 3, 1.8), c("5ac84a"))
    s.outline(0.4)
    return s


@icon("effect_smooth_talker")
def effect_smooth_talker():
    """A speech bubble with a gold coin."""
    s = Sprite(seed=721)
    bubble = rounded(3, 4, 28, 21, 5) | poly([(8, 20), (14, 20), (6, 27)])
    s.shade(bubble, ramp("fff4d8", 4, spread=0.2), dither=0.2)
    coin(s, 16, 12.5, 5.5)
    s.fill(rect(15, 9, 16, 16), c("b88a1a"))
    s.outline(0.4)
    return s


@icon("effect_frosty")
def effect_frosty():
    """A snowflake."""
    s = Sprite(seed=722)
    for k in range(6):
        a = math.radians(k * 60 + 90)
        x1, y1 = 16 + math.cos(a) * 12, 16 + math.sin(a) * 12
        s.fill(thick_line(16, 16, x1, y1, 1.8), c("bfeeff"))
        for side in (-1, 1):
            bx, by = 16 + math.cos(a) * 7, 16 + math.sin(a) * 7
            s.line(bx, by, bx + math.cos(a + side * 0.8) * 4, by + math.sin(a + side * 0.8) * 4, c("8ad8ff"))
    s.fill(ellipse(16, 16, 2.5, 2.5), c("ffffff"))
    s.outline(0.4, fixed=c("2a5a8a"))
    return s


@icon("effect_magnetic")
def effect_magnetic():
    """A red horseshoe magnet pulling sparks."""
    s = Sprite(seed=723)
    ring = ellipse(16, 14, 11, 11) - ellipse(16, 14, 5.5, 5.5)
    shape = (ring - rect(0, 15, 31, 31)) | rect(5, 14, 10, 25) | rect(22, 14, 27, 25)
    s.shade(shape, ramp("e83a3a", 5, spread=0.35), dither=0.3)
    s.fill(rect(5, 22, 10, 25) | rect(22, 22, 27, 25), c("dce4ec"))
    for (x, y) in ((8, 29), (16, 27), (24, 29), (16, 30)):
        s.glint(x, y, c("c88aff"), big=True)
    s.outline(0.4)
    return s


@icon("effect_sixth_sense")
def effect_sixth_sense():
    """An eye with a glowing violet iris."""
    s = Sprite(seed=724)
    eye = poly([(3, 16), (9, 9), (16, 7), (23, 9), (29, 16), (23, 23), (16, 25), (9, 23)])
    s.shade(eye, ramp("f4f0ff", 3, spread=0.15), rim=False)
    s.shade(ellipse(16, 16, 6.5, 6.5), ramp("9a5af0", 5, spread=0.4), dither=0.3)
    s.fill(ellipse(16, 16, 2.6, 2.6), c("1a0a2a"))
    s.put(14, 13, c("ffffff"))
    for (x, y) in ((4, 5), (28, 5), (16, 2)):
        s.glint(x, y, c("e0c8ff"), big=True)
    s.outline(0.4)
    return s


@icon("effect_zen")
def effect_zen():
    """A lotus flower."""
    s = Sprite(seed=725)
    for (pts, col) in (
            ([(16, 5), (21, 14), (16, 22), (11, 14)], "f8a8d8"),
            ([(5, 11), (14, 15), (15, 22), (8, 19)], "e88ac8"),
            ([(27, 11), (18, 15), (17, 22), (24, 19)], "e88ac8"),
            ([(3, 19), (13, 20), (14, 24), (6, 24)], "c86aa8"),
            ([(29, 19), (19, 20), (18, 24), (26, 24)], "c86aa8")):
        s.shade(poly(pts), ramp(col, 4, spread=0.3), dither=0.2)
    s.shade(ellipse(16, 26, 11, 2.5), ramp("4ab89a", 4, spread=0.3), rim=False)
    s.outline(0.4)
    return s


@icon("award_forager")
def award_forager():
    """A woven basket with a wild cannabis leaf and a mushroom."""
    s = Sprite(seed=711)
    cannabis_leaf(s, 13, 11, 9, layer=0, col="4caa32")
    shroom(s, 21, 14, 7, 7)
    basket = poly([(4, 15), (28, 15), (25, 29), (7, 29)])
    s.shade(basket, ramp("c8904a", 5, spread=0.35), dither=0.3)
    for y in range(17, 29, 3):
        s.line(5, y, 27, y, c("8a5a2a"))
    for x in range(8, 26, 4):
        s.line(x, 16, x, 28, c("a8743a"))
    s.fill(rect(3, 14, 29, 16), c("e0a85a"))
    s.outline(0.42)
    return s


@icon("award_chef")
def award_chef():
    s = Sprite(seed=712)
    medal(s, 16, 20, 10, "e8c870", ribbon=("3ac8e8", "e8e8f0"))
    worker_face(s, "cook", 12, 17, 1)
    s.outline(0.4)
    return s


@icon("award_red_eyes")
def award_red_eyes():
    """A pig's face with bloodshot red eyes and a puff of smoke."""
    s = Sprite(seed=713)
    head = rounded(5, 8, 26, 27, 5)
    s.shade(head, ramp("f0a0a8", 5, spread=0.3), dither=0.3)
    for (x0, x1) in ((5, 9), (22, 26)):
        s.fill(poly([(x0, 10), (x1, 4), (x1 if x0 == 5 else x0, 11)]), c("e8909a"))
    snout = rounded(11, 18, 20, 24, 2)
    s.shade(snout, ramp("f8b8c0", 4, spread=0.25))
    s.fill(rect(13, 20, 14, 22), c("a85a64"))
    s.fill(rect(17, 20, 18, 22), c("a85a64"))
    for x0 in (8, 19):
        s.fill(rect(x0, 12, x0 + 4, 16), c("ffffff"))
        s.fill(rect(x0 + 1, 13, x0 + 3, 15), c("e81a1a"))
        s.put(x0 + 2, 14, c("6a0a0a"))
        s.line(x0, 12, x0 + 4, 12, c("e85a5a"))
    for (x, y, a) in ((27, 6, 200), (28, 3, 160), (26, 1, 120)):
        s.fill(ellipse(x, y, 2, 1.6), alpha(c("d8d8d8"), a), 2)
    s.outline(0.42)
    return s
