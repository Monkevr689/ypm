# KushCraft – grow, cook, roll & deal (Paper 26.3)

A **server-side only** Paper plugin: custom plants, strains, a lab, a strain
maker, rolling, a dealer shop and custom effects. All of it comes in **one jar**.
Players don't install any mods. The plugin builds its own resource pack (custom
textures, 3D models and GUI art), hosts it, and sends it to every player who
joins.

**Download:** [`release/KushCraft-1.0.0.jar`](release/KushCraft-1.0.0.jar). Drop it in `plugins/` and restart.

| Items | Machines | Plants |
|---|---|---|
| ![items](docs/items_preview.png) | ![machines](docs/blocks_preview.png) | ![plants](docs/plants3d_preview.png) |

![GUIs](docs/gui_preview.png)

## Features

* **Custom plants that really grow.** Cannabis has 5 growth stages, magic mushrooms have 4. Each is a 3D model drawn with display entities, so no vanilla blocks get replaced.
  * **Sativa** grows tall (2 blocks) and loves **warm** biomes.
  * **Indica** stays short and bushy and loves **cold** biomes.
  * **Hybrid** sits in between and loves **mild** biomes.
  * Growth speed and harvest **quality (1–5 ★)** depend on light, soil (watered farmland or a Planter Box is best), the biome climate and fertilizer.
  * Every strain has its own **bud colour**, visible on the seeds, the buds and the growing plant.
* **10 built-in strains**, and **you can breed your own** in the Strain Maker. Pick a name, type, colour and up to 3 effects. New strains are saved to `strains.yml`, and admins can add strains there too.
* **Biome landraces:** breaking grass in a jungle can drop *Jungle Haze* seeds, a taiga gives *Northern Lights*, a cherry grove gives *Cherry Pie*, and so on.
* **Machines** (custom 3D blocks):

  | Machine | What it does |
  |---|---|
  | **Lab Station** | Cooks hash, moon rocks, space brownies, shroom tea and synthetics (Lucid Tabs, Blue Crystal, Pixie Dust) on a timer |
  | **Strain Maker** | Crosses two seeds into a brand new strain |
  | **Rolling Table** | Rolls joints and blunts |
  | **Drying Rack** | Turns fresh buds into dried buds |
  | **Grow Lamp** | Gives light and a growth boost, so you can grow indoors or underground |
  | **Planter Box** | Perfect soil: faster growth and +1 ★ |
  | **Dealer Stand** | A very simple shop: click to buy, click your items to sell |
* **16 custom effects** with their own icons, shown on a boss-bar HUD with a "high" meter: Giggles, Munchies, Couch Lock, Energy Rush, Euphoria, Creative Flow, Floaty, Paranoia (fake creeper hisses behind you), Sleepy, Focus, Pain Relief, Trippy (rainbow particles and a colour-shifting sky), Hyper (followed by a Crash), Glow, and Greened Out (if you smoke too much).
* **Custom GUIs:** every machine menu has its own full background image.
* **Grower's Handbook:** an in-game book that explains everything, including all recipes. New players get one on their first join.
* **Simple money:** a built-in wallet, or **Vault** (EssentialsX, CMI, …) if installed.

## Installing

1. Use **Paper 26.3** (Java 25).
2. Put `KushCraft-1.0.0.jar` in `plugins/` and start the server.
3. **Open port `8163`** (TCP) on your firewall or hosting panel. This is the plugin's built-in web server, which hands the texture pack to players.
   * Can't open a port? Upload `plugins/KushCraft/KushCraft-pack.zip` somewhere that gives a direct download link and set `resource-pack.url` in `config.yml`.
   * If players reach your server through an address that can't serve the pack, set `resource-pack.host`.
4. Join. Accept the resource pack and you're done.

> Players who decline the pack still have every feature, but items and plants will look like placeholder textures. Set `resource-pack.required: true` to make the pack mandatory.

### Hosted server (Shockbyte etc.) - "FAILED_DOWNLOAD"

Game hosts usually only open the game port, so players can't reach port 8163 and the console shows
`could not load the resource pack (FAILED_DOWNLOAD)`. Host the zip somewhere else instead:

1. In `plugins/KushCraft/config.yml` set `resource-pack.url` to a **direct** link to the zip:
   * GitHub (easiest, the zip is in this repo):
     `https://raw.githubusercontent.com/Monkevr689/ypm/claude/inspiring-keller-65lzp9/kushcraft/release/KushCraft-pack.zip`
   * or upload `KushCraft-pack.zip` (also written to `plugins/KushCraft/`) to Dropbox and use the share link ending in `?dl=1`.
2. Restart the server (or `/kush reload`).

The plugin downloads that file itself on start-up and sends players its hash, so any copy of the zip works.
The console prints `Resource pack: downloaded ... sha1 ...` when it is set up correctly.

## How to play

1. **Seeds:** break grass, ferns or dead bushes (about a 4% chance; the strain depends on the biome) or buy them from a Dealer. Break small mushrooms to get spores.
2. **Plant:** right-click the **top** of farmland, grass, dirt, moss or a Planter Box. Mushrooms like mycelium or podzol and **darkness**.
3. **Grow:** right-click a plant to see its progress; sneak + right-click shows full details. It needs light level 9+ (or a Grow Lamp). Fertilizer and bone meal speed it up.
4. **Harvest:** click a fully grown plant to get **Fresh Buds** and seeds.
5. **Dry:** right-click a Drying Rack with fresh buds. A few minutes later you get **Dried Buds**.
6. **Use:** roll joints or blunts at the Rolling Table, smoke dried bud, hash or moon rocks in a Bong, or cook in the Lab.
7. **Sell** at the Dealer. Better ★ quality and stronger strains earn more.

### Crafting (vanilla crafting table)

| Item | Recipe |
|---|---|
| Lab Station | `Bottle Brewing-Stand Bottle / Iron Cauldron Iron / Iron _ Iron` |
| Strain Maker | `Glass×3 / Glass Emerald Glass / Iron Redstone-Block Iron` |
| Rolling Table | `Paper×3 / Planks×3 / Planks _ Planks` |
| Drying Rack | `Stick×3 / String×3 / Stick _ Stick` |
| Grow Lamp | `Iron×3 / Glowstone-Dust Redstone-Lamp Glowstone-Dust / _ Iron _` |
| Planter Box ×2 | `Planks Bone-Meal Planks / Planks Dirt Planks / Planks×3` |
| Dealer Stand | `Emerald×3 / Planks Chest Planks / Planks×3` |
| Bong | `_ Glass _ / _ Glass Iron-Nugget / Glass Bottle Glass` |
| Rolling Papers ×6 | 3 Paper + Sugar Cane |
| Blunt Wrap ×3 | Paper + Cocoa Beans + Dried Kelp |
| Fertilizer ×3 | 2 Bone Meal + Rotten Flesh |
| Lab Solvent ×2 | Glass Bottle + Sugar + Redstone + Gunpowder |
| Catalyst ×2 | Amethyst Shard + Glowstone Dust + Redstone |
| Grower's Handbook | Book + Wheat Seeds |

### Lab Station recipes

| Product | Ingredients | Time |
|---|---|---|
| Hash ×2 | 4 Dried Bud (one strain) + Ice | 0:30 |
| Moon Rock | Dried Bud + Hash + Honey Bottle | 0:45 |
| Space Brownie ×3 | 2 Dried Bud + Cocoa Beans + 2 Wheat + Sugar | 0:40 |
| Shroom Tea | 2 Magic Mushroom + Glass Bottle | 0:20 |
| Lucid Tab ×6 | Lab Solvent + 2 Magic Mushroom + Paper | 1:00 |
| Blue Crystal ×4 | Lab Solvent + Catalyst + 4 Lapis + 2 Sugar | 1:30 |
| Pixie Dust ×4 | Lab Solvent + 4 Glowstone Dust + 2 Sugar | 1:00 |

### Built-in strains

| Strain | Type | THC | Effects | Found wild in |
|---|---|---|---|---|
| OG Kush | Hybrid | 20% | Giggles, Munchies, Focus | forests, plains |
| Blue Dream | Hybrid | 21% | Euphoria, Creative, Floaty | meadows, rivers, beaches |
| Jungle Haze | Sativa | 22% | Energy, Creative, Giggles | jungles |
| Savanna Gold | Sativa | 20% | Euphoria, Energy, Focus | savanna, desert, badlands |
| Northern Lights | Indica | 24% | Couch Lock, Sleepy, Pain Relief | taiga, snowy biomes |
| Purple Kush | Indica | 26% | Couch Lock, Munchies, Euphoria | dark forest, windswept hills |
| Swamp Skunk | Hybrid | 18% | Munchies, Paranoia, Sleepy | swamps |
| Cherry Pie | Hybrid | 20% | Euphoria, Floaty, Giggles | cherry groves |
| Sour Diesel | Sativa | 25% | Energy, Focus, Paranoia | dealer only |
| White Widow | Hybrid | 27% | Euphoria, Creative, Pain Relief | ice spikes, frozen peaks |

## Commands & permissions

Players only ever *need* items and machines. The command is optional:

| Command | Permission | |
|---|---|---|
| `/kush guide` | `kushcraft.use` | open the handbook |
| `/kush pack` | `kushcraft.use` | re-send the texture pack |
| `/kush balance` | `kushcraft.use` | show your money |
| `/kush strains` | `kushcraft.use` | list all strains |
| `/kush give <player> <item> [amount] [strain] [quality]` | `kushcraft.admin` | |
| `/kush money <player> <amount>` | `kushcraft.admin` | set a balance |
| `/kush shop` | `kushcraft.admin` | open the dealer anywhere |
| `/kush reload` | `kushcraft.admin` | reload config, strains, shop |
| `/kush selftest` | console | tests plants, machines and items on a test world |

`kushcraft.strainmaker` (default: everyone) controls who may create strains.

## Config highlights (`config.yml`)

* `resource-pack.*`: port, host, external URL, required.
* `growth.*`: minutes to grow, minimum light, lamp radius, protection checks (works with WorldGuard, GriefPrevention and similar).
* `wild.*`: seed and spore drop chances.
* `strain-maker.*`: cost, seeds given, max strains per player.
* `economy.*`: Vault, starting balance, currency symbol.
* `shop.buy` / `shop.sell`: everything the dealer sells and pays.

## Building from source

```bash
cd kushcraft
mvn package                       # needs JDK 25; jar ends up in target/
python3 tools/gen_assets.py       # (optional) redraws all textures/models (needs Pillow)
python3 tools/validate_pack.py    # checks the resource pack
```

All textures, models and GUI backgrounds are generated by the Python scripts in
`tools/`. Edit the pixel art in `tools/sprites.py` and run `gen_assets.py`.
GitHub Actions builds the jar against the real Paper 26.3 API, then boots a
real Paper 26.3 server with the plugin and runs `/kush selftest`.
