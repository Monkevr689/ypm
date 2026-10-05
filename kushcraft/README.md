# KushCraft – grow, cook, roll & deal (Paper 26.3)

A **server-side only** Paper plugin: custom plants, strains, a Drug Lab, a
market with a living economy, and custom effects. It all comes in **one jar**.
Players don't install any mods. The plugin builds its own resource pack
(textures, 3D models and menu art) and sends it to everyone who joins.

**Download:** [`release/KushCraft-1.2.0.jar`](release/KushCraft-1.2.0.jar). Drop it in `plugins/` and restart.

| Items | Blocks | Plants |
|---|---|---|
| ![items](docs/items_preview.png) | ![blocks](docs/blocks_preview.png) | ![plants](docs/plants3d_preview.png) |

![menus](docs/gui_preview.png)

## Players: just type `/kush` (or press Shift+F)

Three ways to open the menu, from anywhere:
* type **`/kush`** (or `/k`). The first time, this also gives you the **KushCraft Menu** book.
* press **Shift + F** (sneak + swap hands).
* right-click the **KushCraft Menu** book.

The menu has four colour-coded rows:

| Row | Buttons |
|---|---|
| **Learn** (green) | **Guide** (handbook with recipe pictures) · **Recipes** (every recipe in a crafting grid) · **Drug Catalog** |
| **Trade** (gold) | **Market** (seeds, supplies, sell drugs) · **Exchange** (ores, food, wood, blocks...) · **Daily Orders** |
| **Earn** (orange) | **Jobs** (paid work) · **Send Money** · **Top Dealers** |
| **You** (blue) | **Strains** (mix & name your own) · **Your Status** · **Texture Pack** |

Admins also get a crown button that gives any item. Every page has a Back button.

## Recipes (with pictures)

Every recipe is in `/kush` > **Recipes**, shown in a crafting grid with the real items. Click an item in the **Drug Catalog** to jump to its recipe. The **Guide** book has a clickable contents page and one picture page per recipe:

![recipes](docs/recipes_preview.png)

## The 4 blocks

| Block | Use |
|---|---|
| **Drug Lab** | one station for everything: **Cook**, **Roll** (joints/blunts), **Dry** (fresh → dried buds) and **Mix** (new strains) |
| **Planter** | perfect soil: faster growth, +1 ★ quality |
| **Grow Lamp** | light + growth boost so you can grow indoors or underground |
| **Dealer Stand** | the market as a block (needed only if `market.anywhere: false`) |

Placed Strain Makers, Rolling Tables and Drying Racks from v1.0 still work, but they're no longer sold or crafted.

## Plants & drugs

| Plant | Seeds from | Likes | Harvest → product |
|---|---|---|---|
| Cannabis (10 strains + yours) | grass/ferns (biome decides the strain), Market | sativa: warm, indica: cold, hybrid: mild | fresh buds → dry → joints, blunts, hash, moon rock, brownies |
| Coca bush | jungle/savanna grass, Market | warm | coca leaves → **Cocaine** |
| Opium poppy | red poppy flowers, Market | mild | poppy pods → **Heroin** |
| Magic mushrooms | small mushrooms, Market | darkness, mycelium | mushrooms → shroom tea, **LSD** |

**Drug Lab > Cook** (ingredients come from your inventory):

| Product | Ingredients | Effects |
|---|---|---|
| Hash ×2 | 4 dried bud + ice | strong strain effects (bong) |
| Moon Rock | bud + hash + honey bottle | very strong strain effects (bong) |
| Space Brownie ×3 | 2 bud + cocoa + 2 wheat + sugar | long strain effects + Munchies |
| Shroom Tea | 2 magic mushrooms + bottle | Trippy, Euphoria |
| **Cocaine ×4** | 8 coca leaves + lab solvent + sugar | Energy Rush, Focus, Paranoia |
| **Heroin ×3** | 6 poppy pods + lab solvent + catalyst | Euphoria, Couch Lock, Pain Relief, Sleepy (very strong) |
| **LSD ×6** | lab solvent + 2 magic mushrooms + paper | Trippy, Creative Flow, Focus |
| **Meth ×4** | lab solvent + catalyst + 4 lapis + 2 sugar | Hyper, then a Crash |
| Pixie Dust ×4 | lab solvent + 4 glowstone dust + 2 sugar | Glow, Floaty, Euphoria |

The full list with prices is in-game under `/kush` > Drug Catalog. A boss bar shows your "high"; reach 100% and you **green out**.

## Economy

* Built-in wallet (saved in `balances.yml`), or **Vault** (EssentialsX, CMI, …) if installed.
* **Market:** sell your drugs. Every item you sell lowers that product's price a little, and it recovers over time, so selling a mix pays best. One **hot item** sells for +50% for an hour.
* **Daily Orders:** 3 open orders. The first player to hand one in gets the bonus (~75% extra), then a new order appears.
* **Exchange:** trade money for **ores, gems, building blocks, wood, food, mob drops, Nether/End items** and more, and sell them back. There are 124 items in 7 tabs, all editable in `config.yml`.
  * **Fair prices:** rare things cost more (a diamond is $120, cobblestone is 25¢).
  * You sell for half the buy price, so nothing can be bought and sold back for a profit.
  * Prices move a little with trading and drift back to normal.
* **Jobs:** no need to join, you just get paid for normal work. Placed blocks don't pay, crops must be fully grown, spawner mobs don't count, and there's an hourly cap ($1500 by default).

  | Job | Pays for | Example |
  |---|---|---|
  | Miner | natural ores | diamond ore $15, iron ore $1 |
  | Farmer | fully grown crops | wheat 15¢, melon 30¢ |
  | Woodcutter | logs | 20¢ each |
  | Hunter | monsters | zombie $1, enderman $2, warden $100 |
  | Grower | KushCraft harvests | $3 per plant |
* **Send Money:** `/kush` > Send $, or `/kush pay <player> <amount>`.
* **Top Dealers** leaderboard.

## Installing

1. **Paper 26.3** (Java 25). Put the jar in `plugins/` and start the server.
2. Players need the texture pack. KushCraft hosts it on **port 8163**. Open that port, **or** (most game hosts, e.g. Shockbyte) use a hosted copy:
   ```yaml
   # plugins/KushCraft/config.yml
   resource-pack:
     url: 'https://raw.githubusercontent.com/Monkevr689/ypm/claude/inspiring-keller-65lzp9/kushcraft/release/KushCraft-pack.zip'
   ```
   The plugin downloads that file on start-up and checks it. The console should say `Resource pack: downloaded ... (same as this plugin's pack)`. If it warns that the zip is *not* this version's pack, re-upload `plugins/KushCraft/KushCraft-pack.zip`.
3. Join and accept the pack.

Updating from 1.0 or 1.1: just replace the jar. `config.yml` is upgraded automatically: new options (exchange, jobs, menu) are added, and your own settings, including `resource-pack.url`, are kept.

## Commands (optional)

| Command | Permission | |
|---|---|---|
| `/kush` (`/k`) | `kushcraft.use` | the menu (+ the menu book the first time) |
| `/kush market` / `exchange` / `jobs` / `recipes` / `catalog` / `orders` / `top` / `strains` | `kushcraft.use` | open a page directly |
| `/kush pay <player> <amount>` | `kushcraft.use` | send money |
| `/kush guide` / `pack` / `balance` | `kushcraft.use` | handbook, re-send the pack, your money |
| `/kush give <player> <item> [amount] [strain] [quality]` | `kushcraft.admin` | |
| `/kush money <player> <amount>` | `kushcraft.admin` | set a balance |
| `/kush shop` | `kushcraft.admin` | open the market anywhere |
| `/kush reload` | `kushcraft.admin` | reload config, strains, shop, exchange, jobs |
| `/kush selftest` | console | tests plants, blocks, items, recipes, market, exchange and jobs on a test world |

`kushcraft.strainmaker` (default: everyone) controls who may create strains. Turn the Shift+F hotkey off with `menu.shift-f: false`.

## Building from source

```bash
cd kushcraft
mvn package                       # JDK 25; jar in target/
python3 tools/gen_assets.py       # redraw textures/models/menus/recipe pictures (needs Pillow)
python3 tools/validate_pack.py    # checks the pack (incl. atlas folders)
python3 tools/make_pack_zip.py    # release/KushCraft-pack.zip for hosting
```

GitHub Actions builds against the real Paper 26.3 API and boots a real Paper 26.3 server with the plugin to run `/kush selftest`.
