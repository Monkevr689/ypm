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
    rows = 5
    rng = random.Random(1)
    img, th = panel(rows, THEMES["lab"], rng)
    # recipe shelf row 1
    frame(img, 7 + 18 - 3, 17 + 18 - 3, 7 + 18 * 8 + 2, 17 + 36 + 2, th["accent"], G.shade(th["base"], 0.7))
    for c in range(1, 8):
        cslot(img, 1, c, th)
    label(img, 25, 17 + 3, "RECIPES", th)
    # glass tube progress row 3
    y = 17 + 3 * 18
    frame(img, 7 + 18 - 3, y - 3, 7 + 18 * 8 + 2, y + 20, G.rgba("d8f4ff"), G.shade(th["base"], 0.6))
    for c in range(1, 8):
        cslot(img, 3, c, th)
    label(img, 25, y - 10, "PROGRESS", th)
    # output + status
    cslot(img, 4, 4, th, "big")
    label(img, 7 + 18 * 5 + 4, 17 + 4 * 18 + 6, "OUTPUT", th)
    cslot(img, 0, 8, th)
    cslot(img, 4, 0, th)
    paste_icon(img, "lab_solvent", 7 + 18 * 1 + 1, 17 + 4 * 18 + 1)
    paste_icon(img, "catalyst", 7 + 18 * 2 + 1, 17 + 4 * 18 + 1)
    player_inv(img, rows, th)
    return "lab", img, rows


def strain():
    rows = 6
    rng = random.Random(2)
    img, th = panel(rows, THEMES["strain"], rng)
    # parents + result
    frame(img, 7 + 18 - 3, 17 + 18 - 3, 7 + 18 * 8 + 2, 17 + 36 + 2, th["accent"], G.shade(th["base"], 0.7))
    cslot(img, 1, 1, th)
    cslot(img, 1, 3, th)
    cslot(img, 1, 7, th, "big")
    paste_icon(img, "ui_plus", 7 + 18 * 2 + 1, 17 + 18 + 1, folder="icon")
    paste_icon(img, "ui_dna", 7 + 18 * 4 + 1, 17 + 18 + 1, folder="icon")
    paste_icon(img, "ui_arrow", 7 + 18 * 5 + 1, 17 + 18 + 1, folder="icon")
    paste_icon(img, "ui_arrow", 7 + 18 * 6 + 1, 17 + 18 + 1, folder="icon")
    label(img, 9, 17 + 3, "PARENT A + PARENT B", th)
    label(img, 7 + 18 * 7 - 6, 17 + 3, "STRAIN", th)
    # effects grid rows 3-4, cols 0-5
    y = 17 + 3 * 18
    frame(img, 4, y - 3, 7 + 18 * 6 + 2, y + 38, th["accent"], G.shade(th["base"], 0.7))
    label(img, 8, y - 10, "EFFECTS (PICK 3)", th)
    for r in (3, 4):
        for c in range(6):
            cslot(img, r, c, th)
    # settings 2x2
    frame(img, 7 + 18 * 7 - 3, y - 3, 7 + 18 * 9 + 2, y + 38, th["accent"], G.shade(th["base"], 0.7))
    label(img, 7 + 18 * 7, y - 10, "STYLE", th)
    for r in (3, 4):
        for c in (7, 8):
            cslot(img, r, c, th)
    cslot(img, 5, 4, th, "big")
    label(img, 7 + 18 * 5 + 4, 17 + 5 * 18 + 6, "CREATE", th)
    player_inv(img, rows, th)
    return "strain", img, rows


def roller():
    rows = 3
    rng = random.Random(3)
    img, th = panel(rows, THEMES["roller"], rng)
    y = 17 + 18
    # green rolling mat
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
    cslot(img, 2, 8, th)
    player_inv(img, rows, th)
    return "roller", img, rows


def dealer():
    rows = 6
    rng = random.Random(4)
    img, th = panel(rows, THEMES["dealer"], rng)
    cslot(img, 0, 0, th)
    cslot(img, 0, 4, th, "big")
    cslot(img, 0, 8, th)
    frame(img, 4, 17 + 18 - 2, 171, 17 + 18 * 5 + 1, th["accent"], G.shade(th["base"], 0.7))
    for r in range(1, 5):
        for c in range(9):
            cslot(img, r, c, th)
    cslot(img, 5, 3, th)
    cslot(img, 5, 4, th)
    cslot(img, 5, 5, th)
    label(img, 9, 17 + 5 * 18 + 6, "BUY ABOVE", th)
    label(img, 7 + 18 * 6 + 2, 17 + 5 * 18 + 6, "CLICK YOUR", th)
    label(img, 7 + 18 * 6 + 2, 17 + 5 * 18 + 12, "ITEMS = SELL", th)
    player_inv(img, rows, th)
    return "dealer", img, rows


def generate(g):
    global G
    G = g
    out = []
    for fn in (lab, strain, roller, dealer):
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
