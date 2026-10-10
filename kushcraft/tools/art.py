"""A small toolkit for 32x32 pixel art: hard-edged shapes, light from the
top-left, hue-shifted colour ramps, ordered dithering and selective
outlines. Every sprite has the same three layers as sprites.py: 0 = plain,
1 = tinted in game with the strain colour (draw it in greys), 2 = overlay.
"""
import colorsys
import math
import random

from PIL import Image, ImageDraw

SIZE = 32
LIGHT = (-0.62, -0.78)  # towards the light: up and to the left

BAYER4 = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


# ---------------------------------------------------------------------------
# colours
# ---------------------------------------------------------------------------
def c(h, a=255):
    """'rrggbb' (or 'rrggbbaa') -> RGBA tuple."""
    h = h.lstrip("#")
    if len(h) == 8:
        a = int(h[6:8], 16)
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (a[3] if len(a) > 3 else 255,)


def scale(col, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in col[:3]) + (col[3] if len(col) > 3 else 255,)


def alpha(col, a):
    return col[:3] + (a,)


def grey(v, a=255):
    return (v, v, v, a)


def ramp(base, n=5, spread=0.42, hue=0.035, sat=0.12):
    """Dark -> light ramp around a base colour. Shadows drift towards blue /
    purple and get more saturated, highlights drift towards yellow."""
    if isinstance(base, str):
        base = c(base)
    r, g, b = [v / 255 for v in base[:3]]
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    out = []
    for i in range(n):
        t = i / (n - 1) * 2 - 1  # -1 .. 1
        hh = (h - hue * t) % 1.0 if s > 0.05 else h
        # warm highlights: pull the hue towards yellow (1/6), cool shadows towards blue (2/3)
        target = 1 / 6 if t > 0 else 2 / 3
        if s > 0.05:
            d = ((target - h + 0.5) % 1.0) - 0.5
            hh = (h + d * abs(t) * hue * 4) % 1.0
        ll = max(0.03, min(0.97, l + t * spread * (0.55 if t > 0 else 0.5)))
        ss = max(0, min(1, s + (-t) * sat))
        rr, gg, bb = colorsys.hls_to_rgb(hh, ll, ss)
        out.append((int(rr * 255), int(gg * 255), int(bb * 255), base[3] if len(base) > 3 else 255))
    return out


def grey_ramp(lo=70, hi=250, n=5):
    return [grey(int(lo + (hi - lo) * i / (n - 1))) for i in range(n)]


# ---------------------------------------------------------------------------
# masks (sets of (x, y))
# ---------------------------------------------------------------------------
def _from_draw(fn, size=SIZE):
    img = Image.new("L", (size, size), 0)
    fn(ImageDraw.Draw(img))
    px = img.load()
    return {(x, y) for y in range(size) for x in range(size) if px[x, y] > 127}


def poly(pts, size=SIZE):
    return _from_draw(lambda d: d.polygon([tuple(p) for p in pts], fill=255), size)


def ellipse(cx, cy, rx, ry, size=SIZE):
    """Pixel-centred ellipse: every pixel whose centre is inside."""
    out = set()
    for y in range(size):
        for x in range(size):
            if ((x + 0.5 - cx) / max(rx, 0.01)) ** 2 + ((y + 0.5 - cy) / max(ry, 0.01)) ** 2 <= 1.0:
                out.add((x, y))
    return out


def rect(x0, y0, x1, y1):
    """Inclusive rectangle."""
    return {(x, y) for y in range(y0, y1 + 1) for x in range(x0, x1 + 1)}


def rounded(x0, y0, x1, y1, r):
    m = rect(x0, y0, x1, y1)
    for (cx, cy) in ((x0 + r, y0 + r), (x1 - r, y0 + r), (x0 + r, y1 - r), (x1 - r, y1 - r)):
        for (x, y) in list(m):
            inx = (x < x0 + r and cx == x0 + r) or (x > x1 - r and cx == x1 - r)
            iny = (y < y0 + r and cy == y0 + r) or (y > y1 - r and cy == y1 - r)
            if inx and iny and (x - cx) ** 2 + (y - cy) ** 2 > r * r + 0.5:
                m.discard((x, y))
    return m


def thick_line(x0, y0, x1, y1, w, size=SIZE):
    """A line of width w as a polygon."""
    dx, dy = x1 - x0, y1 - y0
    L = math.hypot(dx, dy) or 1
    nx, ny = -dy / L * w / 2, dx / L * w / 2
    return poly([(x0 + nx, y0 + ny), (x1 + nx, y1 + ny), (x1 - nx, y1 - ny), (x0 - nx, y0 - ny)], size)


def line_px(x0, y0, x1, y1):
    """1px Bresenham line as a mask."""
    x0, y0, x1, y1 = int(round(x0)), int(round(y0)), int(round(x1)), int(round(y1))
    out = set()
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        out.add((x0, y0))
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy
    return out


def shift(m, dx, dy):
    return {(x + dx, y + dy) for (x, y) in m}


def edge(m):
    """Pixels of m that touch the outside (4-neighbours)."""
    return {(x, y) for (x, y) in m if any((x + a, y + b) not in m for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))}


def inside(m, size=SIZE):
    return {(x, y) for (x, y) in m if 0 <= x < size and 0 <= y < size}


# ---------------------------------------------------------------------------
# sprite
# ---------------------------------------------------------------------------
class Sprite:
    def __init__(self, size=SIZE, seed=0, layers=3):
        self.size = size
        self.layers = [Image.new("RGBA", (size, size), (0, 0, 0, 0)) for _ in range(layers)]
        self.rng = random.Random(seed)

    def img(self, layer=0):
        return self.layers[layer]

    def put(self, x, y, col, layer=0):
        if 0 <= x < self.size and 0 <= y < self.size:
            # a pixel lives on exactly one layer
            for i, l in enumerate(self.layers):
                if i != layer and col[3] == 255:
                    l.putpixel((x, y), (0, 0, 0, 0))
            self.layers[layer].putpixel((x, y), col)

    def get(self, x, y):
        """(colour, layer) of the top-most pixel, or (None, None)."""
        for i in reversed(range(len(self.layers))):
            p = self.layers[i].getpixel((x, y))
            if p[3] > 0:
                return p, i
        return None, None

    def opaque(self):
        out = set()
        for y in range(self.size):
            for x in range(self.size):
                if self.get(x, y)[0] is not None:
                    out.add((x, y))
        return out

    # -- painting ---------------------------------------------------------
    def fill(self, mask, col, layer=0):
        for (x, y) in inside(mask, self.size):
            self.put(x, y, col, layer)

    def shade(self, mask, rmp, layer=0, light=LIGHT, dither=0.5, gamma=1.0, rim=True, flat_from=None):
        """Volume shading: how far a pixel is from the lit edge vs the dark
        edge (along the light direction) picks the ramp colour."""
        mask = inside(mask, self.size)
        lx, ly = light
        n = len(rmp)
        for (x, y) in mask:
            a = b = 0
            for k in range(1, 40):
                if (int(round(x + lx * k)), int(round(y + ly * k))) not in mask:
                    break
                a += 1
            for k in range(1, 40):
                if (int(round(x - lx * k)), int(round(y - ly * k))) not in mask:
                    break
                b += 1
            t = (b + 0.5) / (a + b + 1.0)
            t = t ** gamma
            v = t * (n - 1)
            if dither:
                v += (BAYER4[y % 4][x % 4] / 16.0 - 0.47) * dither
            i = max(0, min(n - 1, int(round(v))))
            self.put(x, y, rmp[i], layer)
        if rim:
            # crisp light rim on the lit (top / left) edge
            for (x, y) in edge(mask):
                lit = (x - 1, y) not in mask or (x, y - 1) not in mask
                dark = (x + 1, y) not in mask or (x, y + 1) not in mask
                if lit and not dark:
                    self.put(x, y, rmp[-1], layer)

    def facets(self, faces, layer=0):
        """faces: list of (polygon points, colour) drawn in order."""
        for pts, col in faces:
            self.fill(poly(pts, self.size), col, layer)

    def speckle(self, mask, cols, density, layer=0):
        for (x, y) in sorted(inside(mask, self.size)):
            if self.rng.random() < density:
                self.put(x, y, self.rng.choice(cols) if isinstance(cols, list) else cols, layer)

    def line(self, x0, y0, x1, y1, col, layer=0):
        self.fill(line_px(x0, y0, x1, y1), col, layer)

    def outline(self, factor=0.38, fixed=None, corners=False):
        """Selective outline: every empty pixel next to the sprite gets a dark
        version of its neighbour (on the neighbour's layer, so tinted parts
        get a tinted outline)."""
        solid = self.opaque()
        adds = []
        for y in range(self.size):
            for x in range(self.size):
                if (x, y) in solid:
                    continue
                nb = [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]
                if corners:
                    nb += [(x + 1, y + 1), (x - 1, y - 1), (x + 1, y - 1), (x - 1, y + 1)]
                best = None
                for (a, b) in nb:
                    if (a, b) in solid:
                        col, layer = self.get(a, b)
                        lum = col[0] * 0.3 + col[1] * 0.59 + col[2] * 0.11
                        if best is None or lum < best[0]:
                            best = (lum, col, layer)
                if best:
                    col = fixed if fixed else scale(best[1], factor)
                    adds.append((x, y, alpha(col, 255), 0 if fixed else best[2]))
        for (x, y, col, layer) in adds:
            self.put(x, y, col, layer)

    def glint(self, x, y, col=(255, 255, 255, 255), big=False, layer=2):
        """A little sparkle: a dot, or a 4-point star."""
        self.put(x, y, col, layer)
        if big:
            soft = alpha(col, 170)
            for (a, b) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                self.put(x + a, y + b, soft, layer)

    def flatten(self, tint=None):
        """Composite for previews. tint: colour of layer 1, or {layer: colour}."""
        tints = tint if isinstance(tint, dict) else ({1: tint} if tint is not None else {})
        base = Image.new("RGBA", (self.size, self.size), (0, 0, 0, 0))
        for i, l in enumerate(self.layers):
            if i in tints:
                tint = tints[i]
                r, g, b = (tint >> 16) & 255, (tint >> 8) & 255, tint & 255
                l = l.copy()
                p = l.load()
                for y in range(l.height):
                    for x in range(l.width):
                        pr, pg, pb, pa = p[x, y]
                        p[x, y] = (pr * r // 255, pg * g // 255, pb * b // 255, pa)
            base = Image.alpha_composite(base, l)
        return base
