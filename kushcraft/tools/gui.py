"""Full-window GUI backgrounds.  Each one is drawn as a font glyph in the
inventory title (shifted left with negative-space characters), so chest menus
get a completely custom look.  Slot coordinates follow the vanilla chest
layout: container slot (row, col) has its frame at x=7+18*col, y=17+18*row.

The layouts here MUST match dev.kushcraft.gui.* in the Java code.
"""
import math
import os
import random

from PIL import Image, ImageDraw

G = None

# 3x5 pixel font for labels baked into the backgrounds
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
    "+": ["000", "010", "111", "010", "000"], "-": ["000", "000", "111", "000", "000"],
    ">": ["100", "010", "001", "010", "100"], "$": ["011", "110", "010", "011", "110"],
    " ": ["000", "000", "000", "000", "000"], "/": ["001", "001", "010", "100", "100"],
    "!": ["010", "010", "010", "000", "010"], "(": ["010", "100", "100", "100", "010"],
    ")": ["010", "001", "001", "001", "010"], "=": ["000", "111", "000", "111", "000"],
    "6": ["011", "100", "111", "101", "111"], "7": ["111", "001", "010", "010", "010"],
    "8": ["111", "101", "111", "101", "111"], "9": ["111", "101", "111", "001", "110"],
    "%": ["101", "001", "010", "100", "101"], ",": ["000", "000", "000", "010", "100"],
    "'": ["010", "010", "000", "000", "000"], "*": ["000", "101", "010", "101", "000"],
    ":": ["000", "010", "000", "010", "000"], ".": ["000", "000", "000", "000", "010"], "?": ["110", "001", "010", "000", "010"],
}


def text(img, x, y, s, col, shadow=None):
    for ch in s.upper():
        glyph = FONT.get(ch, FONT["?"])
        for gy, row in enumerate(glyph):
            for gx, b in enumerate(row):
                if b == "1":
                    if shadow:
                        img.putpixel((x + gx + 1, y + gy + 1), shadow)
                    img.putpixel((x + gx, y + gy), col)
        x += 4
    return x


def text_width(s):
    return len(s) * 4 - 1


THEMES = {
    "lab": dict(base="34474c", light="5c787e", dark="1a2427", slot="1e2b2e", accent="5ae8c8"),
    "strain": dict(base="3a2f4c", light="62527e", dark="1c1626", slot="221a30", accent="e85ad0"),
    "roller": dict(base="5a3c24", light="8a6240", dark="2e1e10", slot="2e2014", accent="9ae85a"),
    "dealer": dict(base="2f4a2a", light="56804c", dark="162414", slot="182a16", accent="f2d24a"),
    "main": dict(base="25402f", light="4a7a58", dark="10200f", slot="142518", accent="8ae85a"),
    "list": dict(base="2e3a42", light="52646e", dark="151c20", slot="1a2328", accent="7ad0e8"),
    "dry": dict(base="6a4a2c", light="9a7048", dark="34220f", slot="3a2814", accent="f0c850"),
    "recipe": dict(base="4a3a2a", light="7a6448", dark="241a10", slot="2a2016", accent="f0b860"),
    "exchange": dict(base="2a3e4a", light="4e6e80", dark="121e24", slot="16242c", accent="5ae0f0"),
    "jobs": dict(base="4a3a24", light="7a6440", dark="241a0e", slot="2a2012", accent="f8d050"),
}


def panel(rows, theme, rng):
    th = {k: G.rgba(v) for k, v in theme.items()}
    W, H = 176, 114 + rows * 18
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # body with a subtle diagonal texture
    for y in range(H):
        for x in range(W):
            f = 1 + 0.035 * math.sin((x + y) * 0.45) + rng.uniform(-0.02, 0.02)
            img.putpixel((x, y), G.shade(th["base"], f))
    # rounded corners + bevel border (vanilla style, 1px black outline)
    outline = (0, 0, 0, 255)
    for (x, y) in ((0, 0), (1, 0), (0, 1), (W - 1, 0), (W - 2, 0), (W - 1, 1), (0, H - 1), (1, H - 1), (0, H - 2),
                   (W - 1, H - 1), (W - 2, H - 1), (W - 1, H - 2)):
        img.putpixel((x, y), (0, 0, 0, 0))
    d.line((2, 0, W - 3, 0), fill=outline)
    d.line((2, H - 1, W - 3, H - 1), fill=outline)
    d.line((0, 2, 0, H - 3), fill=outline)
    d.line((W - 1, 2, W - 1, H - 3), fill=outline)
    for p in ((1, 1), (W - 2, 1), (1, H - 2), (W - 2, H - 2)):
        img.putpixel(p, outline)
    d.line((2, 1, W - 3, 1), fill=th["light"])
    d.line((1, 2, 1, H - 3), fill=th["light"])
    d.line((2, 2, W - 4, 2), fill=th["light"])
    d.line((2, 2, 2, H - 4), fill=th["light"])
    d.line((3, H - 2, W - 2, H - 2), fill=th["dark"])
    d.line((W - 2, 3, W - 2, H - 2), fill=th["dark"])
    d.line((3, H - 3, W - 3, H - 3), fill=th["dark"])
    d.line((W - 3, 3, W - 3, H - 3), fill=th["dark"])
    # title strip
    for x in range(4, W - 4):
        for y in range(4, 15):
            img.putpixel((x, y), G.shade(img.getpixel((x, y)), 0.78))
    d.line((4, 15, W - 5, 15), fill=th["accent"])
    return img, th


def slot(img, x, y, th, style="normal"):
    """x,y: top-left of the 18x18 frame."""
    d = ImageDraw.Draw(img)
    inner = th["slot"]
    if style == "big":
        d.rectangle((x - 2, y - 2, x + 19, y + 19), outline=th["accent"])
        d.rectangle((x - 1, y - 1, x + 18, y + 18), outline=G.shade(th["accent"], 0.5))
    d.rectangle((x, y, x + 17, y + 17), fill=inner)
    d.line((x, y, x + 16, y), fill=G.shade(inner, 0.55))
    d.line((x, y, x, y + 16), fill=G.shade(inner, 0.55))
    d.line((x + 1, y + 17, x + 17, y + 17), fill=G.shade(th["light"], 1.25))
    d.line((x + 17, y + 1, x + 17, y + 17), fill=G.shade(th["light"], 1.25))


def cslot(img, row, col, th, style="normal"):
    slot(img, 7 + col * 18, 17 + row * 18, th, style)


def player_inv(img, rows, th):
    top = rows * 18 + 30
    text(img, 8, rows * 18 + 21, "", th["light"])
    for r in range(3):
        for c in range(9):
            slot(img, 7 + c * 18, top + r * 18, th)
    for c in range(9):
        slot(img, 7 + c * 18, top + 58, th)


def frame(img, x0, y0, x1, y1, col, fill=None):
    d = ImageDraw.Draw(img)
    if fill is not None:
        d.rectangle((x0, y0, x1, y1), fill=fill)
    d.rectangle((x0, y0, x1, y1), outline=col)


def paste_icon(img, name, x, y, scale=1, folder="item"):
    folder = "item" if folder == "item" else "item/" + folder
    path = os.path.join(G.ASSETS, "textures", folder, name + ".png")
    ic = Image.open(path).convert("RGBA")
    tint_path = os.path.join(G.ASSETS, "textures", folder, name + "_tint.png")
    if os.path.exists(tint_path):
        ic = G.composite([ic, Image.open(tint_path).convert("RGBA")], 0x7ad04a)
    over = os.path.join(G.ASSETS, "textures", folder, name + "_overlay.png")
    if os.path.exists(over):
        ic.alpha_composite(Image.open(over).convert("RGBA"))
    if scale != 1:
        ic = ic.resize((16 * scale, 16 * scale), Image.NEAREST)
    img.alpha_composite(ic, (x, y))


def label(img, x, y, s, th):
    text(img, x, y, s, G.shade(th["accent"], 1.0), shadow=th["dark"])


# ---------------------------------------------------------------------------
LAYOUTS = {}


def lab():
    """Drug Lab - Cook tab. Recipes rows 1-2 cols 1-7, progress row 3,
    back (4,0), output (4,4), info (4,8), status (0,8)."""
    rows = 5
    rng = random.Random(1)
    img, th = panel(rows, THEMES["lab"], rng)
    frame(img, 7 + 18 - 3, 17 + 18 - 3, 7 + 18 * 8 + 2, 17 + 18 * 3 + 2, th["accent"], G.shade(th["base"], 0.7))
    for r in (1, 2):
        for c in range(1, 8):
            cslot(img, r, c, th)
    label(img, 25, 17 + 3, "RECIPES", th)
    y = 17 + 3 * 18
    frame(img, 7 + 18 - 3, y - 2, 7 + 18 * 8 + 2, y + 19, G.rgba("d8f4ff"), G.shade(th["base"], 0.6))
    for c in range(1, 8):
        cslot(img, 3, c, th)
    cslot(img, 4, 4, th, "big")
    label(img, 7 + 18 * 5 + 4, 17 + 4 * 18 + 6, "OUTPUT", th)
    cslot(img, 0, 8, th)
    cslot(img, 4, 0, th)
    cslot(img, 4, 8, th)
    player_inv(img, rows, th)
    return "lab", img, rows


def strain():
    rows = 6
    rng = random.Random(2)
    img, th = panel(rows, THEMES["strain"], rng)
    frame(img, 7 + 18 - 3, 17 + 18 - 3, 7 + 18 * 8 + 2, 17 + 36 + 2, th["accent"], G.shade(th["base"], 0.7))
    cslot(img, 1, 1, th)
    cslot(img, 1, 3, th)
    cslot(img, 1, 7, th, "big")
    paste_icon(img, "ui_plus", 7 + 18 * 2 + 1, 17 + 18 + 1, folder="icon")
    paste_icon(img, "ui_dna", 7 + 18 * 4 + 1, 17 + 18 + 1, folder="icon")
    paste_icon(img, "ui_arrow", 7 + 18 * 5 + 1, 17 + 18 + 1, folder="icon")
    paste_icon(img, "ui_arrow", 7 + 18 * 6 + 1, 17 + 18 + 1, folder="icon")
    label(img, 9, 17 + 3, "SEED A + SEED B", th)
    label(img, 7 + 18 * 7 - 6, 17 + 3, "STRAIN", th)
    y = 17 + 3 * 18
    frame(img, 4, y - 3, 7 + 18 * 6 + 2, y + 38, th["accent"], G.shade(th["base"], 0.7))
    label(img, 8, y - 10, "EFFECTS (PICK 3)", th)
    for r in (3, 4):
        for c in range(6):
            cslot(img, r, c, th)
    frame(img, 7 + 18 * 7 - 3, y - 3, 7 + 18 * 9 + 2, y + 38, th["accent"], G.shade(th["base"], 0.7))
    label(img, 7 + 18 * 7, y - 10, "STYLE", th)
    for r in (3, 4):
        for c in (7, 8):
            cslot(img, r, c, th)
    cslot(img, 5, 4, th, "big")
    label(img, 7 + 18 * 5 + 4, 17 + 5 * 18 + 6, "CREATE", th)
    cslot(img, 5, 0, th)
    player_inv(img, rows, th)
    return "strain", img, rows


def roller():
    rows = 3
    rng = random.Random(3)
    img, th = panel(rows, THEMES["roller"], rng)
    y = 17 + 18
    frame(img, 7 + 18 - 4, y - 4, 7 + 18 * 8 + 3, y + 21, G.rgba("1a3a14"), G.rgba("2f7a3a"))
    for x in range(7 + 18 - 3, 7 + 18 * 8 + 3):
        for yy in range(y - 3, y + 21):
            if (x + yy) % 7 == 0:
                img.putpixel((x, yy), G.rgba("3c8f48"))
    cslot(img, 1, 1, th, "big")
    cslot(img, 1, 3, th)
    cslot(img, 1, 5, th)
    cslot(img, 1, 7, th)
    paste_icon(img, "ui_arrow", 7 + 18 * 2 + 1, y + 1, folder="icon")
    label(img, 9, 17 + 3, "BUD", th)
    label(img, 7 + 18 * 3 - 2, 17 + 3, "JOINT", th)
    label(img, 7 + 18 * 5 - 2, 17 + 3, "BLUNT", th)
    label(img, 7 + 18 * 7 - 6, 17 + 3, "ROLL ALL", th)
    cslot(img, 2, 0, th)
    cslot(img, 2, 8, th)
    player_inv(img, rows, th)
    return "roller", img, rows


def dealer():
    """Market. Top: info (0,0), orders (0,2), wallet (0,4), hot item (0,6),
    sell all (0,8). Rows 1-4 buy. Row 5: back, prev, page, next."""
    rows = 6
    rng = random.Random(4)
    img, th = panel(rows, THEMES["dealer"], rng)
    for c in (0, 2, 6, 8):
        cslot(img, 0, c, th)
    cslot(img, 0, 4, th, "big")
    frame(img, 4, 17 + 18 - 2, 171, 17 + 18 * 5 + 1, th["accent"], G.shade(th["base"], 0.7))
    for r in range(1, 5):
        for c in range(9):
            cslot(img, r, c, th)
    for c in (0, 3, 4, 5):
        cslot(img, 5, c, th)
    label(img, 29, 17 + 5 * 18 + 4, "BUY", th)
    label(img, 29, 17 + 5 * 18 + 10, "ABOVE", th)
    label(img, 7 + 18 * 6 + 2, 17 + 5 * 18 + 4, "CLICK YOUR", th)
    label(img, 7 + 18 * 6 + 2, 17 + 5 * 18 + 10, "ITEMS = SELL", th)
    player_inv(img, rows, th)
    return "dealer", img, rows


def centered_label(img, row, col, text_, th):
    cx = 7 + 18 * col + 9
    label(img, cx - text_width(text_) // 2, 17 + row * 18 + 6, text_, th)


SECTIONS = {
    # row: (band colour, [(col, line 1, line 2), ...])  -  MUST match MainMenu.java
    1: ("3a8a4a", [(0, "GUIDE", "HOW TO"), (3, "RECIPES", "CRAFTING"), (6, "CATALOG", "ALL DRUGS")]),
    2: ("b08a2a", [(0, "MARKET", "BUY+SELL"), (3, "EXCHANGE", "ORES+MORE"), (6, "ORDERS", "BONUS $")]),
    3: ("b05a2a", [(0, "JOBS", "PAID WORK"), (3, "SEND $", "TO PLAYER"), (6, "TOP 10", "RICHEST")]),
    4: ("4a5ab0", [(0, "STRAINS", "MIX+NAME"), (3, "STATUS", "MY HIGH"), (6, "PACK", "TEXTURES")]),
}


def main_menu():
    """/kush. Info (0,0), wallet (0,4), admin (0,8); four colour-coded rows of
    three buttons at cols 0/3/6 with their name baked in next to them;
    close (5,0) and the hotkey hint."""
    rows = 6
    rng = random.Random(5)
    img, th = panel(rows, THEMES["main"], rng)
    cslot(img, 0, 0, th)
    cslot(img, 0, 4, th, "big")
    label(img, 7 + 18 * 5 + 4, 17 + 6, "BALANCE", th)
    for r, (band, buttons) in SECTIONS.items():
        bc = G.rgba(band)
        y0 = 17 + 18 * r
        for y in range(y0, y0 + 18):
            for x in range(5, 171):
                img.putpixel((x, y), G.mix(img.getpixel((x, y)), bc, 0.28))
        d = ImageDraw.Draw(img)
        d.line((5, y0, 170, y0), fill=G.shade(bc, 0.55))
        for x in range(2, 5):
            for y in range(y0 + 1, y0 + 17):
                img.putpixel((x, y), G.shade(bc, 1.1))
        for c, l1, l2 in buttons:
            cslot(img, r, c, th, "normal")
            x = 7 + 18 * (c + 1) + 1
            text(img, x, y0 + 3, l1, G.shade(bc, 1.9), shadow=G.shade(bc, 0.35))
            text(img, x, y0 + 10, l2, G.rgba("e8e8e0"), shadow=G.shade(bc, 0.35))
    cslot(img, 5, 0, th)
    label(img, 7 + 18 + 4, 17 + 5 * 18 + 7, "SHIFT+F OR /KUSH = THIS MENU", th)
    player_inv(img, rows, th)
    return "main", img, rows


def list_menu():
    """Generic paged list. Header (0,4); rows 1-4 content; row 5:
    back (5,0), prev (5,3), page (5,4), next (5,5), action (5,8)."""
    rows = 6
    rng = random.Random(6)
    img, th = panel(rows, THEMES["list"], rng)
    cslot(img, 0, 4, th, "big")
    frame(img, 4, 17 + 18 - 2, 171, 17 + 18 * 5 + 1, th["accent"], G.shade(th["base"], 0.7))
    for r in range(1, 5):
        for c in range(9):
            cslot(img, r, c, th)
    for c in (0, 3, 4, 5, 8):
        cslot(img, 5, c, th)
    player_inv(img, rows, th)
    return "list", img, rows


def hub():
    """Drug Lab hub: Cook (1,1), Roll (1,3), Dry (1,5), Mix (1,7);
    back (2,0), info (2,8)."""
    rows = 3
    rng = random.Random(7)
    img, th = panel(rows, THEMES["lab"], rng)
    for i, (c, name) in enumerate(((1, "COOK"), (3, "ROLL"), (5, "DRY"), (7, "MIX"))):
        cslot(img, 1, c, th, "big")
        centered_label(img, 2, c, name, th)
    cslot(img, 2, 0, th)
    cslot(img, 2, 8, th)
    label(img, 9, 17 + 3, "WHAT DO YOU WANT TO MAKE?", th)
    player_inv(img, rows, th)
    return "hub", img, rows


def dry():
    """Drying shelf: input (1,1), progress (1,3..5), output (1,7); back (2,0), info (2,8)."""
    rows = 3
    rng = random.Random(8)
    img, th = panel(rows, THEMES["dry"], rng)
    y = 17 + 18
    frame(img, 7 + 18 - 4, y - 4, 7 + 18 * 8 + 3, y + 21, th["dark"], G.shade(th["base"], 0.75))
    for x in range(7 + 18 - 3, 7 + 18 * 8 + 3, 6):
        for yy in range(y - 3, y + 21):
            img.putpixel((x, yy), G.shade(th["base"], 0.6))
    cslot(img, 1, 1, th, "big")
    for c in (3, 4, 5):
        cslot(img, 1, c, th)
    cslot(img, 1, 7, th, "big")
    paste_icon(img, "ui_arrow", 7 + 18 * 2 + 1, y + 1, folder="icon")
    paste_icon(img, "ui_arrow", 7 + 18 * 6 + 1, y + 1, folder="icon")
    label(img, 7 + 18 * 1 - 2, 17 + 3, "FRESH", th)
    label(img, 7 + 18 * 4 - 4, 17 + 3, "DRYING", th)
    label(img, 7 + 18 * 7 - 2, 17 + 3, "DRIED", th)
    cslot(img, 2, 0, th)
    cslot(img, 2, 8, th)
    player_inv(img, rows, th)
    return "dry", img, rows


def orders():
    """Daily orders: (1,2), (1,4), (1,6); back (2,0), info (2,8)."""
    rows = 3
    rng = random.Random(9)
    img, th = panel(rows, THEMES["dealer"], rng)
    label(img, 9, 17 + 4, "CLICK AN ORDER TO HAND IT IN", th)
    for c in (2, 4, 6):
        cslot(img, 1, c, th, "big")
    cslot(img, 2, 0, th)
    cslot(img, 2, 8, th)
    player_inv(img, rows, th)
    return "orders", img, rows


def recipe():
    """Recipe viewer: station (0,8); 3x3 grid rows 1-3 cols 1-3; result (2,6);
    back (4,0), prev/page/next (4,3-5), info (4,8)."""
    rows = 5
    rng = random.Random(10)
    img, th = panel(rows, THEMES["recipe"], rng)
    label(img, 7 + 18 + 1, 17 + 6, "INGREDIENTS", th)
    label(img, 7 + 18 * 6 + 2, 17 + 6, "MADE AT", th)
    cslot(img, 0, 8, th)
    for r in (1, 2, 3):
        for c in (1, 2, 3):
            cslot(img, r, c, th)
    y = 17 + 18 * 2 + 1
    paste_icon(img, "ui_arrow", 7 + 18 * 4 + 2, y, folder="icon")
    paste_icon(img, "ui_arrow", 7 + 18 * 5 - 2, y, folder="icon")
    cslot(img, 2, 6, th, "big")
    centered_label(img, 3, 6, "RESULT", th)
    cslot(img, 4, 0, th)
    for c in (3, 4, 5):
        cslot(img, 4, c, th)
    cslot(img, 4, 8, th)
    player_inv(img, rows, th)
    return "recipe", img, rows


def exchange():
    """Resource exchange: info (0,0), category tabs (0,1..7), wallet (0,8);
    rows 1-4 items; back (5,0), prev/page/next (5,3-5)."""
    rows = 6
    rng = random.Random(11)
    img, th = panel(rows, THEMES["exchange"], rng)
    cslot(img, 0, 0, th)
    for c in range(1, 8):
        cslot(img, 0, c, th)
    cslot(img, 0, 8, th)
    frame(img, 4, 17 + 18 - 1, 171, 17 + 18 * 5 + 1, th["accent"], G.shade(th["base"], 0.7))
    for r in range(1, 5):
        for c in range(9):
            cslot(img, r, c, th)
    for c in (0, 3, 4, 5):
        cslot(img, 5, c, th)
    label(img, 29, 17 + 5 * 18 + 4, "CLICK", th)
    label(img, 29, 17 + 5 * 18 + 10, "= BUY", th)
    label(img, 7 + 18 * 6 + 2, 17 + 5 * 18 + 4, "CLICK YOUR", th)
    label(img, 7 + 18 * 6 + 2, 17 + 5 * 18 + 10, "ITEMS = SELL", th)
    player_inv(img, rows, th)
    return "exchange", img, rows


JOBS = [(0, "MINER"), (2, "FARMER"), (4, "WOODCUT"), (6, "HUNTER"), (8, "GROWER")]


def jobs():
    """Jobs: miner (1,0), farmer (1,2), woodcutter (1,4), hunter (1,6),
    grower (1,8) with names under them; back (3,0), earnings (3,4), info (3,8)."""
    rows = 4
    rng = random.Random(12)
    img, th = panel(rows, THEMES["jobs"], rng)
    label(img, 9, 17 + 6, "YOU GET PAID FOR YOUR WORK!", th)
    for c, name in JOBS:
        cslot(img, 1, c, th, "big")
        centered_label(img, 2, c, name, th)
    cslot(img, 3, 0, th)
    cslot(img, 3, 4, th, "big")
    cslot(img, 3, 8, th)
    player_inv(img, rows, th)
    return "jobs", img, rows


def generate(g):
    global G
    G = g
    out = []
    for fn in (lab, strain, roller, dealer, main_menu, list_menu, hub, dry, orders, recipe, exchange, jobs):
        name, img, rows = fn()
        G.save_png(img, f"gui/{name}")
        LAYOUTS[name] = (img.width, img.height, rows)
        out.append(img)
    # preview
    W = sum(i.width for i in out) + 10 * (len(out) + 1)
    H = max(i.height for i in out) + 20
    sheet = Image.new("RGBA", (W, H), (30, 30, 30, 255))
    x = 10
    for i in out:
        sheet.alpha_composite(i, (x, 10))
        x += i.width + 10
    sheet = sheet.resize((W * 2, H * 2), Image.NEAREST)
    path = os.path.join(G.PREVIEW_DIR, "gui_preview.png")
    G.ensure(path)
    sheet.save(path)
