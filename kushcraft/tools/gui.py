"""Full-window GUI backgrounds. Each one is drawn as a font glyph in the
inventory title (shifted left with negative-space characters), so chest
menus get a completely custom look. Slot coordinates follow the vanilla
chest layout: container slot (row, col) has its frame at x=7+18*col,
y=17+18*row.

2.0 style: one dark panel, icon tabs along the top (the active one lit up
and joined to the page), the page name in big letters, and slot frames
only where something goes.

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
    "shop": "f2c23a", "drugs": "7ae05a", "trade": "5ad8e8", "top": "f09a3a", "awards": "c08aff",
    "cook": "5ae8c8", "roll": "9ae85a", "dry": "f0c850", "mix": "e85ad0", "recipe": "f0b860", "list": "7ad0e8",
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


def header(img, title, accent, logo="KUSHCRAFT"):
    """Title strip: leaf + logo on the left."""
    d = ImageDraw.Draw(img)
    d.rectangle((3, 3, 172, 14), fill=rgba(INNER))
    d.line((3, 15, 172, 15), fill=rgba(DARK))
    leaf_glyph(img, 6, 5, "6ad04a")
    text(img, 16, 5, logo, rgba(TEXT), shadow=rgba(DARK))
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


LAYOUTS = {}

# ---------------------------------------------------------------------------
# /kush tab pages (6 rows). MUST match dev.kushcraft.gui.TabMenu.Tab order.
# ---------------------------------------------------------------------------
TABS = ["SHOP", "DRUGS", "TRADE", "TOP", "AWARDS"]
TITLES = {"SHOP": "SHOP", "DRUGS": "DRUGS", "TRADE": "TRADE", "TOP": "TOP", "AWARDS": "AWARDS"}

# items per row of the Drugs page (Catalog: weed, psych, uppers, downers, gear)
DRUG_ROWS = [9, 6, 6, 4, 9]
DRUG_COLORS = ["5aa83a", "a05ad8", "3aa8d8", "d8803a", "8a8a96"]
TRADE_COLORS = ["8a9ab0", "5ab49a", "a8906a", "c8703a"]
SHOP_SLOTS = 27
TRADE_SLOTS = 36
AWARD_SLOTS = 36


def tab_page(name, seed):
    rows = 6
    idx = TABS.index(name.upper())
    accent = ACCENT[name]
    img = panel(rows, seed)
    header(img, TITLES[name.upper()], accent)
    content(img, rows, accent)
    tabs(img, len(TABS), idx, accent, "wallet", TITLES[name.upper()])
    joint_tab(img, idx, accent)
    return img, accent


def shop():
    """Buy rows 1-3; orders (4,2) (4,4) (4,6) on a cork board; Sell all (5,4)."""
    img, a = tab_page("shop", 20)
    for i in range(SHOP_SLOTS):
        cslot(img, 1 + i // 9, i % 9)
    # cork board behind the orders
    d = ImageDraw.Draw(img)
    rng = random.Random(4)
    y0, y1 = 17 + 18 * 4 - 1, 17 + 18 * 5 - 0
    for y in range(y0, y1):
        for x in range(36, 140):
            v = rng.uniform(0.85, 1.1)
            img.putpixel((x, y), shade(rgba("8a6440"), v))
    d.rectangle((35, y0 - 1, 140, y1), outline=rgba("5a3e24"))
    for c in (2, 4, 6):
        cslot(img, 4, c, "big", "f2e6c8")
        x, y = slot_xy(4, c)
        d.rectangle((x + 8, y - 3, x + 9, y - 2), fill=rgba("e83a3a"))
    # sell all: a big green button with coins either side
    cslot(img, 5, 4, "glow", "6ae05a")
    for (cx, cy) in ((63, 116), (113, 116), (57, 118), (119, 118)):
        d.ellipse((cx - 3, cy - 3, cx + 3, cy + 3), fill=rgba("f2c23a"), outline=rgba("a8801a"))
    player_inv(img, 6)
    return "shop", img, 6


def drugs():
    """Rows 1-5: weed, psychedelics, uppers, downers, gear (DRUG_ROWS items each)."""
    img, a = tab_page("drugs", 21)
    for r, (n, col) in enumerate(zip(DRUG_ROWS, DRUG_COLORS)):
        row_marker(img, 1 + r, col)
        for c in range(n):
            cslot(img, 1 + r, c, tint=col)
    player_inv(img, 6)
    return "drugs", img, 6


def trade():
    """Rows 1-4: 36 offers (ores, lab supplies, blocks, food); Sell all (5,4)."""
    img, a = tab_page("trade", 22)
    for r in range(4):
        row_marker(img, 1 + r, TRADE_COLORS[r])
        for c in range(9):
            cslot(img, 1 + r, c, tint=TRADE_COLORS[r])
    cslot(img, 5, 4, "glow", "6ae05a")
    player_inv(img, 6)
    return "trade", img, 6


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
    """Podium: #1 (1,4), #2 (2,2), #3 (2,6); #4-#10 (4,1..7); you (5,4)."""
    img, a = tab_page("top", 23)
    # podium blocks under the top three
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
    d = ImageDraw.Draw(img)
    x0, y0 = slot_xy(4, 1)
    d.rectangle((x0 - 3, y0 + 19, x0 + 18 * 7 + 2, y0 + 20), fill=rgba("6a4a2a"))
    for c in range(1, 8):
        cslot(img, 4, c)
    cslot(img, 5, 4, "big", ACCENT["top"])
    player_inv(img, 6)
    return "top", img, 6


def awards():
    """Rows 1-4: 36 awards; summary (5,4)."""
    img, a = tab_page("awards", 24)
    d = ImageDraw.Draw(img)
    for i in range(AWARD_SLOTS):
        r, c = 1 + i // 9, i % 9
        cslot(img, r, c)
        x, y = slot_xy(r, c)
        g = rgba("8a7a4a")
        for (px, py) in ((x, y), (x + 17, y), (x, y + 17), (x + 17, y + 17)):
            d.point((px, py), fill=g)
    cslot(img, 5, 4, "big", ACCENT["awards"])
    # ribbons either side of the summary
    x, y = slot_xy(5, 4)
    for side in (-1, 1):
        x0 = x - 30 if side < 0 else x + 22
        d.polygon([(x0, y + 5), (x0 + 26, y + 5), (x0 + 26, y + 12), (x0, y + 12),
                   (x0 + (4 if side < 0 else 22), y + 8)], fill=rgba("8a5ad8"))
    player_inv(img, 6)
    return "awards", img, 6


# ---------------------------------------------------------------------------
# Drug Lab pages (5 rows). MUST match dev.kushcraft.gui.LabTabMenu.Tab order.
# ---------------------------------------------------------------------------
LAB_TABS = ["COOK", "ROLL", "DRY", "MIX"]
COOK_RECIPES = 20


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
    """Recipes rows 1-3 (COOK_RECIPES); progress tube (4,0..6); arrow (4,7); output (4,8)."""
    img, a = lab_page("cook", 30)
    for i in range(COOK_RECIPES):
        cslot(img, 1 + i // 9, i % 9)
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
    """Fresh (2,1), drying (2,3..5), dried (2,7) on a drying rack."""
    img, a = lab_page("dry", 32)
    d = ImageDraw.Draw(img)
    x0, y0 = slot_xy(1, 0)
    x1, y1 = slot_xy(3, 8)
    wood, wood_d = rgba("8a6440"), rgba("5a3e24")
    d.rectangle((x0 + 2, y0 + 2, x0 + 5, y1 + 16), fill=wood, outline=wood_d)
    d.rectangle((x1 + 12, y0 + 2, x1 + 15, y1 + 16), fill=wood, outline=wood_d)
    d.line((x0 + 5, y0 + 6, x1 + 12, y0 + 6), fill=rgba("c8c0b0"))
    # hanging buds along the line
    for k, x in enumerate(range(x0 + 14, x1 + 6, 16)):
        d.line((x, y0 + 6, x, y0 + 9), fill=rgba("c8c0b0"))
        d.ellipse((x - 3, y0 + 9, x + 3, y0 + 17), fill=rgba("6a9a3a" if k % 2 else "8a9a3a"), outline=rgba("2e4a1a"))
        img.putpixel((x - 1, y0 + 12), rgba("e8822e"))
    cslot(img, 2, 1, "big", "7ad04a")
    x, y = slot_xy(2, 2)
    arrow(img, x + 2, y + 5, a, 14)
    for c in (3, 4, 5):
        cslot(img, 2, c)
    x, y = slot_xy(2, 6)
    arrow(img, x + 2, y + 5, a, 14)
    cslot(img, 2, 7, "glow", a)
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
    header(img, "RECIPE", a)
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
    header(img, "ITEMS", a)
    content(img, rows, a)
    cslot(img, 0, 4, "big", a)
    for r in range(1, 5):
        for c in range(9):
            cslot(img, r, c)
    for c in (0, 3, 4, 5, 8):
        cslot(img, 5, c)
    player_inv(img, rows)
    return "list", img, rows


def generate(g):
    global G
    G = g
    out = []
    for fn in (shop, drugs, trade, top, awards, cook, roll, dry, mix_page, recipe, list_menu):
        name, img, rows = fn()
        G.save_png(img, f"gui/{name}")
        LAYOUTS[name] = (img.width, img.height, rows)
        out.append(img)
    # preview
    W = sum(i.width for i in out[:6]) + 10 * 7
    H = max(i.height for i in out) * 2 + 30
    sheet = Image.new("RGBA", (W, H), (30, 30, 30, 255))
    x, y = 10, 10
    for i, im in enumerate(out):
        if i == 6:
            x, y = 10, 10 + max(o.height for o in out[:6]) + 10
        sheet.alpha_composite(im, (x, y))
        x += im.width + 10
    sheet = sheet.resize((W * 2, H * 2), Image.NEAREST)
    path = os.path.join(G.PREVIEW_DIR, "gui_preview.png")
    G.ensure(path)
    sheet.save(path)
