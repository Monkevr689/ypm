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
    "tabs": dict(base="263a30", light="4a6a58", dark="0e1a14", slot="142219", accent="8ae85a"),
    "mixer": dict(base="3a2f4c", light="62527e", dark="1c1626", slot="221a30", accent="e85ad0"),
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
    """Drug Lab - Cook tab. Recipes rows 1-2 (all 9 cols), progress row 3 cols 1-7,
    back (4,0), output (4,4), info (4,8), status (0,8)."""
    rows = 5
    rng = random.Random(1)
    img, th = panel(rows, THEMES["lab"], rng)
    frame(img, 4, 17 + 18 - 3, 171, 17 + 18 * 3 + 2, th["accent"], G.shade(th["base"], 0.7))
    for r in (1, 2):
        for c in range(9):
            cslot(img, r, c, th)
    label(img, 9, 17 + 6, "RECIPES (RANKS UNLOCK MORE)", th)
    y = 17 + 3 * 18
    frame(img, 7 + 18 - 3, y - 2 + 3, 7 + 18 * 8 + 2, y + 19, G.rgba("d8f4ff"), G.shade(th["base"], 0.6))
    for c in range(1, 8):
        cslot(img, 3, c, th)
    cslot(img, 4, 4, th, "big")
    label(img, 7 + 18 * 5 + 4, 17 + 4 * 18 + 6, "OUTPUT", th)
    cslot(img, 0, 8, th)
    cslot(img, 4, 0, th)
    cslot(img, 4, 8, th)
    player_inv(img, rows, th)
    return "lab", img, rows


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



def centered_label(img, row, col, text_, th):
    cx = 7 + 18 * col + 9
    label(img, cx - text_width(text_) // 2, 17 + row * 18 + 6, text_, th)





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






# ---------------------------------------------------------------------------
# tab pages: the same tab bar on top of every main page
# MUST match dev.kushcraft.gui.TabMenu (tab order) and each page's slots
# ---------------------------------------------------------------------------
TABS = ["HOME", "DRUGS", "SHOP", "BREED", "JOBS", "TRADE", "BANK", "BOOK", "CASH"]
TAB_COLORS = ["8ae85a", "ff7ac0", "f8d050", "c08aff", "f0a040", "5ae0f0", "f8e070", "d8c8a0", "f8d050"]


def tab_page(active, rng_seed):
    """6-row panel with the tab bar; returns (img, th). Tab names are drawn in
    the title strip, staggered on two lines so they don't run together."""
    rows = 6
    rng = random.Random(rng_seed)
    theme = dict(THEMES["tabs"])
    theme["accent"] = TAB_COLORS[active]
    img, th = panel(rows, theme, rng)
    d = ImageDraw.Draw(img)
    # tab bar band (title strip + tab row), a bit darker than the page
    for y in range(3, 36):
        for x in range(3, 173):
            img.putpixel((x, y), G.shade(img.getpixel((x, y)), 0.8))
    d.line((4, 15, 171, 15), fill=G.shade(th["base"], 0.62))  # no title underline on tab pages
    # the active tab is raised and opens into the page
    x0 = 7 + 18 * active - 1
    hi = G.shade(th["base"], 1.3)
    d.rectangle((x0, 3, x0 + 19, 36), fill=hi)
    d.line((x0, 3, x0 + 19, 3), fill=th["accent"])
    d.line((x0, 3, x0, 35), fill=th["accent"])
    d.line((x0 + 19, 3, x0 + 19, 35), fill=th["accent"])
    d.line((3, 36, x0, 36), fill=th["accent"])
    d.line((x0 + 19, 36, 172, 36), fill=th["accent"])
    for c, name in enumerate(TABS):
        cx = 7 + 18 * c + 9
        y = 4 if c % 2 == 0 else 10
        col = G.rgba(TAB_COLORS[c]) if c == active else G.shade(G.rgba(TAB_COLORS[c]), 0.7)
        text(img, cx - text_width(name) // 2, y, name, col, shadow=th["dark"])
        cslot(img, 0, c, th, "big" if c == active else "normal")
    return img, th


def page_grid(img, th, first_row=1, last_row=4):
    d = ImageDraw.Draw(img)
    d.rectangle((5, 17 + 18 * first_row, 170, 17 + 18 * (last_row + 1) - 1), fill=G.shade(th["base"], 0.72))
    for r in range(first_row, last_row + 1):
        for c in range(9):
            cslot(img, r, c, th)


def home():
    """Home: rank (1,1), status (1,3), hot (1,5), next (1,7); orders (4,0/2/4/6/8);
    close (5,0), pack (5,4), admin (5,8)."""
    img, th = tab_page(0, 20)
    for c, name in ((1, "RANK"), (3, "HIGH"), (5, "HOT"), (7, "NEXT")):
        cslot(img, 1, c, th, "big")
        centered_label(img, 2, c, name, th)
    label(img, 9, 17 + 3 * 18 + 6, "DAILY ORDERS - CLICK ONE TO HAND IN", th)
    for c in (0, 2, 4, 6, 8):
        cslot(img, 4, c, th, "big")
    for c in (0, 4, 8):
        cslot(img, 5, c, th)
    player_inv(img, 6, th)
    return "home", img, 6


def drugs():
    """Drugs: items rows 1-4; filters (5,0..6), prev (5,7), next (5,8)."""
    img, th = tab_page(1, 21)
    page_grid(img, th)
    for c in range(9):
        cslot(img, 5, c, th)
    player_inv(img, 6, th)
    return "drugs", img, 6


def shop():
    """Shop: buy rows 1-4; prev (5,0), next (5,1), hot (5,7), sell all (5,8)."""
    img, th = tab_page(2, 22)
    page_grid(img, th)
    for c in (0, 1, 7):
        cslot(img, 5, c, th)
    cslot(img, 5, 8, th, "big")
    label(img, 7 + 18 * 2 + 3, 17 + 5 * 18 + 3, "CLICK YOUR ITEMS", th)
    label(img, 7 + 18 * 2 + 3, 17 + 5 * 18 + 10, "BELOW TO SELL >", th)
    player_inv(img, 6, th)
    return "shop", img, 6


def breed():
    """Breed: strains rows 1-4; prev (5,0), next (5,1), filter (5,3), mix (5,8)."""
    img, th = tab_page(3, 23)
    page_grid(img, th)
    for c in (0, 1, 3):
        cslot(img, 5, c, th)
    cslot(img, 5, 8, th, "big")
    label(img, 7 + 18 * 4 + 3, 17 + 5 * 18 + 3, "MIX TWO SEEDS", th)
    label(img, 7 + 18 * 4 + 3, 17 + 5 * 18 + 10, "FOR A NEW ONE >", th)
    player_inv(img, 6, th)
    return "breed", img, 6


JOB_NAMES = [(0, "MINER"), (2, "FARMER"), (4, "WOODCUT"), (6, "HUNTER"), (8, "GROWER")]


def jobs():
    """Jobs: jobs (1,0/2/4/6/8) with names; pay table rows 3-4;
    prev (5,0), next (5,1), earned (5,4), info (5,8)."""
    img, th = tab_page(4, 24)
    for c, name in JOB_NAMES:
        cslot(img, 1, c, th, "big")
        centered_label(img, 2, c, name, th)
    page_grid(img, th, 3, 4)
    for c in (0, 1, 8):
        cslot(img, 5, c, th)
    cslot(img, 5, 4, th, "big")
    player_inv(img, 6, th)
    return "jobs", img, 6


def trade():
    """Trade: items rows 1-4; prev (5,0), categories (5,1..7), next (5,8)."""
    img, th = tab_page(5, 25)
    page_grid(img, th)
    for c in range(9):
        cslot(img, 5, c, th)
    player_inv(img, 6, th)
    return "trade", img, 6


def bank():
    """Bank: balance (1,1), send (1,4), ranks (1,7); top dealers rows 4-5."""
    img, th = tab_page(6, 26)
    for c, name in ((1, "BALANCE"), (4, "SEND $"), (7, "RANKS")):
        cslot(img, 1, c, th, "big")
        centered_label(img, 2, c, name, th)
    label(img, 9, 17 + 3 * 18 + 6, "TOP DEALERS - MOST PRODUCT SOLD", th)
    page_grid(img, th, 4, 5)
    player_inv(img, 6, th)
    return "bank", img, 6


def mixer():
    """Strain mixer: seed A (1,1), seed B (1,3), result (1,7); chances row 3;
    back (4,0), discard (4,3), mix (4,4), keep (4,5), info (4,8)."""
    rows = 5
    rng = random.Random(2)
    img, th = panel(rows, THEMES["mixer"], rng)
    frame(img, 7 + 18 - 3, 17 + 18 - 3, 7 + 18 * 8 + 2, 17 + 36 + 2, th["accent"], G.shade(th["base"], 0.7))
    cslot(img, 1, 1, th, "big")
    cslot(img, 1, 3, th, "big")
    cslot(img, 1, 7, th, "big")
    paste_icon(img, "ui_plus", 7 + 18 * 2 + 1, 17 + 18 + 1, folder="icon")
    paste_icon(img, "ui_arrow", 7 + 18 * 4 + 5, 17 + 18 + 1, folder="icon")
    paste_icon(img, "ui_arrow", 7 + 18 * 5 + 3, 17 + 18 + 1, folder="icon")
    centered_label(img, 2, 1, "SEED A", th)
    centered_label(img, 2, 3, "SEED B", th)
    centered_label(img, 2, 7, "RESULT", th)
    centered_label(img, 2, 5, "CHANCES", th)
    for c in range(9):
        cslot(img, 3, c, th)
    for c in (0, 3, 5, 8):
        cslot(img, 4, c, th)
    cslot(img, 4, 4, th, "big")
    player_inv(img, rows, th)
    return "mixer", img, rows


def generate(g):
    global G
    G = g
    out = []
    for fn in (lab, mixer, roller, list_menu, hub, dry, recipe, home, drugs, shop, breed, jobs, trade, bank):
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
