# KushCraft – grow, cook, roll & deal (Paper 26.3)

A **server-side only** Paper plugin: custom plants, strains, a Drug Lab, a
market with a living economy, and custom effects. It all comes in **one jar**.
Players don't install any mods. The plugin builds its own resource pack
(textures, 3D models and menu art) and sends it to everyone who joins.

**Download:** [`release/KushCraft-1.1.0.jar`](release/KushCraft-1.1.0.jar). Drop it in `plugins/` and restart.

| Items | Blocks | Plants |
|---|---|---|
| ![items](docs/items_preview.png) | ![blocks](docs/blocks_preview.png) | ![plants](docs/plants3d_preview.png) |

![menus](docs/gui_preview.png)

## Players: just type `/kush`

`/kush` (or right-clicking the **KushCraft Menu** item, which new players get) opens one menu with everything:

| Button | What it does |
|---|---|
| **Guide** | the handbook: how everything works |
| **Market** | buy seeds, supplies and blocks; sell your product |
| **Drug Catalog** | every product: how to make it, what it does, what it's worth right now |
| **Strains** | every strain; **mix your own** and **name** it; rename your strains |
| **Daily Orders** | hand in a batch (e.g. 8 cocaine) for ~75% more than the market |
| **Top Dealers** | the money leaderboard |
| **Your Status** | your high meter and active effects |
| **Texture Pack** | re-download the pack |
| *Admin* (ops only) | click any item to get it |

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
* **Market prices move:** every item you sell lowers that product's price a little, and it recovers over time, so selling a mix pays best.
* **Hot item:** one random product sells for +50% for an hour.
* **Daily Orders:** 3 open orders. The first player to hand one in gets the bonus, then a new order appears.
* Better ★ quality and stronger strains always sell for more.
* **Top Dealers** leaderboard in `/kush`.

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

Updating from 1.0: just replace the jar. `config.yml` is upgraded automatically: the new shop list and options are added, and your resource-pack settings are kept.

## Commands (optional)

| Command | Permission | |
|---|---|---|
| `/kush` | `kushcraft.use` | the main menu |
| `/kush guide` / `pack` / `balance` / `strains` | `kushcraft.use` | shortcuts |
| `/kush give <player> <item> [amount] [strain] [quality]` | `kushcraft.admin` | |
| `/kush money <player> <amount>` | `kushcraft.admin` | set a balance |
| `/kush shop` | `kushcraft.admin` | open the market anywhere |
| `/kush reload` | `kushcraft.admin` | reload config, strains, shop |
| `/kush selftest` | console | tests plants, blocks, items and the market on a test world |

`kushcraft.strainmaker` (default: everyone) controls who may create strains.

## Building from source

```bash
cd kushcraft
mvn package                       # JDK 25; jar in target/
python3 tools/gen_assets.py       # redraw textures/models/menus (needs Pillow)
python3 tools/validate_pack.py    # checks the pack (incl. atlas folders)
python3 tools/make_pack_zip.py    # release/KushCraft-pack.zip for hosting
```

GitHub Actions builds against the real Paper 26.3 API and boots a real Paper 26.3 server with the plugin to run `/kush selftest`.
