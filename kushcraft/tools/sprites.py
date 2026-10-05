"""Hand-drawn 16x16 pixel art for KushCraft items and GUI icons.

Each sprite is a list of 16 strings (16 chars each). '.' is transparent.
Palette values are 'RRGGBB' or 'RRGGBBAA'. A palette value may be a tuple
(color, layer): layer 0 is the plain base, layer 1 is tinted in-game with the
strain colour (draw it in grays), layer 2 is drawn on top untinted.
"""

T = 1  # tinted layer
O = 2  # overlay layer

ITEMS = {}
ICONS = {}


def item(name, art, pal, base=None):
    ITEMS[name] = dict(art=art, pal=pal, base=base)


def icon(name, art, pal):
    ICONS[name] = dict(art=art, pal=pal)


# --------------------------------------------------------------------------
# Cannabis
# --------------------------------------------------------------------------
item("seed_pack", [
    "................",
    "...KKKKKKKKKK...",
    "..KQQQQQQQQQQK..",
    "..KpQQQQQQQQqK..",
    "..KppQQQQQQpqK..",
    "..KpppKKKKppqK..",
    "..KppppwppppqK..",
    "..KppwpwpwppqK..",
    "..KpwpwwwpwpqK..",
    "..KppwwwwwppqK..",
    "..KpppwwwpppqK..",
    "..KppppvppppqK..",
    "..KppppppppSsK..",
    "..KqqqqqqqSSsK..",
    "...KKKKKKKKKK...",
    "................",
], {
    "K": "3d2b1f", "Q": "c9b48a", "p": "efe3c2", "q": "d8c79f",
    "w": ("ececec", T), "v": ("a0a0a0", T),
    "S": "9a7040", "s": "5a3e22",
})

item("bud_fresh", [
    "................",
    ".......KK.......",
    "......KbbK......",
    "......KbcbK.....",
    ".....KcbobbK....",
    "..gGKbbcbbcK....",
    "...gKbobbbbbK...",
    "....KbcbbwcbK...",
    "....KbbcbbbobKg.",
    "....KobbbcbbbKGg",
    ".....KbcbbobK...",
    ".....KbbcbbbK...",
    "......KbbcbK....",
    ".......KssK.....",
    "........s.......",
    "................",
], {
    "K": "1f3315", "b": ("dadada", T), "c": ("a2a2a2", T),
    "o": ("e8822e", O), "w": ("f4fff0", O),
    "g": "62a832", "G": "3d7a20", "s": "6a8a3a",
})

item("bud_dried", [
    "................",
    "................",
    ".......KK.......",
    "......KbcK......",
    ".....KbcobK.....",
    ".....KcbwbcK....",
    "....KbobcbbK....",
    "....KcbbwbobK...",
    "....KbcobcbbK...",
    "....KbwbbcwcK...",
    ".....KbcobbK....",
    ".....KcbbwcK....",
    "......KbcbK.....",
    ".......KsK......",
    "................",
    "................",
], {
    "K": "2a2414", "b": ("bdbdbd", T), "c": ("848484", T),
    "o": ("c0702c", O), "w": ("ffffff", O), "s": "6b5a32",
})

JOINT_ART = [
    "................",
    "................",
    "............AE..",
    "...........KWeE.",
    "..........KWWWK.",
    ".........KWWwK..",
    "........KWWwK...",
    ".......KWWwK....",
    "......KWWwK.....",
    ".....KWWwK......",
    "....KWWwK.......",
    "...KFFwK........",
    "..KFFfK.........",
    "..KfFK..........",
    "...KK...........",
    "................",
]
item("joint", JOINT_ART, {
    "K": "5e5e5e", "W": "f6f6f0", "w": "cfcfc4", "F": "d0aa70", "f": "a07c48",
    "A": "8a8a8a", "E": "ff7a1a", "e": "ffb848",
})
item("blunt", JOINT_ART, {
    "K": "2e1c0e", "W": "93623a", "w": "6e4524", "F": "6e4524", "f": "4e3018",
    "A": "9a9a9a", "E": "ff6a10", "e": "ffb040",
})

PAPERS_ART = [
    "................",
    "................",
    "....wwwwwwww....",
    "...KWWWWWWWWK...",
    "...KHHHHHHHHK...",
    "...KGGGLGGGGK...",
    "...KGLGLGLGGK...",
    "...KLGLLLGLGK...",
    "...KGLLLLLGGK...",
    "...KGGLLLGGGK...",
    "...KGGGlGGGGK...",
    "...KGGGGGGGGK...",
    "...KDDDDDDDDK...",
    "...KKKKKKKKKK...",
    "................",
    "................",
]
item("rolling_papers", PAPERS_ART, {
    "w": "ffffff", "W": "e8e8e8", "K": "1c3a14", "H": "5cbf40", "G": "3a8f2a",
    "L": "f2d24a", "l": "c8a830", "D": "2a6a1e",
})
item("blunt_wrap", PAPERS_ART, {
    "w": "c08850", "W": "a06a38", "K": "2e1a0c", "H": "c8783a", "G": "8a4a22",
    "L": "f0c878", "l": "b89048", "D": "6a3818",
})

item("bong", [
    "......KKKK......",
    "......KgGK......",
    "......KgGK......",
    "......KgGK......",
    "......KgGK......",
    "......KgGK..KK..",
    "......KgGK.KmmK.",
    ".....KggGGK.Kn..",
    "....KgggGGGKs...",
    "...KggggGGsGK...",
    "...KgggggsGGK...",
    "..KgWWWWWWWWGK..",
    "..KgWwWWWWWWGK..",
    "..KgWWWWWwWWGK..",
    "...KWWWWWWWWK...",
    "....KKKKKKKK....",
], {
    "K": "1e3a3a", "g": "c0f4e8d0", "G": "7cc8b8e0", "W": "3a8ad8e8", "w": "8ac8ffe8",
    "m": "9a9aa8", "n": "6a6a78", "s": "5a6a6a",
})

item("hash", [
    "................",
    "................",
    "................",
    "................",
    "..KKKKKKKKKKKK..",
    "..KtttttttttTK..",
    "..KthhhhhhhhdK..",
    "..KthhhSShhhdK..",
    "..KthhSSSShhdK..",
    "..KthhhSShhhdK..",
    "..KthhhhhhhhdK..",
    "..KTdddddddddK..",
    "..KKKKKKKKKKKK..",
    "................",
    "................",
    "................",
], {
    "K": "1e140a", "t": "8e6436", "T": "6a4826", "h": "70492a", "d": "4a3018",
    "S": "3a2410",
})

item("moon_rock", [
    "................",
    "................",
    "................",
    "......KKKK......",
    "....KKkbkkKK....",
    "...KkjkbbkjkK...",
    "...KkbbkjkbkK...",
    "..KkjbkkjkkjkK..",
    "..KkbbkjkkbbkK..",
    "..KjkkkbbkjkkK..",
    "..KkbjkkkkbkjK..",
    "...KkkbbjkkkK...",
    "...KjkkkkbkjK...",
    "....KKkjkkKK....",
    "......KKKK......",
    "................",
], {
    "K": "4a3e1a", "k": "ecdfa4", "j": "cdb66a", "b": ("a6a6a6", T),
})

# --------------------------------------------------------------------------
# Mushrooms
# --------------------------------------------------------------------------
item("magic_mushroom", [
    "................",
    "................",
    "......KKKK......",
    "....KKccccKK....",
    "...KcccCCcccK...",
    "..KccCyCCCycCK..",
    "..KcCCCCCCCCCK..",
    ".KCCCyCCCCCyCCK.",
    ".KCCCCCCCCCCCCK.",
    ".KggggggggggggK.",
    "..KKKKsssKKKK...",
    "......KssK......",
    ".....KssSK......",
    ".....KsssK......",
    "....KssssSK.....",
    "....KKKKKKK.....",
], {
    "K": "3a2410", "c": "eebc62", "C": "c4883c", "y": "f8e4a8", "g": "8a6a4a",
    "s": "f2e8d4", "S": "c8b896",
})

item("mushroom_spores", [
    "................",
    "......KKKK......",
    "......KccK......",
    ".....KKKKKK.....",
    "......KgGK......",
    "......KgGK......",
    "......KLLK......",
    "......KLmK......",
    "......KbBK......",
    "......KbBK......",
    "......KbdK......",
    "......KdBK......",
    "......KbBK......",
    ".......KK.......",
    "................",
    "................",
], {
    "K": "2a2a2a", "c": "b07a48", "g": "e0f4f0c8", "G": "a8d0c8d2", "L": "f0ead8",
    "m": "8a4a1a", "b": "7a5a3a", "B": "5a3e24", "d": "3a2614",
})

item("shroom_tea", [
    "................",
    ".....s...s......",
    "......s...s.....",
    ".....s...s......",
    "..KKKKKKKKKK....",
    "..KtttttttTK....",
    "..KmmmmmmmmKKK..",
    "..KmMmmmmmmK.K..",
    "..KmMmCCmmmK.K..",
    "..KmMCCCCmmKKK..",
    "..KmMmSmmmmK....",
    "...KmmmmmmK.....",
    "....KKKKKK......",
    "................",
    "................",
    "................",
], {
    "K": "3a2a20", "t": "a87a3a", "T": "8a5a28", "m": "f2ebe0", "M": "d8ccbc",
    "C": "c4883c", "S": "e0d4bc", "s": "ffffff8c",
})

# --------------------------------------------------------------------------
# Edibles / synthetics
# --------------------------------------------------------------------------
item("space_brownie", [
    "................",
    "................",
    "................",
    "................",
    "................",
    "...KKKKKKKKKK...",
    "..KttttgtttttK..",
    "..KtttgGgttttK..",
    "..KttgGGGgtttK..",
    "..KtttgvgttttK..",
    "..KTTTTTTTTTTK..",
    "..KbbbbbbbbbbK..",
    "..KbBbbbBbbbbK..",
    "..KbbbbbbbBbbK..",
    "...KKKKKKKKKK...",
    "................",
], {
    "K": "1e0e06", "t": "6e3e1e", "T": "512c14", "b": "4a2810", "B": "2e1608",
    "g": "5ac83a", "G": "3a9a2a", "v": "2a6a1e",
})

item("blue_crystal", [
    "................",
    "........K.......",
    ".......KbK......",
    ".......KbBK.....",
    "......KbbBK.....",
    "...K..KbwBK.....",
    "..KbK.KbbBBK....",
    "..KbBKKbbBBK.K..",
    ".KbwBKbbbBBKKbK.",
    ".KbbBBKbbBBKbBK.",
    ".KbbbBKbbbBKbBK.",
    "..KbbBKbbbBKbK..",
    "..KKKKKKKKKKKK..",
    "................",
    "................",
    "................",
], {
    "K": "0e2a5a", "b": "72d6ff", "B": "2a8ad8", "w": "eaffff",
})

item("pixie_dust", [
    "................",
    "................",
    "....KKKKKKKK....",
    "....KrrrrrrK....",
    "....KwwwwwwK....",
    "....KwwwwwwK....",
    "...KwwwwwwwwK...",
    "...KwwwwwwwwK...",
    "...KwpwPwpwwK...",
    "...KpPpppPppK...",
    "...KppPpppPpK...",
    "...KPpppPpppK...",
    "...KpppPppPpK...",
    "....KKKKKKKK....",
    "................",
    "................",
], {
    "K": "5a3a5a", "r": "e03a5a", "w": "f0f0ff78", "p": "ff8ad8", "P": "ffe0f6",
})

item("lab_solvent", [
    "................",
    "......KKKK......",
    "......KccK......",
    "......KKKK......",
    "......KgGK......",
    "......KgGK......",
    ".....KggGGK.....",
    ".....KggGGK.....",
    "....KgggGGGK....",
    "....KLLLLLLK....",
    "...KLlLLLLLLK...",
    "...KLLLLLlLLK...",
    "..KLLlLLLLLLLK..",
    "..KLLLLLLLlLLK..",
    "..KKKKKKKKKKKK..",
    "................",
], {
    "K": "1e2a2a", "c": "c8c8c8", "g": "e0f8f4b4", "G": "a8d8d0c8", "L": "6ae86a",
    "l": "c8ffc8",
})

item("catalyst", [
    "................",
    "................",
    ".....KKKKKK.....",
    ".....KmmmmK.....",
    ".....KKKKKK.....",
    "....KgwwwwgK....",
    "....KgwwwwgK....",
    "....KgpPppgK....",
    "....KpPpPppK....",
    "....KppPpPpK....",
    "....KPppPppK....",
    "....KpPppPpK....",
    "....KKKKKKKK....",
    "................",
    "................",
    "................",
], {
    "K": "2a1a3a", "m": "c84ae8", "g": "e0e0f8b4", "w": "f4f4ff78", "p": "9a3ad8",
    "P": "e0a8ff",
})

item("fertilizer", [
    "................",
    "......K..K......",
    ".......KK.......",
    "......KttK......",
    ".....KbbbbK.....",
    "....KbbbbbbK....",
    "...KbbbbbbbbK...",
    "...KbbLLLLbbK...",
    "..KbbLLgLLLbbK..",
    "..KbbLgGgLLbbK..",
    "..KbbLLvLLLbbK..",
    "..KbbbLLLLbbbK..",
    "..KbbbbbbbbbbK..",
    "...KKKKKKKKKK...",
    "................",
    "................",
], {
    "K": "3a2a14", "t": "c8a050", "b": "c8a878", "L": "f0e8d0", "g": "5ac83a",
    "G": "3a9a2a", "v": "2a6a1e",
})

item("grower_guide", [
    "................",
    "................",
    "...KKKKKKKKKK...",
    "..KGGGGGGGGGPK..",
    "..KGGGGLGGGGPK..",
    "..KGGLGLGLGGPK..",
    "..KGLGLLLGLGPK..",
    "..KGGLLLLLGGPK..",
    "..KGGGLLLGGGPK..",
    "..KGGGGlGGGGPK..",
    "..KGGGGGGGGGPK..",
    "..KGHHHHHHHGPK..",
    "..KDDDDDDDDDpK..",
    "...KKKKKKKKKK...",
    "................",
    "................",
], {
    "K": "14240e", "G": "2e7a24", "H": "c8a030", "L": "f0d050", "l": "a88a28",
    "P": "f4ecd8", "p": "d8ccb0", "D": "1e5418",
})

item("cash", [
    "................",
    "................",
    "................",
    "................",
    "...KKKKKKKKKKKK.",
    "...KhhhhhhhhhhK.",
    ".KKKKKKKKKKKKKK.",
    ".KhhhhhhhhhhhhK.",
    ".KhGGhhwwhhGGhK.",
    ".KhGhhwDDwhhGhK.",
    ".KhhhhwDDwhhhhK.",
    ".KhGhhhwwhhhGhK.",
    ".KhGGhhhhhhGGhK.",
    ".KhhhhhhhhhhhhK.",
    ".KKKKKKKKKKKKKK.",
    "................",
], {
    "K": "1e4a1e", "h": "92dc8a", "G": "3a8a3a", "w": "e8ffe8", "D": "2a6a2a",
})

# --------------------------------------------------------------------------
# Effect icons (GUI)
# --------------------------------------------------------------------------
FACE = [
    "................",
    ".....KKKKKK.....",
    "...KKyyyyyyKK...",
    "..KyyyyyyyyyyK..",
    ".KyyyEyyyyEyyyK.",
    ".KyyyEyyyyEyyyK.",
    "KyyyyyyyyyyyyyyK",
    "KyyyyyyyyyyyyyyK",
    "KyyMyyyyyyyyMyyK",
    "KyyyMyyyyyyMyyyK",
    ".KyyyMMMMMMyyyK.",
    ".KyyyyMmmMyyyyK.",
    "..KyyyyyyyyyyK..",
    "...KKyyyyyyKK...",
    ".....KKKKKK.....",
    "................",
]
icon("effect_giggles", FACE, {"K": "6a4a00", "y": "ffd83a", "E": "3a2a00", "M": "3a2a00", "m": "e84a4a"})
icon("effect_green_out", [
    "................",
    ".....KKKKKK.....",
    "...KKyyyyyyKK...",
    "..KyyyyyyyyyyK..",
    ".KyyEyEyyEyEyyK.",
    ".KyyyEyyyyEyyyK.",
    "KyyyEyEyyEyEyyyK",
    "KyyyyyyyyyyyyyyK",
    "KyyyyyyyyyyyyyyK",
    "KyyyMMMMMMMMyyyK",
    ".KyyMyyMMyyMyyK.",
    ".KyyyyyyyyyyyyK.",
    "..KyyyyyyyyyyK..",
    "...KKyyyyyyKK...",
    ".....KKKKKK.....",
    "................",
], {"K": "1e4a14", "y": "8ad84a", "E": "1e3a10", "M": "1e3a10"})

icon("effect_munchies", [
    "................",
    "..........KKK...",
    ".........KbbbK..",
    "........KbbbbbK.",
    ".......KbbbBbbK.",
    "......KbbbbbbbK.",
    ".....KbbbBbbbK..",
    ".....KbbbbbbK...",
    "....KKbbbbKK....",
    "...KwKKKKKK.....",
    "..KwwK..........",
    ".KwwK...........",
    "KwwwwK..........",
    "KwKKwK..........",
    ".K..K...........",
    "................",
], {"K": "3a1e0a", "b": "c8742e", "B": "8a4a1a", "w": "f4ecdc"})

icon("effect_couch_lock", [
    "................",
    "................",
    "................",
    "...KKKKKKKKKK...",
    "..KrrrrrrrrrrK..",
    "..KrRrrrrrrRrK..",
    "..KrrrrrrrrrrK..",
    "KKKKKKKKKKKKKKKK",
    "KrrKrrrrrrrrKrrK",
    "KrrKRRRRRRRRKrrK",
    "KrrKKKKKKKKKKrrK",
    "KrrrrrrrrrrrrrrK",
    "KKKKKKKKKKKKKKKK",
    ".Kw..........wK.",
    ".KK..........KK.",
    "................",
], {"K": "3a0e0e", "r": "c83a3a", "R": "8a2020", "w": "6a4a2a"})

icon("effect_energy", [
    "................",
    ".........KKKK...",
    "........KyyyK...",
    ".......KyyyK....",
    "......KyyyK.....",
    ".....KyyyK......",
    "....KyyyKKKKK...",
    "...KyyyyyyyyK...",
    "...KKKKKyyyK....",
    ".......KyyK.....",
    "......KyyK......",
    ".....KyyK.......",
    "....KyyK........",
    "...KyK..........",
    "...KK...........",
    "................",
], {"K": "6a4a00", "y": "ffe23a"})

icon("effect_hyper", [
    "................",
    ".........KKKK...",
    "........KyyyK...",
    ".......KyyyK....",
    "......KyyyK.....",
    ".....KyyyK......",
    "....KyyyKKKKK...",
    "...KyyyyyyyyK...",
    "...KKKKKyyyK....",
    ".......KyyK.....",
    "......KyyK......",
    ".....KyyK.......",
    "....KyyK........",
    "...KyK..........",
    "...KK...........",
    "................",
], {"K": "5a0a0a", "y": "ff4a3a"})

HEART = [
    "................",
    "................",
    "..KKKK....KKKK..",
    ".KrrrrK..KrrrrK.",
    "KrwwrrrKKrrrrrrK",
    "KrwrrrrrrrrrrrrK",
    "KrrrrrrrrrrrrrrK",
    "KrrrrrrrrrrrrrRK",
    ".KrrrrrrrrrrrRK.",
    "..KrrrrrrrrrRK..",
    "...KrrrrrrrRK...",
    "....KrrrrrRK....",
    ".....KrrrRK.....",
    "......KrRK......",
    ".......KK.......",
    "................",
]
icon("effect_euphoria", HEART, {"K": "5a0a2a", "r": "ff6ab4", "R": "c83a84", "w": "ffd8ec"})

icon("effect_crash", [
    "................",
    "................",
    "..KKKK....KKKK..",
    ".KrrrrK..KrrrrK.",
    "KrrrrrrKKrrrrrrK",
    "KrrrrrrKrrrrrrrK",
    "KrrrrrrrKrrrrrrK",
    "KrrrrrrKrrrrrrRK",
    ".KrrrrrrKrrrrRK.",
    "..KrrrrKrrrrRK..",
    "...KrrrrKrrRK...",
    "....KrrKrrRK....",
    ".....KrrKRK.....",
    "......KKrK......",
    ".......KK.......",
    "................",
], {"K": "2a2a2a", "r": "8a8a9a", "R": "5a5a6a"})

icon("effect_creative", [
    "................",
    ".....KKKKKK.....",
    "....KyyyyyyK....",
    "...KyywyyyyyK...",
    "...KywyyyyyyK...",
    "...KyyyyyyyyK...",
    "...KyyyyyyyyK...",
    "....KyyyyyyK....",
    ".....KyyyyK.....",
    ".....KmmmmK.....",
    ".....KMMMMK.....",
    ".....KmmmmK.....",
    "......KMMK......",
    ".......KK.......",
    "................",
    "................",
], {"K": "5a4a00", "y": "f8e84a", "w": "ffffff", "m": "c0c0c0", "M": "808080"})

icon("effect_floaty", [
    "................",
    "................",
    "................",
    "......KKKK......",
    ".....KwwwwK.....",
    "...KKwwwwwwKK...",
    "..KwwwwwwwwwwK..",
    ".KwwwwwwwwwwwwK.",
    "KwwwwwwwwwwwwwwK",
    "KwwwwwwwwwwwwwsK",
    "KswwwwwwwwwwwssK",
    ".KsssssssssssK..",
    "..KKKKKKKKKKK...",
    "................",
    "................",
    "................",
], {"K": "5a6a8a", "w": "ffffff", "s": "c8d4ec"})

icon("effect_paranoia", [
    "................",
    "................",
    "................",
    ".....KKKKKK.....",
    "...KKwwwwwwKK...",
    "..KwwwrwwrwwwK..",
    ".KwwwwKKKKwwwwK.",
    "KwwwrKiiiiKrwwwK",
    "KwwwwKiPPiKwwwwK",
    ".KwwwKiPPiKwwwK.",
    "..KwwwKKKKwwwK..",
    "...KKwwwwwwKK...",
    ".....KKKKKK.....",
    "................",
    "................",
    "................",
], {"K": "2a0a0a", "w": "f4f0e8", "r": "e84a4a", "i": "c83a1a", "P": "1a0a0a"})

icon("effect_sleepy", [
    "................",
    "..........KKKK..",
    "....KKK......K..",
    "..KKmmK.....K...",
    ".KmmmK.....KKKK.",
    ".KmmK...........",
    "KmmK....KKK.....",
    "KmmK......K.....",
    "KmmK.....K......",
    "KmmmK...KKK.....",
    ".KmmmK.......KK.",
    ".KmmmmKK...KKmK.",
    "..KKmmmmKKKmmK..",
    "....KKmmmmmKK...",
    "......KKKKK.....",
    "................",
], {"K": "1a1a4a", "m": "f8e87a"})

icon("effect_focus", [
    "................",
    ".......KK.......",
    ".....KKrrKK.....",
    "...KKrrKKrrKK...",
    "..KrrKK..KKrrK..",
    "..KrK..KK..KrK..",
    ".KrK..KrrK..KrK.",
    "KKrK.KrwwrK.KrKK",
    "KKrK.KrwwrK.KrKK",
    ".KrK..KrrK..KrK.",
    "..KrK..KK..KrK..",
    "..KrrKK..KKrrK..",
    "...KKrrKKrrKK...",
    ".....KKrrKK.....",
    ".......KK.......",
    "................",
], {"K": "3a0a0a", "r": "e8483a", "w": "ffffff"})

icon("effect_pain_relief", [
    "................",
    "................",
    ".....KKKKKK.....",
    ".....KwwwwK.....",
    ".....KwrrwK.....",
    ".....KwrrwK.....",
    "..KKKKwrrwKKKK..",
    "..KwwwwrrwwwwK..",
    "..KwrrrrrrrrwK..",
    "..KwrrrrrrrrwK..",
    "..KwwwwrrwwwwK..",
    "..KKKKwrrwKKKK..",
    ".....KwrrwK.....",
    ".....KwwwwK.....",
    ".....KKKKKK.....",
    "................",
], {"K": "5a0a0a", "w": "ffffff", "r": "e83a3a"})

STAR = [
    "................",
    ".......KK.......",
    ".......KyK......",
    "......KyyK......",
    "......KywyK.....",
    "KKKKKKKywyKKKKKK",
    ".KyyyyyywyyyyyK.",
    "..KyyyywwyyyyK..",
    "...KyyyyyyyyK...",
    "....KyyyyyyK....",
    "....KyyyyyyyK...",
    "...KyyyKKyyyK...",
    "...KyyK..KyyyK..",
    "..KyK.....KKyK..",
    "..KK.........K..",
    "................",
]
icon("effect_glow", STAR, {"K": "5a4a00", "y": "fff27a", "w": "ffffff"})

# trippy and a few others are generated procedurally in gen_assets.py

# --------------------------------------------------------------------------
# GUI icons
# --------------------------------------------------------------------------
icon("ui_confirm", [
    "................",
    "................",
    "..............K.",
    ".............KgK",
    "............KggK",
    "...........KggK.",
    ".K........KggK..",
    "KgK......KggK...",
    "KggK....KggK....",
    ".KggK..KggK.....",
    "..KggKKggK......",
    "...KggggK.......",
    "....KggK........",
    ".....KK.........",
    "................",
    "................",
], {"K": "0e3a0e", "g": "4ae84a"})

icon("ui_cancel", [
    "................",
    "................",
    "..KK........KK..",
    ".KrrK......KrrK.",
    ".KrrrK....KrrrK.",
    "..KrrrK..KrrrK..",
    "...KrrrKKrrrK...",
    "....KrrrrrrK....",
    "....KrrrrrrK....",
    "...KrrrKKrrrK...",
    "..KrrrK..KrrrK..",
    ".KrrrK....KrrrK.",
    ".KrrK......KrrK.",
    "..KK........KK..",
    "................",
    "................",
], {"K": "4a0a0a", "r": "f04a4a"})

icon("ui_arrow", [
    "................",
    "................",
    "........KK......",
    "........KgK.....",
    "........KggK....",
    "KKKKKKKKKgggK...",
    "KggggggggggggK..",
    "KgggggggggggggK.",
    "KgggggggggggggK.",
    "KggggggggggggK..",
    "KKKKKKKKKgggK...",
    "........KggK....",
    "........KgK.....",
    "........KK......",
    "................",
    "................",
], {"K": "1e3a14", "g": "8ae85a"})

icon("ui_plus", [
    "................",
    "................",
    "......KKKK......",
    "......KggK......",
    "......KggK......",
    "......KggK......",
    "..KKKKKggKKKKK..",
    "..KggggggggggK..",
    "..KggggggggggK..",
    "..KKKKKggKKKKK..",
    "......KggK......",
    "......KggK......",
    "......KggK......",
    "......KKKK......",
    "................",
    "................",
], {"K": "1e3a14", "g": "8ae85a"})

icon("ui_info", [
    "................",
    ".....KKKKKK.....",
    "...KKbbbbbbKK...",
    "..KbbbbwwbbbbK..",
    ".KbbbbbwwbbbbbK.",
    ".KbbbbbbbbbbbbK.",
    "KbbbbbwwwbbbbbbK",
    "KbbbbbbwwbbbbbbK",
    "KbbbbbbwwbbbbbbK",
    "KbbbbbbwwbbbbbbK",
    ".KbbbbbwwbbbbbK.",
    ".KbbbbwwwwbbbbK.",
    "..KbbbbbbbbbbK..",
    "...KKbbbbbbKK...",
    ".....KKKKKK.....",
    "................",
], {"K": "0a1e4a", "b": "3a7ae8", "w": "ffffff"})

icon("ui_name_tag", [
    "................",
    "................",
    "..........KKK...",
    ".........KwwwK..",
    "........KwwKwK..",
    ".......KwwwwwK..",
    "......KtttttK...",
    ".....KtttttK....",
    "....KttLLtK.....",
    "...KtLLLtK......",
    "..KttttK........",
    "..KtttK.........",
    "...KKK..........",
    "................",
    "................",
    "................",
], {"K": "3a2a14", "w": "c8c8c8", "t": "f0e0b4", "L": "8a7a5a"})

icon("ui_dna", [
    "................",
    "...KK......KK...",
    "...KrK....KbK...",
    "....KrK..KbK....",
    ".....KrKKbK.....",
    "......KrbK......",
    ".....KbKKrK.....",
    "....KbK..KrK....",
    "...KbK....KrK...",
    "...KbwwwwwwrK...",
    "....KbK..KrK....",
    ".....KbKKrK.....",
    "......KbrK......",
    ".....KrKKbK.....",
    "....KrK..KbK....",
    "................",
], {"K": "1a1a2a", "r": "f04a8a", "b": "4ac8f0", "w": "ffffff"})

icon("ui_palette", [
    "................",
    "................",
    ".....KKKKKK.....",
    "...KKwwwwwwKK...",
    "..KwrrwwwbbwwK..",
    ".KwwrrwwwbbwwwK.",
    ".KwwwwwwwwwwwwK.",
    "KwyywwwKKwwwggwK",
    "KwyywwKKKKwwggwK",
    "KwwwwwKKKKwwwwK.",
    ".KwwwwwKKwwwwwK.",
    ".KwppwwwwwwwwK..",
    "..KppwwwwwwwKK..",
    "...KKwwwwwKK....",
    ".....KKKKK......",
    "................",
], {"K": "3a2a1a", "w": "f0e0c0", "r": "f04a4a", "b": "4a8af0", "y": "f0e04a", "g": "4ad84a", "p": "c84af0"})

icon("ui_wallet", [
    "................",
    "................",
    "................",
    "...KKKKKKKKKK...",
    "..KhhhhhhhhhhK..",
    ".KKKKKKKKKKKKKK.",
    ".KbbbbbbbbbbbbK.",
    ".KbbbbbbbbbKKKK.",
    ".KbbbbbbbbKyyyK.",
    ".KbbbbbbbbKyYyK.",
    ".KbbbbbbbbKyyyK.",
    ".KbbbbbbbbbKKKK.",
    ".KBBBBBBBBBBBBK.",
    ".KKKKKKKKKKKKKK.",
    "................",
    "................",
], {"K": "2a1a0a", "h": "92dc8a", "b": "8a5a2e", "B": "5a3a1a", "y": "f8d84a", "Y": "c8a020"})

icon("ui_sell", [
    "................",
    ".......KKK......",
    "......KyyyK.....",
    ".......KKK......",
    ".....KKbbbKK....",
    "....KbbbbbbbK...",
    "...KbbbbbbbbbK..",
    "..KbbbbyyybbbbK.",
    "..KbbbbyKKbbbbK.",
    "..KbbbbyyybbbbK.",
    "..KbbbbbKybbbbK.",
    "..KbbbbyyybbbbK.",
    "..KbbbbbbbbbbbK.",
    "...KbbbbbbbbbK..",
    "....KKKKKKKKK...",
    "................",
], {"K": "2a1e0a", "b": "d8b878", "y": "3a8a2a"})

icon("progress_empty", [
    "................",
    "................",
    "................",
    "................",
    "................",
    "KKKKKKKKKKKKKKKK",
    "gggggggggggggggg",
    "................",
    "................",
    "................",
    "GGGGGGGGGGGGGGGG",
    "KKKKKKKKKKKKKKKK",
    "................",
    "................",
    "................",
    "................",
], {"K": "1a2a2a", "g": "ffffff50", "G": "00000030"})

icon("progress_full", [
    "................",
    "................",
    "................",
    "................",
    "................",
    "KKKKKKKKKKKKKKKK",
    "wwwwwwwwwwwwwwww",
    "LLlLLLLLLlLLLLLL",
    "LLLLLLlLLLLLLlLL",
    "LlLLLLLLLLLLLLLL",
    "DDDDDDDDDDDDDDDD",
    "KKKKKKKKKKKKKKKK",
    "................",
    "................",
    "................",
    "................",
], {"K": "1a2a2a", "w": "c8ffc8", "L": "4ae84a", "l": "a8ffa8", "D": "2a9a2a"})

# leaf type icons
icon("type_sativa", [
    "................",
    ".......K........",
    "......KgK.......",
    "..K...KgK...K...",
    "..KgK.KgK.KgK...",
    "...KgKKgKKgK....",
    "K...KgKgKgK...K.",
    "KgK..KgggK..KgK.",
    ".KggKKgggKKggK..",
    "..KKggggggggKK..",
    "....KKgggggKK...",
    "......KKgKK.....",
    ".......KsK......",
    ".......KsK......",
    "........K.......",
    "................",
], {"K": "1e3a10", "g": "8ae84a", "s": "5a8a2a"})

icon("type_indica", [
    "................",
    "......KKKK......",
    ".....KggggK.....",
    "..KK.KggggK.KK..",
    ".KggKKggggKKggK.",
    ".KgggKggggKgggK.",
    "..KgggKggKgggK..",
    "KKKKggKggKggKKKK",
    "KgggggggggggggGK",
    ".KKgggggggggGKK.",
    "...KKKgggGKKK...",
    "......KKgK......",
    ".......KsK......",
    ".......KsK......",
    "........K.......",
    "................",
], {"K": "0e2a0a", "g": "3a8a2a", "G": "2a6a1e", "s": "4a6a2a"})

icon("type_hybrid", [
    "................",
    ".......K........",
    "......KgK.......",
    "...K..KgK..K....",
    "..KgK.KgK.KgK...",
    "..KggKKgKKggK...",
    "K..KggKgKggK..K.",
    "KgK.KgggggK.KgK.",
    ".KggKKgggKKggK..",
    "..KKggggggggKK..",
    "....KKgggggKK...",
    "......KKgKK.....",
    ".......KsK......",
    ".......KsK......",
    "........K.......",
    "................",
], {"K": "1a3a10", "g": "5ac83a", "s": "4a7a2a"})


# --------------------------------------------------------------------------
# Coca & opium poppy (v1.1)
# --------------------------------------------------------------------------
_SEED_ART = ITEMS["seed_pack"]["art"]
_SEED_PAL = dict(ITEMS["seed_pack"]["pal"])

item("coca_seeds", _SEED_ART, dict(_SEED_PAL, w="5ac83a", v="2e7a24", S="b03a2a", s="6a1a12",
                                   Q="b8c890", p="e4ecc8", q="c8d4a8"))
item("poppy_seeds", _SEED_ART, dict(_SEED_PAL, w="e83a3a", v="8a1414", S="4a4a5a", s="2a2a34",
                                    Q="c8b8a0", p="f0e8dc", q="d8cfc0"))

item("coca_leaves", [
    "................",
    "................",
    ".........KKK....",
    "........KgggK...",
    ".......KgGgggK..",
    "..KKK..KggGggK..",
    ".KgggK.KgggGgK..",
    "KgGgggKKggggGK..",
    "KggGgggKKgggK...",
    ".KggGgggsKKK....",
    "..KKgGggsK.KK...",
    "....KKKsK.KrrK..",
    "......KsK.KrRK..",
    ".....KsK...KK...",
    ".....KK.........",
    "................",
], {"K": "143a10", "g": "5ac83a", "G": "2e8a24", "s": "6a5a2a", "r": "e8402a", "R": "a01a12"})

item("poppy_pod", [
    "................",
    "......KKKK......",
    ".....KcKKcK.....",
    "....KKccccKK....",
    "...KgggggggGK...",
    "..KgwgggggggGK..",
    "..KgwggggggggK..",
    "..KggggggggGGK..",
    "..KgggggggggGK..",
    "...KgggggggGK...",
    "....KKgggGKK....",
    "......KssK......",
    "......KssK......",
    ".......KsK......",
    ".......KK.......",
    "................",
], {"K": "1e3a2a", "c": "8a7aa8", "g": "8ec8a0", "G": "5a9a72", "w": "d8f0e0", "s": "6a9a5a"})

_BAG_ART = ITEMS["pixie_dust"]["art"]
item("cocaine", _BAG_ART, {"K": "5a5a6a", "r": "3a7ae8", "w": "f0f0ff60", "p": "e6e6ee", "P": "ffffff"})
item("heroin", _BAG_ART, {"K": "4a3a2a", "r": "e8c040", "w": "f0f0ff60", "p": "a8825a", "P": "d4b48a"})


# --------------------------------------------------------------------------
# Menu icons (v1.1)
# --------------------------------------------------------------------------
icon("ui_back", [row[::-1] for row in ICONS["ui_arrow"]["art"]], ICONS["ui_arrow"]["pal"])

icon("ui_catalog", [
    "................",
    "......KKKK......",
    "....KKmmmmKK....",
    "...KbbKKKKbbK...",
    "...KbwwwwwwbK...",
    "...KbwLLwwwbK...",
    "...KbwwwwwwbK...",
    "...KbwLLLwwbK...",
    "...KbwwwwwwbK...",
    "...KbwLLwwwbK...",
    "...KbwwwwwwbK...",
    "...KbwLLLLwbK...",
    "...KbwwwwwwbK...",
    "...KbbbbbbbbK...",
    "....KKKKKKKK....",
    "................",
], {"K": "2a1e10", "m": "c0c0c8", "b": "a8743a", "w": "f4f0e4", "L": "7a7a7a"})

icon("ui_trophy", [
    "................",
    "..KKKKKKKKKKKK..",
    ".KyKyyyyyyyyKyK.",
    ".KyKyywyyyyyKyK.",
    ".KyKyywyyyyyKyK.",
    "..KKyyyyyyyyKK..",
    "...KyyyyyyyyK...",
    "....KyyyyyyK....",
    ".....KKyyKK.....",
    "......KyyK......",
    "......KyyK......",
    ".....KYYYYK.....",
    "....KbbbbbbK....",
    "....KbBbbBbK....",
    "....KKKKKKKK....",
    "................",
], {"K": "5a3a00", "y": "f8c83a", "w": "fff4c0", "Y": "d89a20", "b": "6a4a2a", "B": "4a3018"})

icon("ui_orders", [
    "................",
    "...KKKKKKKKK....",
    "...KwwwwwwwwK...",
    "...KwLLLLwwwK...",
    "...KwwwwwwwwK...",
    "...KwLLLwwwwK...",
    "...KwwwwwwwwK...",
    "...KwLLLLLwwK...",
    "...KwwwwwwKKK...",
    "...KwwwwwKyyyK..",
    "...KwLLwKyYYyyK.",
    "...KwwwwKyYyyyK.",
    "...KKKKKKyYYyyK.",
    ".........KyyyK..",
    "..........KKK...",
    "................",
], {"K": "3a2a14", "w": "f4f0e4", "L": "8a8a8a", "y": "f8d84a", "Y": "c89a20"})

icon("ui_crown", [
    "................",
    "................",
    "................",
    "..K....KK....K..",
    ".KyK..KyyK..KyK.",
    ".KyyK.KyyK.KyyK.",
    ".KyyyKyyyyKyyyK.",
    ".KyyyyyyyyyyyyK.",
    ".KyRyyyByyyGyyK.",
    ".KyyyyyyyyyyyyK.",
    ".KYYYYYYYYYYYYK.",
    "..KKKKKKKKKKKK..",
    "................",
    "................",
    "................",
    "................",
], {"K": "5a3a00", "y": "f8d84a", "Y": "c89a20", "R": "e83a3a", "B": "3a7ae8", "G": "3ac84a"})

icon("ui_fire", [
    "................",
    ".......K........",
    "......KrK.......",
    "......KrrK......",
    ".....KrrrK..K...",
    "..K..KrorrK.KK..",
    "..KK.KroorrKrK..",
    "..KrKKrooorrrK..",
    "..KrrrooyyoorK..",
    "..KrroyyyyyorK..",
    "..KrooyyyyyorK..",
    "...KroyyyyyoK...",
    "...KrooyyyyoK...",
    "....KroooooK....",
    ".....KKKKKK.....",
    "................",
], {"K": "5a1a00", "r": "e8402a", "o": "f8902a", "y": "fff04a"})

icon("ui_recipes", [
    "................",
    ".KKKKKKKKKKKKKK.",
    ".KBBBBBBBBBBBBK.",
    ".KBKKKKKKKKKKBK.",
    ".KBKwwKwwKwwKBK.",
    ".KBKwwKggKwwKBK.",
    ".KBKKKKKKKKKKBK.",
    ".KBKggKggKggKBK.",
    ".KBKwgKggKgwKBK.",
    ".KBKKKKKKKKKKBK.",
    ".KBKwwKggKwwKBK.",
    ".KBKwwKwgKwwKBK.",
    ".KBKKKKKKKKKKBK.",
    ".KBBBBBBBBBBBBK.",
    ".KKKKKKKKKKKKKK.",
    "................",
], {"K": "2a1a0a", "B": "8a5a2e", "w": "c8a070", "g": "5ac83a"})

icon("ui_exchange", [
    "................",
    "..KKKK.....K....",
    ".KcccdK....KK...",
    "KccddddK.KKKaK..",
    "KcddddDK.KaaaaK.",
    ".KddDDK..KKKaK..",
    "..KDDK.....KK...",
    "...KK......K....",
    "....K......KKK..",
    "...KK....KKyyyK.",
    "..KaKKK.KyyYYyyK",
    ".KaaaaK.KyYyyyyK",
    "..KaKKK.KyYyyyyK",
    "...KK...KyyYYyyK",
    "....K....KKyyyK.",
    "...........KKK..",
], {"K": "1a2a2a", "c": "c8fff8", "d": "4ae0e0", "D": "2a9aa8", "a": "6ae84a", "y": "f8d84a", "Y": "c89a20"})

icon("ui_jobs", [
    "................",
    "................",
    "...KKKKKKK......",
    "..KsssssssKK....",
    "...KKKKKsssK....",
    "........KhssK...",
    ".......KhKKssK..",
    "......KhK..KsK..",
    ".....KhK....KsK.",
    "....KhK......KK.",
    "...KhK..........",
    "..KhK...........",
    ".KhK............",
    ".KK.............",
    "................",
    "................",
], {"K": "1a1a1a", "s": "b8c8d0", "h": "8a5a2e"})

icon("ui_pay", [
    "................",
    "................",
    "................",
    "...KKK..........",
    ".KKyyyKK....K...",
    ".KyYYYyK....KK..",
    "KyYyyyyyKKKKKaK.",
    "KyyYYYyyKKaaaaaK",
    "KyyyyyYyKKKKKaK.",
    ".KyYYYyK....KK..",
    ".KKyyyKK....K...",
    "...KKK..........",
    "................",
    "................",
    "................",
    "................",
], {"K": "4a3000", "y": "f8d84a", "Y": "c89a20", "a": "6ae84a"})


# --------------------------------------------------------------------------
# v1.3 drugs
# --------------------------------------------------------------------------
item("peyote_seeds", _SEED_ART, dict(_SEED_PAL, w="7ac8a0", v="3a7a5a", S="e86aa8", s="8a2a5a",
                                     Q="d8b878", p="f4e2b0", q="dcc690"))
item("ketamine", ITEMS["blue_crystal"]["art"], {"K": "3a4a5a", "b": "eef4fa", "B": "aebccc", "w": "ffffff"})
item("dmt", ITEMS["blue_crystal"]["art"], {"K": "5a2a0a", "b": "f8b040", "B": "c87a1a", "w": "fff0c0"})
item("angel_dust", _BAG_ART, {"K": "4a3a2a", "r": "d83a2a", "w": "f0f0ff60", "p": "d8b880", "P": "f0d8a8"})
item("crack", ITEMS["moon_rock"]["art"], {"K": "5a5040", "k": "f4ecd0", "j": "d8c890", "b": "bcae80"})
item("opium", ITEMS["hash"]["art"], {"K": "140a04", "t": "5a3a1a", "T": "3a2410", "h": "4a2e14", "d": "2a180a",
                                     "S": "8a5a2a"})

item("ecstasy", [
    "................",
    "................",
    "................",
    ".....KKKKKK.....",
    "...KKppppppKK...",
    "..KppPPppppppK..",
    "..KpPpppKKpppK..",
    ".KppppppKKppppK.",
    ".KppKpppppKpppK.",
    ".KpppKKKKKppppK.",
    ".KppppppppppddK.",
    "..KppppppppddK..",
    "..KdppppppdddK..",
    "...KKddddddKK...",
    ".....KKKKKK.....",
    "................",
], {"K": "6a1a4a", "p": "ff8ad0", "P": "ffd8f0", "d": "d85aa8"})

item("mescaline", [
    "................",
    "................",
    "...........KKK..",
    "..........KwwwK.",
    ".........KwwwwK.",
    "........KwwwwwK.",
    ".......KwwwwwK..",
    "......KyKwwwK...",
    ".....KyyyKKK....",
    "....KyyyyyK.....",
    "...KyYyyyK......",
    "..KyYyyyK.......",
    "..KyyyyK........",
    "...KKKK.........",
    "................",
    "................",
], {"K": "4a3a10", "w": "f4f0e0", "y": "e8c84a", "Y": "fff0a0"})

item("lean", [
    "................",
    "................",
    "..KKKKKKKKKKKK..",
    "..KppppPppppwK..",
    "..KwwwwwwwwwwK..",
    "...KwwwwwwwwK...",
    "...KwgwwwwwwK...",
    "...KwgwwwwwwK...",
    "...KwwwwwwwwK...",
    "....KwwwwwwK....",
    "....KwwwwwwK....",
    "....KwwwwwwK....",
    "....KwwwwwwK....",
    ".....KKKKKK.....",
    "................",
    "................",
], {"K": "4a4a5a", "w": "f8f8fc", "g": "d8d8e4", "p": "a050d8", "P": "d098ff"})

item("peyote_button", [
    "................",
    "................",
    "................",
    ".......KK.......",
    "......KpPK......",
    "....KKKppKKK....",
    "...KggGgGggGK...",
    "..KgGggggggGgK..",
    "..KgwgGgggGgwK..",
    ".KggggGgGgggggK.",
    ".KgGgggggggggGK.",
    ".KggwgGggGgwggK.",
    "..KgggggggggGK..",
    "...KKdddddddK...",
    ".....KKKKKK.....",
    "................",
], {"K": "1e3a2a", "g": "7ac8a0", "G": "4a9a7a", "w": "e8f0e0", "p": "f07ab8", "P": "ffd0e8", "d": "a87a4a"})

item("gummies", [
    "................",
    "................",
    "...KK....KK.....",
    "..KggK..KggK....",
    "..KgwgKKgggK....",
    "...KggggggK.....",
    "...KgEggEgK.....",
    "..KggggggggK....",
    ".KggggGGggggK...",
    ".KgggGggGgggK...",
    "..KggggggggK.KK.",
    "..KggKKKKggKKggK",
    "..KgK....KgKgwgK",
    "...K......K.KggK",
    ".............KK.",
    "................",
], {"K": ("5a5a5a", T), "g": ("e8e8e8", T), "G": ("b8b8b8", T), "w": "ffffff", "E": "2a2a2a"})

# --------------------------------------------------------------------------
# v1.3 effects
# --------------------------------------------------------------------------
icon("effect_lucky", [
    "................",
    ".....KK..KK.....",
    "....KggKKggK....",
    "....KgGggGgK....",
    ".KK.KggggggK.KK.",
    "KggKKKggggKKKggK",
    "KgGgggKggKgggGgK",
    "KggggggKKggggggK",
    "KgGgggKggKgggGgK",
    "KggKKKggggKKKggK",
    ".KK.KggggggK.KK.",
    "....KgGggGgK....",
    "....KggKKggK....",
    ".....KK.Ks......",
    "........Ks......",
    ".........K......",
], {"K": "0e3a14", "g": "5ae87a", "G": "2ea84a", "s": "3a6a2a"})

icon("effect_night_owl", [
    "................",
    "..KK........KK..",
    "..KbK......KbK..",
    "..KbbKKKKKKbbK..",
    "..KbbbbbbbbbbK..",
    ".KbKKKbbbbKKKbK.",
    ".KKyyyKbbKyyyKK.",
    ".KKyEyKbbKyEyKK.",
    ".KKyyyKooKyyyKK.",
    ".KbKKKbooKKKbbK.",
    "..KbbbbKKbbbbK..",
    "..KBbbbbbbbbBK..",
    "...KBBbbbbBBK...",
    "....KKBBBBKK....",
    "......KKKK......",
    "................",
], {"K": "1a1a3a", "b": "7a6aa8", "B": "5a4a88", "y": "ffe24a", "E": "1a1a1a", "o": "f0a03a"})

icon("effect_aquatic", [
    "................",
    "...........KK...",
    "..........KwK...",
    "...KK......KK...",
    "..KwwK..........",
    "..KwwK...KKKK...",
    "...KK...KbbbbK..",
    ".......KbbbbbbK.",
    "..KKK.KbbEbbbbbK",
    ".KbbbKbbbbbbbbK.",
    "KbbbbbKbbbbbbK..",
    ".KbbbKKBbbbbBK..",
    "..KKK..KBBBBK...",
    "........KKKK....",
    "................",
    "................",
], {"K": "0a2a4a", "b": "4ac8f0", "B": "2a88c0", "w": "d8f4ff", "E": "0a1a2a"})

icon("effect_fireproof", [
    "................",
    "......KKKK......",
    ".....KssssK.....",
    "....KsssssK.....",
    "...KssKrrKsK....",
    "...KsKrrrKsK....",
    "...KsKroorKsK...",
    "...KsKroyorKsK..",
    "...KsKroyyoKsK..",
    "...KsKroyyorKsK.",
    "...KsKrooyorKsK.",
    "....KsKrooorKsK.",
    "....KsKKrrrKKsK.",
    ".....KssKKKssK..",
    "......KKsssKK...",
    "........KKK.....",
], {"K": "2a2a3a", "s": "a8b8c8", "r": "e8402a", "o": "f8902a", "y": "fff04a"})

icon("effect_ghost", [
    "................",
    ".....KKKKKK.....",
    "...KKwwwwwwKK...",
    "..KwwwwwwwwwwK..",
    "..KwwwwwwwwwwK..",
    ".KwwwEEwwEEwwwK.",
    ".KwwwEEwwEEwwwK.",
    ".KwwwwwwwwwwwwK.",
    ".KwwwwwEEwwwwwK.",
    ".KwwwwwEEwwwwwK.",
    ".KwwwwwwwwwwwwK.",
    ".KwwwwwwwwwwwwK.",
    ".KwgwwwgwwwgwwK.",
    ".KgKgwgKgwgKgwK.",
    ".KK.KgK.KgK.KgK.",
    "................",
], {"K": "5a6a7a", "w": "eef2f6", "g": "c0c8d0", "E": "2a2a3a"})

icon("effect_loved_up", [
    "................",
    ".KKK..KKK.......",
    "KrrrKKrrrK......",
    "KrwrrrrrrK......",
    "KrrrrrrrrK......",
    ".KrrrrrrK..KK.KK",
    "..KrrrrK.KppKppK",
    "...KrrK..KpwpppK",
    "....KK...KpppppK",
    "..........KpppK.",
    "...........KpK..",
    "............K...",
    "................",
    "................",
    "................",
    "................",
], {"K": "5a0a2a", "r": "ff5aa8", "R": "c83a84", "w": "ffd8ec", "p": "ff9ad0"})

icon("effect_visions", [
    "................",
    "................",
    "....KKKKKKKK....",
    "..KKwwwwwwwwKK..",
    ".KwwwwKKKKwwwwK.",
    "KwwwwKppppKwwwwK",
    "KwwwKppPPppKwwwK",
    "KwwwKpPEEPpKwwwK",
    "KwwwKpPEEPpKwwwK",
    "KwwwKppPPppKwwwK",
    "KwwwwKppppKwwwwK",
    ".KwwwwKKKKwwwwK.",
    "..KKwwwwwwwwKK..",
    "....KKKKKKKK....",
    "................",
    "................",
], {"K": "2a1a4a", "w": "e8dcff", "p": "9a6af0", "P": "c8a8ff", "E": "1a0a2a"})

icon("effect_rage", FACE, {"K": "5a0a0a", "y": "e84a3a", "E": "2a0000", "M": "2a0000", "m": "ffd0d0"})

icon("effect_dizzy", [
    "................",
    ".....KKKKKK.....",
    "...KKyyyyyyKK...",
    "..KyyKKKKKKyyK..",
    ".KyyKyyyyyyKyyK.",
    ".KyKyyKKKKyyKyK.",
    "KyKyyKyyyyKyyKyK",
    "KyKyKyyKKyyKyKyK",
    "KyKyKyKyyKyKyKyK",
    "KyKyKyyyyKyKyKyK",
    "KyKyyKKKKyyKyKyK",
    ".KyKyyyyyyyKyyK.",
    ".KyyKKKKKKKyyK..",
    "..KyyyyyyyyyK...",
    "...KKKKKKKKK....",
    "................",
], {"K": "4a5a10", "y": "d8e84a"})

icon("effect_dissociated", [
    "................",
    "......KKKK......",
    ".....KccccK.....",
    ".....KccccK.....",
    "......KKKK......",
    "....KKccccKK....",
    "...KcKccccKcK...",
    "...KcKccccKcK...",
    "....K.KccK.K....",
    "......KccK......",
    ".....KcKKcK.....",
    "....KcK..KcK....",
    "................",
    "..d...d....d..d.",
    "...dd...dd...dd.",
    "................",
], {"K": "3a4a5a", "c": "b8d0dc80", "d": "8aa8b8"})

icon("effect_syrupy", [
    "................",
    ".......KK.......",
    "......KppK......",
    "......KppK......",
    ".....KppppK.....",
    ".....KpwppK.....",
    "....KpwpppPK....",
    "....KppppppK....",
    "...KpppppppPK...",
    "...KppppppPPK...",
    "...KpppppPPPK...",
    "....KppPPPPK....",
    ".....KKKKKK.....",
    "................",
    "................",
    "................",
], {"K": "3a1a5a", "p": "b060e8", "P": "7a3aa8", "w": "f0d8ff"})

icon("effect_bad_trip", [
    "................",
    "....KKKKKKKK....",
    "...KwwwwwwwwK...",
    "..KwwwwwwwwwwK..",
    "..KwwwwwwwwwwK..",
    "..KwKKKwwKKKwK..",
    "..KwKKKwwKKKwK..",
    "..KwwKwwwwKwwK..",
    "...KwwwKKwwwK...",
    "....KwwwwwwK....",
    "....KwKwKwKK....",
    "....KKwKwKwK....",
    ".....KKKKKK.....",
    "................",
    "................",
    "................",
], {"K": "2a0a14", "w": "d8c8c0"})

# --------------------------------------------------------------------------
# v1.3 menu icons
# --------------------------------------------------------------------------
icon("ui_home", [
    "................",
    ".......KK.......",
    "......KrrK......",
    ".....KrrrrK.KK..",
    "....KrrrrrrKbK..",
    "...KrrrrrrrrKK..",
    "..KrrrrrrrrrrK..",
    ".KKKKKKKKKKKKKK.",
    "..KwwwwwwwwwwK..",
    "..KwKKwwwKKKwK..",
    "..KwKbKwwKdKwK..",
    "..KwKKwwwKdKwK..",
    "..KwwwwwwKdKwK..",
    "..KwwwwwwKdKwK..",
    "..KKKKKKKKKKKK..",
    "................",
], {"K": "2a1a0a", "r": "d84a3a", "w": "f0e0c0", "b": "6ac8f0", "d": "8a5a2e"})

icon("ui_bank", [
    "................",
    ".......KK.......",
    ".....KKyyKK.....",
    "...KKyyyyyyKK...",
    ".KKyyyyyyyyyyKK.",
    "KKKKKKKKKKKKKKKK",
    ".KwK.KwK.KwK.KwK"[:16],
    ".KwK.KwK.KwK.KwK"[:16],
    ".KwK.KwK.KwK.KwK"[:16],
    ".KwK.KwK.KwK.KwK"[:16],
    ".KwK.KwK.KwK.KwK"[:16],
    "KKKKKKKKKKKKKKKK",
    "KyyyyyyyyyyyyyyK",
    "KKKKKKKKKKKKKKKK",
    "................",
    "................",
], {"K": "3a2a0a", "y": "f8d84a", "w": "e8e0d0"})

icon("ui_lock", [
    "................",
    ".....KKKKKK.....",
    "....KssssssK....",
    "...KsK....KsK...",
    "...KsK....KsK...",
    "...KsK....KsK...",
    "..KKKKKKKKKKKK..",
    "..KyyyyyyyyyyK..",
    "..KyyyyKKyyyyK..",
    "..KyyyKKKKyyyK..",
    "..KyyyyKKyyyyK..",
    "..KyyyyKKyyyyK..",
    "..KYYYYYYYYYYK..",
    "..KKKKKKKKKKKK..",
    "................",
    "................",
], {"K": "3a2a0a", "s": "b8c0c8", "y": "f8c83a", "Y": "c8982a"})
