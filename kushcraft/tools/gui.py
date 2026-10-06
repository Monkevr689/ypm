"""Full-window GUI backgrounds. Each one is drawn as a font glyph in the
inventory title (shifted left with negative-space characters), so chest
menus get a completely custom look. Slot coordinates follow the vanilla
chest layout: container slot (row, col) has its frame at x=7+18*col,
y=17+18*row.

3.0 style: one dark panel, icon tabs along the top (the active one lit up
and joined to the page), the page name in big letters, slot frames only
where something goes, and a little scene behind each page: wooden seed
shelves in the Shop, market crates in Trade, a red-and-gold cartel room,
a drying room with five racks, a trophy cabinet for the Awards.

The layouts here MUST match dev.kushcraft.gui.* in the Java code
(tools/validate_pack.py checks the tab order, row counts and item counts).
"""
import math
import os
import random

from PIL import Image, ImageDraw

G = None

# 3x5 pixel font for the few words drawn into the backgrounds
FONT = {
    "A": ["010", "101", "111", "101", "101"], "B": ["110", "101", "110", "101", "110"],
    "C": ["011", "100", "100", "100", "011"], "D": ["110", "101", "101", "101", "110"],
    "E": ["111", "100", "110", "100", "111"], "F": ["111", "100", "110", "100", "100"],
    "G": ["011", "100", "101", "101", "011"], "H": ["101", "101", "111", "101", "101"],
    "I": ["111", "010", "010", "010", "111"], "J": ["001", "001", "001", "101", "010"],
    "K": ["101", "101", "110", "101", "101"], "L": ["100", "100", "100", "100", "111"],
    "M": ["101", "111", "111", "101", "101"], "N": ["110", "101", "101", "101", "101"],
    "O": ["010", "101", "101", "101", "010"], "P": ["110", "101", "110", "100", "100"],
    "Q": ["010", "101", "101", "110", "011"], "R": ["110", "101", "110", "101", "101"],
    "S": ["011", "100", "010", "001", "110"], "T": ["111", "010", "010", "010", "010"],
    "U": ["101", "101", "101", "101", "111"], "V": ["101", "101", "101", "101", "010"],
    "W": ["101", "101", "111", "111", "101"], "X": ["101", "101", "010", "101", "101"],
    "Y": ["101", "101", "010", "010", "010"], "Z": ["111", "001", "010", "100", "111"],
    "0": ["111", "101", "101", "101", "111"], "1": ["010", "110", "010", "010", "111"],
    "2": ["110", "001", "010", "100", "111"], "3": ["110", "001", "010", "001", "110"],
    "4": ["101", "101", "111", "001", "001"], "5": ["111", "100", "110", "001", "110"],
    "6": ["011", "100", "111", "101", "111"], "7": ["111", "001", "010", "010", "010"],
    "8": ["111", "101", "111", "101", "111"], "9": ["111", "101", "111", "001", "110"],
    "+": ["000", "010", "111", "010", "000"], "-": ["000", "000", "111", "000", "000"],
    ">": ["100", "010", "001", "010", "100"], "$": ["011", "110", "010", "011", "110"],
    " ": ["000", "000", "000", "000", "000"], "#": ["101", "111", "101", "111", "101"],
    "?": ["110", "001", "010", "000", "010"], "!": ["010", "010", "010", "000", "010"],
    ":": ["000", "010", "000", "010", "000"], ".": ["000", "000", "000", "000", "010"],
    ",": ["000", "000", "000", "010", "100"], "'": ["010", "010", "000", "000", "000"],
    "/": ["001", "001", "010", "100", "100"], "(": ["010", "100", "100", "100", "010"],
    ")": ["010", "001", "001", "001", "010"], "=": ["000", "111", "000", "111", "000"],
    "%": ["101", "001", "010", "100", "101"], "*": ["000", "101", "010", "101", "000"],
    "&": ["010", "101", "010", "101", "011"],
}


def text(img, x, y, s, col, shadow=None, scale=1):
    for ch in s.upper():
        glyph = FONT.get(ch, FONT["?"])
        for gy, row in enumerate(glyph):
            for gx, b in enumerate(row):
                if b != "1":
                    continue
                for sy in range(scale):
                    for sx in range(scale):
                        px, py = x + gx * scale + sx, y + gy * scale + sy
                        if shadow:
                            img.putpixel((px + scale, py + scale), shadow)
                        img.putpixel((px, py), col)
        x += 4 * scale
    return x


def text_width(s, scale=1):
    return (len(s) * 4 - 1) * scale


def rgba(h):
    return G.rgba(h)


def shade(col, f):
    return G.shade(col, f)


def mix(a, b, t):
    return G.mix(a, b, t)


# ---------------------------------------------------------------------------
# palette
# ---------------------------------------------------------------------------
BASE = "1f2823"
INNER = "161d19"
LIGHT = "3a4c41"
DARK = "0b100d"
SLOT = "0f1512"
SLOT_HI = "34463b"
SLOT_LO = "050806"
TEXT = "d8e4dc"

ACCENT = {
    "shop": "f2c23a", "drugs": "7ae05a", "trade": "5ad8e8", "cartel": "f04a4a", "top": "f09a3a", "awards": "c08aff",
    "cook": "5ae8c8", "roll": "9ae85a", "dry": "f0c850", "mix": "e85ad0", "recipe": "f0b860", "list": "7ad0e8",
    "gear": "5ad8e8", "worker": "e8c870", "guide": "5ad8f0", "admin": "e84a4a",
}


def size(rows):
    return 176, 114 + rows * 18


def slot_xy(row, col):
    return 7 + col * 18, 17 + row * 18


def panel(rows, seed):
    """Rounded dark panel with a bevel and a faint diagonal grain."""
    W, H = size(rows)
    rng = random.Random(seed)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    base = rgba(BASE)
    px = img.load()
    for y in range(H):
        for x in range(W):
            f = 1 + 0.03 * math.sin((x * 0.7 + y) * 0.35) + rng.uniform(-0.015, 0.015)
            px[x, y] = shade(base, f)
    d = ImageDraw.Draw(img)
    for (x, y) in ((0, 0), (1, 0), (0, 1), (W - 1, 0), (W - 2, 0), (W - 1, 1), (0, H - 1), (1, H - 1), (0, H - 2),
                   (W - 1, H - 1), (W - 2, H - 1), (W - 1, H - 2)):
        px[x, y] = (0, 0, 0, 0)
    black = (0, 0, 0, 255)
    d.line((2, 0, W - 3, 0), fill=black)
    d.line((2, H - 1, W - 3, H - 1), fill=black)
    d.line((0, 2, 0, H - 3), fill=black)
    d.line((W - 1, 2, W - 1, H - 3), fill=black)
    for p in ((1, 1), (W - 2, 1), (1, H - 2), (W - 2, H - 2)):
        px[p] = black
    d.line((2, 1, W - 3, 1), fill=rgba(LIGHT))
    d.line((1, 2, 1, H - 3), fill=rgba(LIGHT))
    d.line((2, H - 2, W - 3, H - 2), fill=rgba(DARK))
    d.line((W - 2, 2, W - 2, H - 3), fill=rgba(DARK))
    return img


def slot(img, x, y, style="normal", accent=None, tint=None):
    """x, y: top-left of the 18x18 frame. tint: colour mixed into the inside."""
    d = ImageDraw.Draw(img)
    inner = rgba(SLOT) if tint is None else mix(rgba(SLOT), rgba(tint), 0.16)
    if style in ("big", "gold", "glow") and accent is not None:
        a = rgba(accent)
        d.rectangle((x - 2, y - 2, x + 19, y + 19), outline=shade(a, 0.55))
        d.rectangle((x - 1, y - 1, x + 18, y + 18), outline=a)
        if style == "glow":
            for (gx, gy) in ((x - 3, y + 8), (x + 20, y + 8), (x + 8, y - 3), (x + 8, y + 20)):
                d.rectangle((gx, gy, gx + 1, gy + 1), fill=shade(a, 0.7))
    d.rectangle((x, y, x + 17, y + 17), fill=inner)
    d.line((x, y, x + 16, y), fill=rgba(SLOT_LO))
    d.line((x, y, x, y + 16), fill=rgba(SLOT_LO))
    d.line((x + 1, y + 17, x + 17, y + 17), fill=rgba(SLOT_HI))
    d.line((x + 17, y + 1, x + 17, y + 17), fill=rgba(SLOT_HI))


def cslot(img, row, col, style="normal", accent=None, tint=None):
    x, y = slot_xy(row, col)
    slot(img, x, y, style, accent, tint)


def row_marker(img, row, col):
    """A short colour bar in the left margin of a row."""
    d = ImageDraw.Draw(img)
    x, y = slot_xy(row, 0)
    d.rectangle((3, y + 2, 4, y + 15), fill=rgba(col))


def player_inv(img, rows):
    top = rows * 18 + 30
    d = ImageDraw.Draw(img)
    d.line((5, top - 4, 170, top - 4), fill=rgba(DARK))
    d.line((5, top - 3, 170, top - 3), fill=rgba(LIGHT))
    for r in range(3):
        for c in range(9):
            slot(img, 7 + c * 18, top + r * 18)
    for c in range(9):
        slot(img, 7 + c * 18, top + 58)


def area(img, x0, y0, x1, y1, col, alpha=1.0, border=None):
    """A slightly darker rounded box (optionally tinted)."""
    d = ImageDraw.Draw(img)
    fill = mix(rgba(INNER), rgba(col), alpha) if col else rgba(INNER)
    d.rectangle((x0 + 1, y0, x1 - 1, y1), fill=fill)
    d.line((x0, y0 + 1, x0, y1 - 1), fill=fill)
    d.line((x1, y0 + 1, x1, y1 - 1), fill=fill)
    if border:
        b = rgba(border)
        d.line((x0 + 1, y0, x1 - 1, y0), fill=b)
        d.line((x0 + 1, y1, x1 - 1, y1), fill=shade(b, 0.6))


def arrow(img, x, y, col, w=12):
    """Right-pointing arrow, 7px tall, starting at (x, y) top-left."""
    d = ImageDraw.Draw(img)
    c = rgba(col)
    d.rectangle((x, y + 2, x + w - 5, y + 4), fill=c)
    for k in range(4):
        d.line((x + w - 5 + k, y + k, x + w - 5 + k, y + 6 - k), fill=c)
    d.line((x, y + 5, x + w - 5, y + 5), fill=shade(c, 0.55))


def plus(img, cx, cy, col):
    d = ImageDraw.Draw(img)
    c = rgba(col)
    d.rectangle((cx - 4, cy - 1, cx + 4, cy + 1), fill=c)
    d.rectangle((cx - 1, cy - 4, cx + 1, cy + 4), fill=c)


def leaf_glyph(img, x, y, col):
    """A tiny 7x7 cannabis leaf."""
    rows = ["0001000", "1001001", "0101010", "0011100", "1111111", "0001000", "0001000"]
    for yy, r in enumerate(rows):
        for xx, b in enumerate(r):
            if b == "1":
                img.putpixel((x + xx, y + yy), rgba(col))


def header(img, title, accent, logo="KUSHCRAFT", show_title=False):
    """Title strip: leaf + logo on the left (and the page name after it)."""
    d = ImageDraw.Draw(img)
    d.rectangle((3, 3, 172, 14), fill=rgba(INNER))
    d.line((3, 15, 172, 15), fill=rgba(DARK))
    if logo is None:
        return  # the menu shows its own (in-game) title here
    leaf_glyph(img, 6, 5, "6ad04a")
    x = text(img, 16, 5, logo, rgba(TEXT), shadow=rgba(DARK))
    if show_title and title:
        d.rectangle((x + 2, 7, x + 3, 8), fill=rgba("5a6a60"))
        text(img, x + 7, 5, title, rgba(accent), shadow=rgba(DARK))
    # accent pips on the right
    for k in range(3):
        d.rectangle((160 - k * 5, 8, 161 - k * 5, 9), fill=shade(rgba(accent), 1.0 - k * 0.25))


def tabs(img, count, active, accent, right_slot="wallet", page_name=""):
    """Row 0: icon tabs in slots 0..count-1, the active one lit and joined
    to the page; page name in big letters; a framed slot 8."""
    d = ImageDraw.Draw(img)
    a = rgba(accent)
    for i in range(count):
        x, y = slot_xy(0, i)
        if i == active:
            d.rectangle((x - 1, y - 2, x + 18, y + 20), fill=mix(rgba(BASE), a, 0.28))
            d.line((x - 1, y - 2, x + 18, y - 2), fill=a)
            d.line((x - 1, y - 2, x - 1, y + 19), fill=a)
            d.line((x + 18, y - 2, x + 18, y + 19), fill=a)
            slot(img, x, y)
            # a little lamp above the active tab
            d.rectangle((x + 7, y - 1, x + 10, y - 1), fill=shade(a, 1.3))
        else:
            d.rectangle((x - 1, y - 1, x + 18, y + 18), fill=rgba(INNER))
            slot(img, x, y)
    if page_name:
        sc = 2 if text_width(page_name, 2) <= 18 * (8 - count) - 6 else 1
        x0 = 7 + 18 * count + 4
        x1 = 7 + 18 * 8 - 3
        tw = text_width(page_name, sc)
        tx = x0 + (x1 - x0 - tw) // 2
        ty = 17 + (18 - 5 * sc) // 2
        text(img, tx, ty, page_name, a, shadow=rgba(DARK), scale=sc)
    if right_slot == "wallet":
        cslot(img, 0, 8, "big", "f2c23a")
    elif right_slot == "upgrade":
        cslot(img, 0, 8, "big", "f2c23a")
    return a


def content(img, rows, accent, first=1):
    """The page area under the tab bar; its top border is the accent colour."""
    d = ImageDraw.Draw(img)
    y0 = 17 + 18 * first - 2
    y1 = 17 + 18 * rows + 1
    d.rectangle((4, y0, 171, y1), fill=rgba(INNER))
    d.line((4, y0, 171, y0), fill=rgba(accent))
    d.line((4, y1, 171, y1), fill=rgba(LIGHT))


def joint_tab(img, active, accent):
    """Opens the content border under the active tab so it looks joined."""
    d = ImageDraw.Draw(img)
    x, y = slot_xy(0, active)
    d.line((x, 17 + 18 - 2, x + 17, 17 + 18 - 2), fill=mix(rgba(BASE), rgba(accent), 0.28))


# ---------------------------------------------------------------------------
# textures for the little scenes behind the pages
# ---------------------------------------------------------------------------
def wood(img, x0, y0, x1, y1, base="7a5232", seed=1, vertical=False):
    """Planks with grain, knots and dark seams every 6 px."""
    rng = random.Random(seed)
    b = rgba(base)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            u, v = (y - y0, x - x0) if vertical else (x - x0, y - y0)
            plank = v // 6
            grain = math.sin(u * 0.45 + plank * 2.1 + math.sin(u * 0.13 + plank) * 2.0)
            f = 1.0 + grain * 0.06 + rng.uniform(-0.04, 0.04) + (plank % 2) * 0.05
            c = shade(b, f)
            if v % 6 == 5:
                c = shade(b, 0.62)
            elif v % 6 == 0:
                c = shade(b, 1.15)
            img.putpixel((x, y), c)
    for _ in range(max(1, (x1 - x0) * (y1 - y0) // 900)):
        kx, ky = rng.randint(x0 + 2, x1 - 2), rng.randint(y0 + 1, y1 - 1)
        img.putpixel((kx, ky), shade(b, 0.55))
        if kx + 1 <= x1:
            img.putpixel((kx + 1, ky), shade(b, 0.7))


def shelf(img, x0, x1, y, base="8a6440"):
    """A wooden shelf board (3 px) with a shadow under it and two brackets."""
    d = ImageDraw.Draw(img)
    b = rgba(base)
    d.rectangle((x0, y, x1, y + 2), fill=b)
    d.line((x0, y, x1, y), fill=shade(b, 1.3))
    d.line((x0, y + 3, x1, y + 3), fill=shade(rgba(INNER), 0.6))
    for bx in (x0 + 6, x1 - 8):
        d.rectangle((bx, y + 3, bx + 2, y + 5), fill=shade(b, 0.6))


def velvet(img, x0, y0, x1, y1, base="5a1418", seed=3):
    """Deep red velvet with a soft diamond quilt pattern."""
    rng = random.Random(seed)
    b = rgba(base)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            q = (abs(((x - x0) + (y - y0)) % 12 - 6) + abs(((x - x0) - (y - y0)) % 12 - 6)) / 12.0
            f = 0.85 + q * 0.3 + rng.uniform(-0.03, 0.03)
            img.putpixel((x, y), shade(b, f))
    for y in range(y0 + 6, y1, 12):
        for x in range(x0 + 6, x1, 12):
            img.putpixel((x, y), rgba("c8a040"))


def gold_frame(img, x0, y0, x1, y1):
    d = ImageDraw.Draw(img)
    d.rectangle((x0, y0, x1, y1), outline=rgba("8a6a1a"))
    d.rectangle((x0 + 1, y0 + 1, x1 - 1, y1 - 1), outline=rgba("e8c050"))
    d.line((x0 + 1, y0 + 1, x1 - 1, y0 + 1), fill=rgba("fff0a0"))
    for (cx, cy) in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
        d.rectangle((cx - 1, cy - 1, cx + 1, cy + 1), fill=rgba("f8d870"))


def cork(img, x0, y0, x1, y1, seed=4):
    d = ImageDraw.Draw(img)
    rng = random.Random(seed)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            img.putpixel((x, y), shade(rgba("9a7046"), rng.uniform(0.82, 1.12)))
    d.rectangle((x0 - 2, y0 - 2, x1 + 2, y1 + 2), outline=rgba("5a3e24"))
    d.rectangle((x0 - 1, y0 - 1, x1 + 1, y1 + 1), outline=rgba("7a5634"))


def pin(img, x, y, col="e83a3a"):
    d = ImageDraw.Draw(img)
    d.rectangle((x, y, x + 1, y + 1), fill=rgba(col))
    img.putpixel((x, y), shade(rgba(col), 1.4))


def crate(img, x0, y0, x1, y1, seed=5):
    """A wooden crate face: planks with a darker frame and a cross brace."""
    wood(img, x0, y0, x1, y1, "8a6a3e", seed)
    d = ImageDraw.Draw(img)
    f = rgba("5e4424")
    d.rectangle((x0, y0, x1, y1), outline=f)
    d.rectangle((x0 + 1, y0 + 1, x1 - 1, y1 - 1), outline=shade(f, 1.3))
    for (cx, cy) in ((x0 + 2, y0 + 2), (x1 - 2, y0 + 2), (x0 + 2, y1 - 2), (x1 - 2, y1 - 2)):
        img.putpixel((cx, cy), rgba("c8c0b0"))


def stencil(img, x, y, s, col="2a1a0a"):
    text(img, x, y, s, rgba(col))


def coin_pile(img, cx, cy, n=4, seed=6):
    d = ImageDraw.Draw(img)
    rng = random.Random(seed)
    for k in range(n):
        x = cx + rng.randint(-5, 5)
        y = cy - k * 2 + rng.randint(-1, 1)
        d.ellipse((x - 3, y - 2, x + 3, y + 2), fill=rgba("f2c23a"), outline=rgba("a8801a"))
        img.putpixel((x - 1, y - 1), rgba("fff0a0"))


def cash_stack(img, x, y):
    d = ImageDraw.Draw(img)
    for k in range(3):
        d.rectangle((x, y - k * 2, x + 9, y + 3 - k * 2), fill=rgba("5aa84a"), outline=rgba("2a6a2a"))
        d.line((x + 4, y - k * 2, x + 4, y + 3 - k * 2), fill=rgba("d8e8c8"))


def label_plate(img, cx, y, s, col="2a1a0a", plate="e8dcb8"):
    """A small paper label with pixel text, centred on cx."""
    d = ImageDraw.Draw(img)
    w = text_width(s) + 4
    d.rectangle((cx - w // 2, y, cx - w // 2 + w, y + 7), fill=rgba(plate), outline=shade(rgba(plate), 0.6))
    text(img, cx - w // 2 + 3, y + 1, s, rgba(col))


LAYOUTS = {}

# ---------------------------------------------------------------------------
# /kush tab pages (6 rows). MUST match dev.kushcraft.gui.TabMenu.Tab order.
# ---------------------------------------------------------------------------
TABS = ["SHOP", "DRUGS", "TRADE", "CARTEL", "AWARDS"]
TITLES = {"SHOP": "SHOP", "DRUGS": "DRUGS", "TRADE": "TRADE", "CARTEL": "CARTEL", "AWARDS": "AWARDS",
          "TOP": "TOP DEALERS", "GEAR": "GEAR & WORKERS"}

# items per row of the Drugs page (Catalog: weed, psych, uppers, downers, gear)
DRUG_ROWS = [5, 8, 7, 8, 9]
DRUG_COLORS = ["5aa83a", "a05ad8", "3aa8d8", "d8803a", "8a8a96"]
SHOP_SEEDS = 36
GEAR_SLOTS = 18
HIRE_SLOTS = ((3, 1), (3, 3), (3, 5), (3, 7))
HIRE_COLORS = ("4caa32", "e8a832", "3ac8e8", "c85ad8")
# shelf buttons of the Trade page: row 1 and row 5, 9 each (TradeMenu.SHELVES)
TRADE_SHELVES = 18
TRADE_SLOTS = 27
AWARD_SLOTS = 45
DRY_RACKS = 5


GUIDE_SLOT = 7
EXTRA_SLOT = 5


def tab_page(name, seed, tab=None, extra=False):
    """name: the background (and accent); tab: which tab is lit (default: the same).
    Row 0: tabs 0-4, the page's own button 5 (extra=True), the admin button 6
    (admins only, no frame), the guide 7 and the wallet 8."""
    rows = 6
    tab = (tab or name).upper()
    idx = TABS.index(tab)
    accent = ACCENT[name]
    img = panel(rows, seed)
    header(img, TITLES[name.upper()], accent, show_title=True)
    content(img, rows, accent)
    tabs(img, len(TABS), idx, accent, "wallet")
    joint_tab(img, idx, accent)
    guide_slot(img)
    if extra:
        cslot(img, 0, EXTRA_SLOT, "big", accent)
    return img, accent


def guide_slot(img):
    """The guide button (0,7): a framed slot with a little light around it."""
    x, y = slot_xy(0, GUIDE_SLOT)
    d = ImageDraw.Draw(img)
    for (gx, gy) in ((x - 3, y + 8), (x + 20, y + 8), (x + 8, y - 2)):
        d.rectangle((gx, gy, gx + 1, gy + 1), fill=rgba("ffe680"))
    cslot(img, 0, GUIDE_SLOT, "glow", "5ad8f0")


def shop():
    """Seeds on four wooden shelves (rows 1-4); Gear & Workers (5,1), Sell all (5,4), market news (5,7)."""
    img, a = tab_page("shop", 20)
    x0, y0 = 5, slot_xy(1, 0)[1] - 1
    x1, y1 = 170, slot_xy(5, 0)[1] - 3
    wood(img, x0, y0, x1, y1, "4a3220", 20)
    for r in range(4):
        y = slot_xy(1 + r, 0)[1]
        shelf(img, x0, x1, y + 17)
        for c in range(9):
            cslot(img, 1 + r, c, tint="c8a050")
    # bottom row: a toolbox button, the big sell button with cash, the news stand
    cslot(img, 5, 1, "big", "5ad8e8")
    cslot(img, 5, 4, "glow", "6ae05a")
    sx, sy = slot_xy(5, 4)
    coin_pile(img, sx - 14, sy + 13, 4, 7)
    coin_pile(img, sx + 32, sy + 13, 3, 8)
    cslot(img, 5, 7, "big", "f2e6c8")
    player_inv(img, 6)
    return "shop", img, 6


def gear():
    """Shop > Gear & Workers: gear on two metal racks (rows 1-2), a hiring board with
    four workers (3,1) (3,3) (3,5) (3,7), back to seeds (5,0) and your workers (5,4). The Shop tab is lit."""
    img, a = tab_page("gear", 26, tab="shop")
    d = ImageDraw.Draw(img)
    x0, x1 = 5, 170
    for r in (1, 2):
        gy = slot_xy(r, 0)[1]
        d.rectangle((x0, gy - 2, x1, gy + 19), fill=rgba("2a3034"))
        for x in range(x0, x1 + 1, 4):
            img.putpixel((x, gy - 2), rgba("5a6a74"))
            img.putpixel((x + 2, gy + 19), rgba("1a2024"))
        d.line((x0, gy - 2, x1, gy - 2), fill=rgba("7a8a94"))
        for c in range(9):
            cslot(img, r, c, tint="8ab4c8")
    # the hiring board: cork with a sign and two posters
    by0, by1 = slot_xy(3, 0)[1] - 8, slot_xy(4, 0)[1] + 17
    cork(img, 8, by0, 167, by1, 61)
    label_plate(img, 88, by1 - 8, "HIRING", plate="f8e8a8")
    for (r, c), col in zip(HIRE_SLOTS, HIRE_COLORS):
        x, y = slot_xy(r, c)
        d.rectangle((x - 7, y - 3, x + 24, y + 26), fill=rgba("f4ecd4"), outline=rgba("8a7a5a"))
        d.rectangle((x - 7, y + 22, x + 24, y + 26), fill=rgba(col))
        pin(img, x + 8, y - 5)
        cslot(img, r, c, "big", col)
        # a wage line under the slot
        for k in range(5):
            d.point((x - 4 + k * 6, y + 20), fill=rgba("b8a880"))
    cslot(img, 5, 0, "big", "a8b0b8")
    cslot(img, 5, 4, "glow", "6ae05a")
    player_inv(img, 6)
    return "gear", img, 6


def drugs():
    """Rows 1-5: weed, psychedelics, uppers, downers, gear (DRUG_ROWS items each) in a glass cabinet."""
    img, a = tab_page("drugs", 21)
    d = ImageDraw.Draw(img)
    for r, (n, col) in enumerate(zip(DRUG_ROWS, DRUG_COLORS)):
        y = slot_xy(1 + r, 0)[1]
        # coloured glass shelf behind each row
        area(img, 5, y - 1, 7 + 18 * max(n, 1) + 1, y + 18, col, 0.18)
        d.line((5, y + 18, 7 + 18 * n + 1, y + 18), fill=shade(rgba(col), 0.9))
        row_marker(img, 1 + r, col)
        for c in range(n):
            cslot(img, 1 + r, c, tint=col)
        # glass reflections at the end of short rows
        if n < 9:
            gx = 7 + 18 * n + 6
            for k in range(3):
                d.line((gx + k * 6, y + 13, gx + k * 6 + 4, y + 3), fill=shade(rgba(INNER), 1.8))
    player_inv(img, 6)
    return "drugs", img, 6


def trade():
    """Shelf buttons on two market sign boards (row 1 and row 5, 9 each); 27 offers on crates (rows 2-4).
    Trade only sells, so there's no sell button."""
    img, a = tab_page("trade", 22)
    d = ImageDraw.Draw(img)
    for row in (1, 5):
        y = slot_xy(row, 0)[1]
        wood(img, 5, y - 1, 170, y + 18, "5a3e24", 22 + row)
        for c in range(TRADE_SHELVES // 2):
            cslot(img, row, c, tint="5ad8e8")
    # crates behind the goods
    for r in range(3):
        for blk in range(3):
            x0, y0 = slot_xy(2 + r, blk * 3)
            crate(img, x0 - 1, y0 - 1, x0 + 18 * 3, y0 + 18, 30 + r * 3 + blk)
        for c in range(9):
            cslot(img, 2 + r, c)
    player_inv(img, 6)
    return "trade", img, 6


def cartel():
    """Cartel room: banner (1,1), bank (1,3), level (1,5), members (1,7);
    how cartels work (3,1) on a note, the shipment (3,4) on a crate, Top Dealers (3,7)."""
    img, a = tab_page("cartel", 25)
    d = ImageDraw.Draw(img)
    top, bottom = slot_xy(1, 0)[1] - 1, slot_xy(5, 0)[1] + 18
    velvet(img, 5, top, 170, bottom, "4a1216", 25)
    # gold rail under the top row
    ry = slot_xy(1, 0)[1] + 21
    d.rectangle((5, ry, 170, ry + 1), fill=rgba("c8a040"))
    d.line((5, ry, 170, ry), fill=rgba("fff0a0"))
    # banner pole behind the banner slot
    bx, by = slot_xy(1, 1)
    d.rectangle((bx - 4, by - 1, bx - 3, by + 19), fill=rgba("8a6a3a"))
    img.putpixel((bx - 4, by - 2), rgba("f8d870"))
    img.putpixel((bx - 3, by - 2), rgba("f8d870"))
    cslot(img, 1, 1, "big", "e84a4a")
    # bank: a round vault door frame
    vx, vy = slot_xy(1, 3)
    d.ellipse((vx - 5, vy - 3, vx + 22, vy + 20), fill=rgba("3a3e44"), outline=rgba("8a949e"))
    for k in range(8):
        ang = k * math.pi / 4
        px_, py_ = vx + 8.5 + math.cos(ang) * 12, vy + 8.5 + math.sin(ang) * 10.5
        d.rectangle((px_ - 1, py_ - 1, px_, py_), fill=rgba("c8d0d8"))
    cslot(img, 1, 3, "big", "f2c23a")
    # level: a ladder of stars
    lx, ly = slot_xy(1, 5)
    ly += 28
    for k in range(5):
        sx = lx - 5 + k * 7
        d.polygon([(sx, ly - 3), (sx + 1, ly - 1), (sx + 3, ly - 1), (sx + 1, ly), (sx + 2, ly + 2),
                   (sx, ly + 1), (sx - 2, ly + 2), (sx - 1, ly), (sx - 3, ly - 1), (sx - 1, ly - 1)],
                  fill=rgba("f8d040" if k < 3 else "6a5a3a"))
    cslot(img, 1, 5, "big", "f2c23a")
    cslot(img, 1, 7, "big", "e8e0d0")
    # how it works: a pinned paper note with a question mark
    hx, hy = slot_xy(3, 1)
    d.rectangle((hx - 6, hy - 6, hx + 23, hy + 26), fill=rgba("f4ecd4"), outline=rgba("8a7a5a"))
    pin(img, hx + 8, hy - 7)
    text(img, hx + 6, hy + 21, "HOW?", rgba("6a4a2a"))
    cslot(img, 3, 1, "big", "5ad8f0")
    # shipment: a crate on a pallet, the slot set into its front
    sx, sy = slot_xy(3, 4)
    crate(img, sx - 14, sy - 9, sx + 31, sy + 21, 27)
    d.rectangle((sx - 16, sy + 22, sx + 33, sy + 24), fill=rgba("6a4a2a"))
    for k in range(4):
        d.rectangle((sx - 15 + k * 15, sy + 25, sx - 12 + k * 15, sy + 26), fill=rgba("4a3018"))
    stencil(img, sx - 11, sy - 7, "SHIP")
    cslot(img, 3, 4, "glow", "f2c23a")
    # Top Dealers: a crown plaque
    tx, ty = slot_xy(3, 7)
    gold_frame(img, tx - 8, ty - 6, tx + 25, ty + 23)
    d.rectangle((tx - 6, ty - 4, tx + 23, ty + 21), fill=rgba("2a0e10"))
    cslot(img, 3, 7, "big", "f2c23a")
    player_inv(img, 6)
    return "cartel", img, 6


def podium(img, x0, y0, x1, y1, col, num):
    d = ImageDraw.Draw(img)
    c = rgba(col)
    d.rectangle((x0, y0, x1, y1), fill=shade(c, 0.55))
    d.rectangle((x0, y0, x1, y0 + 2), fill=c)
    d.line((x0, y0, x1, y0), fill=shade(c, 1.3))
    d.line((x0, y0, x0, y1), fill=shade(c, 0.8))
    d.line((x1, y0, x1, y1), fill=shade(c, 0.35))
    tw = text_width(num, 2)
    text(img, (x0 + x1) // 2 - tw // 2 + 1, y0 + 6, num, shade(c, 1.25), shadow=shade(c, 0.3), scale=2)


def top():
    """Cartel > Top Dealers: podium #1 (1,4), #2 (2,2), #3 (2,6); #4-#10 (4,1..7);
    you (5,4); dealers/cartels switch (5,8). The Cartel tab is lit."""
    img, a = tab_page("top", 23, tab="cartel")
    d = ImageDraw.Draw(img)
    # spotlight rays from the top
    cx = slot_xy(1, 4)[0] + 9
    for y in range(slot_xy(1, 0)[1] - 1, 17 + 18 * 4 - 4):
        spread = (y - 30) * 0.9
        for x in range(int(cx - spread), int(cx + spread) + 1):
            if 5 <= x <= 170:
                img.putpixel((x, y), mix(img.getpixel((x, y)), rgba("f2c23a"), 0.07))
    x, y = slot_xy(1, 4)
    podium(img, x - 6, y + 21, x + 23, 17 + 18 * 4 - 4, "f2c23a", "1")
    x, y = slot_xy(2, 2)
    podium(img, x - 6, y + 21, x + 23, 17 + 18 * 4 - 4, "c8d0dc", "2")
    x, y = slot_xy(2, 6)
    podium(img, x - 6, y + 21, x + 23, 17 + 18 * 4 - 4, "d8884a", "3")
    cslot(img, 1, 4, "big", "f2c23a")
    cslot(img, 2, 2, "big", "c8d0dc")
    cslot(img, 2, 6, "big", "d8884a")
    # a shelf for #4-#10
    x0, y0 = slot_xy(4, 1)
    d.rectangle((x0 - 3, y0 + 19, x0 + 18 * 7 + 2, y0 + 20), fill=rgba("6a4a2a"))
    for c in range(1, 8):
        cslot(img, 4, c)
    cslot(img, 5, 4, "big", ACCENT["top"])
    cslot(img, 5, 8, "big", ACCENT["cartel"])
    player_inv(img, 6)
    return "top", img, 6


def awards():
    """Rows 1-5: 45 awards in a trophy cabinet."""
    img, a = tab_page("awards", 24, extra=True)
    d = ImageDraw.Draw(img)
    y0, y1 = slot_xy(1, 0)[1] - 1, slot_xy(5, 0)[1] + 18
    wood(img, 5, y0, 170, y1, "3a2418", 24)
    for i in range(AWARD_SLOTS):
        r, c = 1 + i // 9, i % 9
        cslot(img, r, c, tint="c08aff")
        x, y = slot_xy(r, c)
        g = rgba("c8a040")
        for (px_, py_) in ((x, y), (x + 17, y), (x, y + 17), (x + 17, y + 17)):
            d.point((px_, py_), fill=g)
    for r in range(1, 6):
        y = slot_xy(r, 0)[1]
        d.line((5, y + 18, 170, y + 18), fill=rgba("6a4a2a"))
    player_inv(img, 6)
    return "awards", img, 6


# ---------------------------------------------------------------------------
# Drug Lab pages (5 rows). MUST match dev.kushcraft.gui.LabTabMenu.Tab order.
# ---------------------------------------------------------------------------
LAB_TABS = ["COOK", "ROLL", "DRY", "MIX"]
# recipes per group, in LabRecipe order: weed, psychedelics, uppers, downers
COOK_GROUPS = [1, 8, 8, 10]
COOK_RECIPES = sum(COOK_GROUPS)


def lab_page(name, seed):
    rows = 5
    idx = LAB_TABS.index(name.upper())
    accent = ACCENT[name]
    img = panel(rows, seed)
    header(img, name.upper(), accent, logo="DRUG LAB")
    content(img, rows, accent)
    tabs(img, len(LAB_TABS), idx, accent, "upgrade", name.upper())
    joint_tab(img, idx, accent)
    return img, accent


def cook():
    """Recipes rows 1-3 (COOK_RECIPES), tinted by group like the Drugs page;
    progress tube (4,0..6); arrow (4,7); output (4,8)."""
    img, a = lab_page("cook", 30)
    i = 0
    for n, col in zip(COOK_GROUPS, DRUG_COLORS):
        for _ in range(n):
            cslot(img, 1 + i // 9, i % 9, tint=col)
            i += 1
    d = ImageDraw.Draw(img)
    x0, y0 = slot_xy(4, 0)
    # glass tube around the progress slots
    d.rectangle((x0 - 2, y0 - 2, x0 + 18 * 7 + 1, y0 + 19), outline=rgba("8ab4c8"))
    d.line((x0 - 1, y0 - 1, x0 + 18 * 7, y0 - 1), fill=rgba("d8f0f8"))
    for c in range(7):
        cslot(img, 4, c)
    x, y = slot_xy(4, 7)
    arrow(img, x + 2, y + 5, a, 14)
    cslot(img, 4, 8, "glow", a)
    player_inv(img, 5)
    return "cook", img, 5


def roll():
    """Bud (2,1), joint (2,3), blunt (2,5), roll all (2,7) on a rolling tray."""
    img, a = lab_page("roll", 31)
    d = ImageDraw.Draw(img)
    x0, y0 = slot_xy(1, 0)
    x1, y1 = slot_xy(3, 8)
    d.rounded_rectangle((x0 + 2, y0 + 4, x1 + 15, y1 + 14), radius=4, fill=rgba("7a4e2a"), outline=rgba("3e2614"))
    d.rounded_rectangle((x0 + 5, y0 + 7, x1 + 12, y1 + 11), radius=3, fill=rgba("4a8a3a"))
    rng = random.Random(9)
    for _ in range(60):
        px = rng.randint(x0 + 7, x1 + 10)
        py = rng.randint(y0 + 9, y1 + 9)
        img.putpixel((px, py), rgba(rng.choice(("5aa04a", "3e7a30", "6ab456"))))
    # papers lying on the tray
    d.rectangle((x0 + 10, y0 + 9, x0 + 28, y0 + 14), fill=rgba("f4f0e2"))
    d.rectangle((x1 - 12, y1 + 2, x1 + 8, y1 + 7), fill=rgba("f4f0e2"))
    cslot(img, 2, 1, "big", a)
    x, y = slot_xy(2, 2)
    arrow(img, x + 2, y + 5, "f4f0e2", 14)
    cslot(img, 2, 3)
    cslot(img, 2, 5)
    cslot(img, 2, 7, "big", a)
    player_inv(img, 5)
    return "roll", img, 5


def dry():
    """Drying room: five racks (2,2..6), each with a gauge under it (3,2..6)."""
    img, a = lab_page("dry", 32)
    d = ImageDraw.Draw(img)
    x0, y0 = slot_xy(1, 0)
    x1, y1 = slot_xy(3, 8)
    wood(img, 5, y0 - 1, 170, y1 + 18, "3e2a1a", 32, vertical=True)
    wood_c, wood_d = rgba("8a6440"), rgba("5a3e24")
    # the rail the racks hang from
    rail = y0 + 5
    d.rectangle((14, rail - 1, 161, rail + 1), fill=wood_c, outline=wood_d)
    for c in range(2, 7):
        x, y = slot_xy(2, c)
        # hook + string down to the rack
        d.line((x + 8, rail + 2, x + 8, y - 3), fill=rgba("c8c0b0"))
        d.line((x + 9, rail + 2, x + 9, y - 3), fill=rgba("8a8478"))
        d.rectangle((x + 6, y - 4, x + 11, y - 3), fill=rgba("c8c0b0"))
        cslot(img, 2, c, "big", "7ad04a")
        # gauge: a little glass tube
        gx, gy = slot_xy(3, c)
        d.rectangle((gx - 1, gy - 1, gx + 18, gy + 18), outline=rgba("8ab4c8"))
        cslot(img, 3, c)
    # buds hanging at the sides
    for k, x in enumerate((20, 30, 146, 156)):
        d.line((x, rail + 2, x, rail + 9), fill=rgba("c8c0b0"))
        d.ellipse((x - 3, rail + 9, x + 3, rail + 18), fill=rgba("6a9a3a" if k % 2 else "8a9a3a"), outline=rgba("2e4a1a"))
        img.putpixel((x - 1, rail + 12), rgba("e8822e"))
        img.putpixel((x + 1, rail + 15), rgba("f0f0e0"))
    # a timer sign
    label_plate(img, 88, slot_xy(4, 0)[1] + 4, "30 SEC")
    player_inv(img, 5)
    return "dry", img, 5


def mix_page():
    """Seed A (1,1) + seed B (1,3) -> result (1,7); chances (3,0..7) + mutation (3,8);
    discard (4,3), mix (4,4), keep (4,5)."""
    img, a = lab_page("mix", 33)
    cslot(img, 1, 1, "big", "7ad04a")
    x, y = slot_xy(1, 2)
    plus(img, x + 9, y + 9, TEXT)
    cslot(img, 1, 3, "big", "7ad04a")
    for c in (4, 5):
        x, y = slot_xy(1, c)
        arrow(img, x + 2, y + 5, a, 16)
    x, y = slot_xy(1, 6)
    arrow(img, x + 2, y + 5, a, 14)
    cslot(img, 1, 7, "glow", a)
    d = ImageDraw.Draw(img)
    x0, y0 = slot_xy(3, 0)
    area(img, 5, y0 - 1, 170, y0 + 18, a, 0.12)
    for c in range(8):
        cslot(img, 3, c)
    cslot(img, 3, 8, "big", "b45af0")
    cslot(img, 4, 3, "big", "e84a4a")
    cslot(img, 4, 4, "glow", a)
    cslot(img, 4, 5, "big", "6ae05a")
    player_inv(img, 5)
    return "mix", img, 5


# ---------------------------------------------------------------------------
# other menus
# ---------------------------------------------------------------------------
def recipe():
    """Recipe viewer: station (0,8); 3x3 grid rows 1-3 cols 1-3; result (2,6);
    back (4,0), prev (4,3), next (4,5)."""
    rows = 5
    a = ACCENT["recipe"]
    img = panel(rows, 40)
    header(img, "RECIPE", a, logo=None)
    content(img, rows, a, first=0)
    cslot(img, 0, 8, "big", a)
    d = ImageDraw.Draw(img)
    x0, y0 = slot_xy(1, 1)
    d.rectangle((x0 - 3, y0 - 3, x0 + 18 * 3 + 2, y0 + 18 * 3 + 2), fill=rgba("2a2016"), outline=rgba("6a5034"))
    for r in (1, 2, 3):
        for c in (1, 2, 3):
            cslot(img, r, c)
    x, y = slot_xy(2, 4)
    arrow(img, x + 4, y + 5, a, 26)
    cslot(img, 2, 6, "glow", a)
    cslot(img, 4, 0)
    cslot(img, 4, 3)
    cslot(img, 4, 5)
    player_inv(img, rows)
    return "recipe", img, rows


def list_menu():
    """Generic paged list (admin items): header (0,4); rows 1-4; back (5,0),
    prev (5,3), page (5,4), next (5,5), action (5,8)."""
    rows = 6
    a = ACCENT["list"]
    img = panel(rows, 41)
    header(img, "ITEMS", a, logo=None)
    content(img, rows, a)
    cslot(img, 0, 4, "big", a)
    for r in range(1, 5):
        for c in range(9):
            cslot(img, r, c)
    for c in (0, 3, 4, 5, 8):
        cslot(img, 5, c)
    player_inv(img, rows)
    return "list", img, rows


def plain_page(name, rows, seed, title):
    a = ACCENT[name]
    img = panel(rows, seed)
    header(img, title, a, show_title=True)
    content(img, rows, a, first=0)
    return img, a


def worker():
    """A worker's menu (5 rows): portrait (0,0), their job (0,4), rename / pause / train /
    dismiss (0,5..8); the satchel (rows 1-3); take all (4,4)."""
    rows = 5
    img, a = plain_page("worker", rows, 50, "WORKER")
    d = ImageDraw.Draw(img)
    cslot(img, 0, 0, "big", "f2c23a")
    x, y = slot_xy(0, 1)
    text(img, x + 4, y + 6, "YOUR HIRE", rgba(a), shadow=rgba(DARK))
    cslot(img, 0, 4, "glow", "5ad8f0")
    for c, col in zip((5, 6, 7, 8), ("e8e0d0", "f0a03a", "f2c23a", "e84a4a")):
        cslot(img, 0, c, "big", col)
    # the satchel: a burlap sack behind rows 1-3
    sx0, sy0 = 4, slot_xy(1, 0)[1] - 3
    sx1, sy1 = 171, slot_xy(3, 0)[1] + 20
    rng = random.Random(51)
    for yy in range(sy0, sy1 + 1):
        for xx in range(sx0, sx1 + 1):
            weave = (xx % 3 == 0) != (yy % 3 == 0)
            col = shade(rgba("8a6a40"), (0.9 if weave else 1.04) + rng.uniform(-0.05, 0.05))
            img.putpixel((xx, yy), col)
    d.rectangle((sx0, sy0, sx1, sy1), outline=rgba("4a3418"))
    # stitches along the top and a drawstring
    for xx in range(sx0 + 3, sx1 - 2, 5):
        d.line((xx, sy0 + 1, xx + 2, sy0 + 1), fill=rgba("e8d8a8"))
    for r in (1, 2, 3):
        for c in range(9):
            cslot(img, r, c, tint="b8945a")
    cslot(img, 4, 4, "glow", "6ae05a")
    player_inv(img, rows)
    return "worker", img, rows


GUIDE_STEPS = 7


def guide():
    """Getting started (5 rows): summary (1,4); seven steps along a path (2,1..7);
    back (4,0), handbook (4,3), tips (4,5)."""
    rows = 5
    img, a = plain_page("guide", rows, 52, "GETTING STARTED")
    d = ImageDraw.Draw(img)
    # a garden path winding under the steps
    y = slot_xy(2, 0)[1] + 9
    for xx in range(10, 166):
        wob = int(2 * math.sin(xx * 0.12))
        d.line((xx, y + wob - 3, xx, y + wob + 3), fill=rgba("6a5434"))
        if xx % 5 == 0:
            img.putpixel((xx, y + wob - 2), rgba("8a7450"))
    # grass tufts along the path
    rng = random.Random(53)
    for _ in range(40):
        gx, gy = rng.randint(8, 168), rng.choice((y - 7, y + 7)) + rng.randint(-1, 1)
        d.line((gx, gy, gx, gy - 2), fill=rgba("4caa32"))
    cslot(img, 1, 4, "big", a)
    for k in range(GUIDE_STEPS):
        cslot(img, 2, 1 + k, "big" if k == 0 else "normal", "f2c23a")
        x, yy = slot_xy(2, 1 + k)
        text(img, x + 7, yy + 20, str(k + 1), rgba(TEXT), shadow=rgba(DARK))
    # finish flag
    fx, fy = slot_xy(2, 8)
    d.line((fx + 6, fy + 1, fx + 6, fy + 17), fill=rgba("c8c0b0"))
    d.polygon([(fx + 7, fy + 1), (fx + 15, fy + 4), (fx + 7, fy + 7)], fill=rgba("e84a4a"))
    cslot(img, 4, 0)
    cslot(img, 4, 3, "big", "6ad04a")
    cslot(img, 4, 5, "big", "e8e0d0")
    player_inv(img, rows)
    return "guide", img, rows


ADMIN_ROWS = (("PLAYERS", "f2c23a", 5), ("AROUND YOU", "6ad04a", 4), ("MARKET", "f0a03a", 5), ("SERVER", "5ad8f0", 4))


def admin():
    """Admin panel (6 rows): one topic per row (1-4), its label icon in column 0 and
    its buttons from column 1; back (5,0). Admin player pages use it too (head at (0,4))."""
    rows = 6
    img, a = plain_page("admin", rows, 54, "ADMIN")
    d = ImageDraw.Draw(img)
    # red warning stripes along the top of the page
    for xx in range(4, 172):
        if (xx // 4) % 2 == 0:
            d.point((xx, 16), fill=rgba("c8302a"))
    cslot(img, 0, 4, "big", a)
    for r, (label, col, n) in enumerate(ADMIN_ROWS, start=1):
        y = slot_xy(r, 0)[1]
        area(img, 5, y - 1, 170, y + 18, col, 0.12)
        cslot(img, r, 0, "big", col)
        for c in range(1, 9):
            cslot(img, r, c, tint=col if c <= n else None)
    cslot(img, 5, 0)
    player_inv(img, rows)
    return "admin", img, rows


def generate(g):
    global G
    G = g
    out = []
    for fn in (shop, drugs, trade, cartel, top, awards, cook, roll, dry, mix_page, recipe, list_menu, gear, worker,
               guide, admin):
        name, img, rows = fn()
        G.save_png(img, f"gui/{name}")
        LAYOUTS[name] = (img.width, img.height, rows)
        out.append(img)
    # preview: six to a row
    per = 6
    rows_ = [out[i:i + per] for i in range(0, len(out), per)]
    W = max(sum(i.width for i in r) for r in rows_) + 10 * (per + 1)
    H = sum(max(i.height for i in r) for r in rows_) + 10 * (len(rows_) + 1)
    sheet = Image.new("RGBA", (W, H), (30, 30, 30, 255))
    y = 10
    for r in rows_:
        x = 10
        for im in r:
            sheet.alpha_composite(im, (x, y))
            x += im.width + 10
        y += max(i.height for i in r) + 10
    sheet = sheet.resize((W * 2, H * 2), Image.NEAREST)
    path = os.path.join(G.PREVIEW_DIR, "gui_preview.png")
    G.ensure(path)
    sheet.save(path)
