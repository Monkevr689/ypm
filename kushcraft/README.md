# KushCraft 2.0 – grow, cook & deal (Paper 26.3)

A **server-side only** Paper plugin with custom plants, 22 drugs, strain breeding, a sales leaderboard, 36 achievements and a living economy. It all comes in **one jar**.
Players don't install any mods. The plugin builds its own resource pack (32px art, 3D plants and blocks, menu art and recipe pictures) and sends it to everyone who joins.

**Download:** [`release/KushCraft-2.0.0.jar`](release/KushCraft-2.0.0.jar). Drop it in `plugins/` and restart.

| Items | Plants | Blocks |
|---|---|---|
| ![items](docs/items_preview.png) | ![plants](docs/plants3d_preview.png) | ![blocks](docs/blocks_preview.png) |

![menus](docs/gui_preview.png)

## The menu

Open it with **`/kush`**, **Shift + F**, or the **KushCraft Menu** book. There are five tabs, and your money sits in the corner:

| Tab | What's there |
|---|---|
| **Shop** | buy seeds and gear, hand in **daily orders**, sell product (click it, or **Sell all**) |
| **Drugs** | every product in one row per kind; click one to see its recipe picture |
| **Trade** | spend your money on diamonds, ores, blocks and lab supplies (you can sell product here too) |
| **Top** | the best sellers on a podium, plus your place |
| **Awards** | 36 achievements, greyed out until you unlock them |

The **Drug Lab** block works the same way: it opens on **Cook**, with **Roll**, **Dry** and **Mix** tabs and an **Upgrade** button. Recipes you have everything for glow. One click cooks, using ingredients straight from your inventory.

## Getting started

1. Break grass for seeds (the biome decides the strain). Jungle grass gives coca, red poppies give poppy seeds, desert dead bushes give peyote and small mushrooms give spores. You can also buy all of them in the Shop.
2. Plant them, wait, then click the plant to harvest.
3. Craft a **Drug Lab** (4 iron, a glass bottle and a crafting table), then dry, roll and cook.
4. Sell your product. Sell the most to become the **Cartel Boss**.

## Drugs (Drug Lab > Cook)

Every recipe needs **1 to 3** things, and everyone can cook everything.

| Weed | Psychedelics | Uppers | Downers |
|---|---|---|---|
| Hash, Moon Rock, **Wax**, **Vape Pen**, Space Brownie, THC Gummies | Shroom Tea, LSD, Mescaline, DMT | Cocaine, Crack, Meth, Ecstasy, Pixie Dust, Angel Dust | Opium, Heroin, Lean, Ketamine |

Joints and blunts come from Roll; magic mushrooms and peyote buttons are eaten raw. Here's every recipe:

![recipes](docs/recipes_preview.png)

## Dealer ranks: whoever sells the most

Titles go to the top of the sales leaderboard (lifetime Shop sales plus orders) and change hands the moment someone sells more:

| Place | Title | Bonus on every sale |
|---|---|---|
| #1 | Cartel Boss | +15% |
| #2 | Kingpin | +12% |
| #3 | The Plug | +10% |
| top 5 | Supplier | +7% |
| top 10 | Hustler | +5% |
| top 25 | Dealer | +2% |
| everyone else | Street Seller | – |

Titles show in the tab list, and a new title is announced to the server.

## Economy

* **Cheap to start:** seeds $40–110, a Drug Lab $900 (or craft one).
* **Product pays well**, e.g. dried bud $15, cocaine $80, heroin $110, a vape pen $320. Selling lots of one thing lowers its price for a while, and one **hot item** pays +50%.
* **Resources are expensive.** In **Trade** a diamond costs $1,500, an iron ingot $80 and netherite $40,000, so you have to sell a lot of product to buy them. Resources sell back for 20%.
* **Daily orders** pay extra for big batches. Lab upgrades (levels 2–5, $2,500 to $50,000) cook faster and give bonus items. Jobs (mining, farming, hunting, growing) pay a little on the side.
* Built-in wallet, or **Vault** (EssentialsX, CMI…) if installed. Every number is in `config.yml`.

## Achievements

There are 36 awards, each with a cash reward: first harvest, first cook, 1,000 harvests, cook every recipe, breed a Legendary strain, sell $1,000,000, become the #1 seller, try every drug and more. They're also real **advancements**: unlocking one pops the usual toast, and they have their own *KushCraft* tab in the advancements screen (L).

## Plants & breeding

* Plants are 3D, with fan leaves and colas. Bud colour comes from the strain, the leaves pick up a touch of it, and every plant is a slightly different size.
* **Drug Lab > Mix:** cross two seeds for $250. The child is random: effects from the parents, a 35% chance of a new mutation, random potency and a rarity from Common to Legendary. Keep it and name it, or try again.
* There are 28 effects, from Giggles and Munchies to Ghost, Loved Up, Visions, Rage and Bad Trip. A boss bar shows how high you are.

## Installing

1. **Paper 26.3** (Java 25). Put the jar in `plugins/` and start the server.
2. Players need the texture pack. KushCraft hosts it on **port 8163**. Open that port, **or** (most game hosts, e.g. Shockbyte) use the hosted copy:
   ```yaml
   # plugins/KushCraft/config.yml
   resource-pack:
     url: 'https://raw.githubusercontent.com/Monkevr689/ypm/claude/inspiring-keller-65lzp9/kushcraft/release/KushCraft-pack.zip'
   ```
3. Join and accept the pack.

**Updating from 1.x:** replace the jar and restart. `config.yml` upgrades itself to version 5:
* the new shop prices, leaderboard ranks and Trade list are written in;
* your `resource-pack.url` and other settings are kept.

## Commands (optional – everything is in the menu)

| Command | Permission | |
|---|---|---|
| `/kush` (`/k`) | `kushcraft.use` | the menu |
| `/kush shop` / `drugs` / `trade` / `top` / `awards` | `kushcraft.use` | open a tab |
| `/kush pay <player> <amount>` | `kushcraft.use` | send money |
| `/kush guide` / `pack` / `balance` | `kushcraft.use` | handbook, re-send the pack, your money |
| `/kush items` | `kushcraft.admin` | click any item to get it |
| `/kush give <player> <item> [amount] [strain] [quality]` | `kushcraft.admin` | |
| `/kush money <player> <amount>` / `sales <player> <amount>` | `kushcraft.admin` | set a balance / lifetime sales |
| `/kush reload` | `kushcraft.admin` | reload the config |
| `/kush selftest` | console | runs the built-in tests |

## Building from source

```bash
cd kushcraft
mvn package                       # JDK 25; jar in target/
python3 tools/gen_assets.py       # redraw all art, menus and recipe pictures (needs Pillow)
python3 tools/validate_pack.py    # checks the pack against the Java code
python3 tools/make_pack_zip.py    # release/KushCraft-pack.zip for hosting
```

GitHub Actions builds against the real Paper 26.3 API, boots a real Paper 26.3 server to run `/kush selftest` (including the advancement tab), and tests the hosted pack and the config upgrade.
