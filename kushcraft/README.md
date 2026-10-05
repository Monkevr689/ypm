# KushCraft – grow, cook, breed & deal (Paper 26.3)

A **server-side only** Paper plugin with custom plants, 20+ drugs, random strain breeding, dealer ranks, a living economy and 28 custom effects. It all comes in **one jar**.
Players don't install any mods. The plugin builds its own resource pack (textures, 3D models, menu art and recipe pictures) and sends it to everyone who joins.

**Download:** [`release/KushCraft-1.3.0.jar`](release/KushCraft-1.3.0.jar). Drop it in `plugins/` and restart.

| Items | Blocks | Plants |
|---|---|---|
| ![items](docs/items_preview.png) | ![blocks](docs/blocks_preview.png) | ![plants](docs/plants3d_preview.png) |

![menus](docs/gui_preview.png)

## Open the menu

Use **`/kush`** (or `/k`), press **Shift + F**, or right-click the **KushCraft Menu** book (`/kush` gives you one).

Every page has the same **tab bar** on top, so you can reach anything in one click:

| Tab | What's there |
|---|---|
| **Home** | your rank and progress, how high you are, the hot item, what you unlock next, **Daily Orders** |
| **Drugs** | every product by category (Weed, Psychedelics, Uppers, Downers, Seeds, Gear): effects, price and rank needed. Click one to see its recipe. |
| **Shop** | buy seeds, supplies and blocks; click your product to sell it, or press **Sell all** |
| **Breed** | every strain (yours first), rename yours, and the **Mix** button |
| **Jobs** | what each job pays and what you earned this hour |
| **Trade** | buy and sell vanilla resources: ores, blocks, wood, food, mob drops… |
| **Bank** | balance, send money, all ranks, top dealers |
| Book / Cash | the handbook (with a picture of every recipe) / your balance |

## Getting started

1. Break grass for seeds (the biome decides the strain). Jungle grass gives coca, red poppies give poppy seeds, desert dead bushes give peyote, small mushrooms give spores.
2. Plant them on farmland (peyote also grows on sand). Wait, then click the plant to harvest it.
3. Craft a **Drug Lab**. It has four tabs: **Cook**, **Roll**, **Dry** and **Mix**, plus an **Upgrade** button.
4. Sell at the **Shop**. Selling ranks you up, and ranks unlock the harder drugs.

## Drugs (Drug Lab > Cook)

| Rank | Drugs |
|---|---|
| 1 Street Seller | Hash, Space Brownie, THC Gummies, Shroom Tea, **Opium**, **Lean** |
| 2 Corner Dealer | Moon Rock, LSD, **Ecstasy**, Pixie Dust |
| 3 Hustler | Cocaine, **Crack**, **Ketamine**, **Mescaline** |
| 4 Supplier | Meth, Heroin, **DMT** |
| 5 The Plug | **Angel Dust** |

Also: joints and blunts (Roll), magic mushrooms and peyote buttons (eaten raw). Every recipe, with ingredients, is under **Drugs**, in the handbook and here:

![recipes](docs/recipes_preview.png)

## Breeding

**Breed > Mix** (or the Drug Lab's Mix tab):
1. Click two seeds and press **MIX** ($1,500 plus one seed of each).
2. The child is **random**:
   * each parent effect has a 55% chance to be passed on (90% if both parents have it);
   * there's a 35% chance of a **mutation**, a brand-new effect;
   * potency lands around the parents' average, with a rare jackpot.
3. Its **rarity** (Common → Legendary) comes from potency and effect count. Rarer strains sell for up to 50% more.
4. Keep it and name it, or throw it away and try again.

## Effects (28)

* **Strain effects:** Giggles, Munchies, Couch Lock, Energy Rush, Euphoria, Creative Flow, Floaty, Paranoia, Sleepy, Focus, Pain Relief, Trippy, Lucky (double ore drops), Night Owl, Aquatic, Fireproof, Ghost, Loved Up (heals people near you), Visions, Rage, Dizzy.
* **Drug-only effects:** Hyper, Glow, Crash, Dissociated, Syrupy, Bad Trip, Greened Out.

A boss bar shows your high. At 100% you green out, and taking psychedelics while very high can give you a bad trip.

## Economy: you have to sell a lot

Things are expensive:

| Item | Price |
|---|---|
| Drug Lab | $10,000 |
| Grow Lamp | $3,000 |
| Seeds | $300–900 |
| Breeding (per mix) | $1,500 |

Product is worth a few dollars to ~$75 each. Selling lots of one product lowers its price for a while, so sell a mix.

* **Dealer ranks:** everything you sell (at the Shop and through orders) counts.

  | Rank | Sold | Sale bonus |
  |---|---|---|
  | Street Seller | $0 | 0% |
  | Corner Dealer | $2,500 | +5% |
  | Hustler | $15,000 | +10% |
  | Supplier | $60,000 | +15% |
  | The Plug | $200,000 | +20% |
  | Kingpin | $600,000 | +25% |
  | Cartel Boss | $2,000,000 | +30% |

  Ranks unlock recipes and pay that bonus on every sale. A rank-up is announced to the server.
* **Lab upgrades:** level 2–5 cost $5,000, $15,000, $40,000 and $100,000. Each level cooks 15% faster and adds an 8% chance of a bonus item. A picked-up lab keeps its level.
* **Daily Orders** (on Home) ask for big batches and pay about 60% extra. One **hot item** pays +50% for an hour.
* **Trade:**
  * buy 124 vanilla items (prices × 2.5);
  * sell them back for 30% of the buy price.
* **Jobs:** you're paid for natural ores, grown crops, logs, monsters and KushCraft harvests, with an hourly cap.
* Built-in wallet, or **Vault** (EssentialsX, CMI…) if installed. Every number above is in `config.yml`.

## Installing

1. **Paper 26.3** (Java 25). Put the jar in `plugins/` and start the server.
2. Players need the texture pack. KushCraft hosts it on **port 8163**. Open that port, **or** (most game hosts, e.g. Shockbyte) use a hosted copy:
   ```yaml
   # plugins/KushCraft/config.yml
   resource-pack:
     url: 'https://raw.githubusercontent.com/Monkevr689/ypm/claude/inspiring-keller-65lzp9/kushcraft/release/KushCraft-pack.zip'
   ```
   The console should say `Resource pack: downloaded ... (same as this plugin's pack)`.
3. Join and accept the pack.

**Updating from 1.x:** replace the jar and restart. `config.yml` upgrades itself to version 4:
* the shop lists and prices are replaced by the new, higher ones;
* ranks, lab upgrades and the new options are added;
* your `resource-pack.url` and other settings are kept.

## Commands (optional – everything is in the menu)

| Command | Permission | |
|---|---|---|
| `/kush` (`/k`) | `kushcraft.use` | the menu (+ the menu book the first time) |
| `/kush shop` / `drugs` / `breed` / `jobs` / `trade` / `bank` | `kushcraft.use` | open a tab directly |
| `/kush pay <player> <amount>` | `kushcraft.use` | send money |
| `/kush guide` / `pack` / `balance` | `kushcraft.use` | handbook, re-send the pack, your money |
| `/kush give <player> <item> [amount] [strain] [quality]` | `kushcraft.admin` | |
| `/kush money <player> <amount>` | `kushcraft.admin` | set a balance |
| `/kush sales <player> <amount>` | `kushcraft.admin` | set lifetime sales (and so the rank) |
| `/kush reload` | `kushcraft.admin` | reload config, strains, shop, ranks, trade, jobs |
| `/kush selftest` | console | tests plants, blocks, items, recipes, ranks, breeding, trade and jobs |

Ops can cook every recipe regardless of rank, and get a "give items" button on Home. `kushcraft.strainmaker` (default: everyone) controls breeding. Shift+F can be turned off with `menu.shift-f: false`.

## Building from source

```bash
cd kushcraft
mvn package                       # JDK 25; jar in target/
python3 tools/gen_assets.py       # redraw textures/models/menus/recipe pictures (needs Pillow)
python3 tools/validate_pack.py    # checks the pack against the Java code
python3 tools/make_pack_zip.py    # release/KushCraft-pack.zip for hosting
```

GitHub Actions builds against the real Paper 26.3 API, boots a real Paper 26.3 server to run `/kush selftest`, and tests the hosted-pack and config-upgrade paths.
