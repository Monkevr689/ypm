# KushCraft 4.0 – grow, cook & deal (Paper 26.3)

A **server-side only** Paper plugin with 34 unique strains, climates, custom plants, 22 drugs, 34 effects, strain breeding with very rare Mythic strains, **workers you hire to farm for you**, cartels, a sales leaderboard, 47 achievements, an admin panel and a living market. It all comes in **one jar**.
Players don't install any mods. The plugin builds its own resource pack (32px art, 3D plants and blocks, worker skins and hats, animated Mythic buds, menu art and recipe pictures) and sends it to everyone who joins.

**Download:** [`release/KushCraft-4.0.0.jar`](release/KushCraft-4.0.0.jar). Drop it in `plugins/` and restart.

| Items | Plants | Blocks |
|---|---|---|
| ![items](docs/items_preview.png) | ![plants](docs/plants3d_preview.png) | ![blocks](docs/blocks_preview.png) |

![strains](docs/strains_preview.png)

![menus](docs/gui_preview.png)

![workers](docs/workers_preview.png)

## The menu

Open it with **`/kush`**, **Shift + F**, or the **KushCraft Menu** book. There are five panels. In the top bar next to them are the glowing **Next** button (your next step, see *Getting started*) and your money. Admins also get a red **Admin** button.

| Panel | What's there |
|---|---|
| **Shop** | seeds of every strain (rarer strains cost more), **Gear & Workers**, the market news and selling (click product, or **Sell all**) |
| **Drugs** | every product in one row per kind; click one to see its recipe picture |
| **Trade** | 7 shelves of resources: ores, farming, wood, building blocks, mob drops, nether & end, lab & tools |
| **Cartel** | your cartel (bank, level, members), **contracts**, the cartel **shipment** and **Top Dealers** |
| **Awards** | 47 achievements on two pages, greyed out until you unlock them |

The **Drug Lab** block works the same way: it opens on **Cook**, with **Roll**, **Dry** and **Mix** tabs and an **Upgrade** button. Recipes you have everything for glow. One click cooks, using ingredients straight from your inventory.

## Getting started

New players get the menu book and a small starter kit (2 OG Kush seeds and fertilizer). The glowing **Next** button in the menu always shows the next step, and each step pays a reward:

1. **Plant a seed**: buy one in the Shop, or break grass (the biome decides the strain). Jungle grass gives coca, red poppies give poppy seeds, desert dead bushes give peyote, small mushrooms give spores and ripe wheat gives **ergot**.
2. **Harvest it**: right-click the plant when it's fully grown. Sneak + right-click harvests all your ripe plants around it. You get a message when plants are ready.
3. **Get a Drug Lab**: Shop > Gear & Workers, or craft one.
4. **Dry your buds**: Drug Lab > Dry (30 seconds).
5. **Roll or cook** something.
6. **Sell** your product.
7. **Hire a worker**.

`/kush start` shows all the steps.

## Workers

Shop > **Gear & Workers** has a hiring board. Buy a worker, then right-click the ground where they should work. They walk to the job, work and walk back.

| Worker | Price | What they do |
|---|---|---|
| **Farmhand** | $2,500 | harvests your ripe plants around them and plants a seed from the harvest again; plants seeds and uses fertilizer from their satchel on empty farmland and your Planters |
| **Dryer** | $2,000 | stands near your Drug Lab: hangs fresh buds on its racks, takes them off when dry, and fetches fresh buds from your Farmhands |

* Every job costs a small **wage** from your wallet ($3 for a Farmhand, $2 for a Dryer). No money, no work.
* **Right-click** a worker for their menu: the satchel (click an item to take it, click your own seeds or buds to give them), rename, pause, **train** (level 2 and 3: they work further away, rest less and walk faster) and dismiss (you get their contract and satchel back).
* Up to 4 workers each. They only touch the plants and labs of the player who hired them. `/kush workers` lists yours.

## Strains & climates

There are 34 strains and every one looks different: its own bud colour, leaf colour, hair colour and bud shape (**classic**, **foxtail**, **popcorn** or **spear**), plus its own effects, flavour and climate. Seeds cost about $20 (Swamp Skunk) up to $300 (Dragon Fruit). New in 4.0: Mango Kush, Green Crack, Red Congolese, Durban Poison, Blueberry, Wedding Cake, Girl Scout Cookies, Ice Cream Cake, and Solar Flare (Mythic, golden, found in deserts).

Every strain loves one of six climates (shown on its seeds):

| Climate | Where |
|---|---|
| Tropical | jungles |
| Desert | deserts, savannas, badlands, the Nether |
| Temperate | plains, forests |
| Wetland | swamps, rivers, beaches |
| Cold | snow, taiga, ice |
| Mountain | hills, meadows, cherry groves, anything above y 100 |

In its own climate a plant grows **50% faster**, gets **+1 ★** and **+1 bud**. In the opposite climate it grows at half speed with −1 ★ (a **Grow Lamp** nearby fixes that, like a greenhouse).

### Breeding & Mythic strains

**Drug Lab > Mix:** cross two seeds for $150. The child is random: effects, potency, climate, colours and bud shape come from the parents, or mutate into something new.

About **1 in 70** crosses is **Mythic**: animated **rainbow**, **galaxy**, **golden**, **crystal**, **neon** or **inferno** buds. Mythic plants sparkle (some glow in the dark) and Mythic product sells for 2.5×. A Mythic parent passes its look on about 1 in 5. Three Mythic strains, **Aurora Kush**, **Rainbow Runtz** and **Solar Flare**, can very rarely be found in the wild.

### Effects

Strains carry up to 4 of 27 strain effects (giggles, munchies, couch lock, energy, focus, night owl, fireproof, ghost, rage...), and the hard drugs have their own. New in 4.0:

| Effect | What it does |
|---|---|
| Green Thumb | plants within 10 blocks grow 25% faster |
| Smooth Talker | the Shop pays you 10% more |
| Frosty | water freezes under your feet |
| Magnetic | items nearby fly to you |
| Sixth Sense | mobs nearby glow through walls |
| Zen | stand still to heal and shrug off hits |

## Drugs (Drug Lab > Cook)

Every recipe needs **1 to 3** kinds of things and takes 15–60 seconds. Everyone can cook everything. **Shift-click** a recipe to cook up to 4 batches in one go.

| Weed | Psychedelics | Uppers | Downers |
|---|---|---|---|
| Hash, Moon Rock, Wax, Vape Pen, Space Brownie, THC Gummies | Shroom Tea, **Ergot → LSD**, Mescaline, DMT | Cocaine, Crack, Meth, Ecstasy, Pixie Dust, Angel Dust | Opium, Heroin, Lean, Ketamine |

**LSD:** cook 4 wheat into ergot (or find ergot when you harvest ripe wheat), then ergot + paper + solvent = 4 tabs.
**Drying:** every Drug Lab has **5 drying racks**; buds are dry in **30 seconds**.

Joints and blunts come from Roll; magic mushrooms and peyote buttons are eaten raw. Here's every recipe:

![recipes](docs/recipes_preview.png)

## Cartels

Start a cartel for $2,500 (Cartel panel), invite players from the members list, or `/kush cartel invite <player>`.

* **Bank:** every sale a member makes adds **5%** on top into the bank (nobody pays it), and contracts add 10% of their reward. Members put money in; the boss takes it out.
* **Levels:** the boss spends the bank on levels. Every member gets the bonus:

| Level | Cost | Members | Sales | Growth | Lab speed |
|---|---|---|---|---|---|
| Crew | – | 4 | – | – | – |
| Gang | $10,000 | 6 | +3% | +5% | – |
| Syndicate | $40,000 | 8 | +6% | +10% | +5% |
| Cartel | $120,000 | 12 | +9% | +15% | +10% |
| Empire | $350,000 | 16 | +12% | +20% | +15% |

* **Shipments:** each cartel gets a big order, e.g. 75 cocaine within 24 hours. Every member can deliver part of it and gets paid the normal price for it; when it's full the bank gets a big bonus.
* **Contracts:** three batch orders anyone can fill for bonus cash.
* **Top Dealers:** the best players and the best cartels (switch with the button in the corner).

## Dealer titles: whoever sells the most

| Place | Title | Bonus on every sale |
|---|---|---|
| #1 | Cartel Boss | +15% |
| #2 | Kingpin | +12% |
| #3 | The Plug | +10% |
| top 5 | Supplier | +7% |
| top 10 | Hustler | +5% |
| top 25 | Dealer | +2% |
| everyone else | Street Seller | – |

Titles and cartels show in the tab list.

## Economy

* **Getting started costs a bit:** seeds about $20–300 by strain, papers 8 for $8, solvent 8 for $18, a Drug Lab $650 (or craft one: crafting table, furnace, 3 iron, 2 bottles). Lab upgrades $2,500 to $50,000, mixing $250.
* **Product pays well**, e.g. dried bud $15, cocaine $80, heroin $110, a vape pen $320. Rarer and stronger strains sell for more (Mythic 2.5×).
* **The market fights back:** every item you sell lowers the price of the next one, even within one big sale, and prices take a while to recover. Dumping 200 of one drug pays a lot less than 200 × today's price, so sell a mix. The **Market news** button in the Shop shows the hot item (+50%) and what's flooded. Admins can start a **market boom** (everything +50% for 30 minutes).
* **Resources are expensive.** Trade has 187 resources on 7 shelves: a diamond costs $1,500, an iron ingot $80, an elytra $250,000. Resources sell back for 20%, and prices move as people buy and sell.
* Jobs (mining, farming, hunting, growing) pay a little on the side.
* Built-in wallet, or **Vault** (EssentialsX, CMI…) if installed. Every number is in `config.yml`.

## Achievements

There are 47 awards, each with a cash reward: grow in every climate, fill all 5 drying racks, cook LSD, hire a worker, run 4 workers, deliver a cartel shipment, build an Empire, breed a Mythic strain, sell $1,000,000, become the #1 seller and more. They're also real **advancements**: unlocking one pops the usual toast, and they have their own *KushCraft* tab in the advancements screen (L).

## Installing

1. **Paper 26.3** (Java 25). Put the jar in `plugins/` and start the server.
2. Players need the texture pack. KushCraft hosts it on **port 8163**. Open that port, **or** (most game hosts, e.g. Shockbyte) use the hosted copy:
   ```yaml
   # plugins/KushCraft/config.yml
   resource-pack:
     url: 'https://raw.githubusercontent.com/Monkevr689/ypm/claude/inspiring-keller-65lzp9/kushcraft/release/KushCraft-pack.zip'
   ```
3. Join and accept the pack.

**Updating from 3.x (or older):** replace the jar and restart.
* `config.yml` upgrades itself to version 7: **the shop prices** (now with the workers), lab upgrade and mixing costs and the market settings are **replaced** with the 4.0 ones, and the `workers`, `harvest` and `new-players` sections are added. Your `resource-pack.url`, Trade shelves, cartels and other settings are kept.
* `strains.yml` gets the 9 new strains; strains your players bred are kept.

## Admin panel

Admins (op or `kushcraft.admin`) get a red **Admin** button in the menu's top bar (or `/kush admin`):

* **Players & items:** give any item, seeds or buds of any strain (bred ones too), manage players (add or take money, set lifetime sales, starter kit, sober up, teleport), see every worker, level up, fund or disband cartels.
* **Around you:** grow every plant within 16 blocks, finish every Drug Lab (cooking and drying), sober up, give yourself the starter kit.
* **Market:** new hot item, reset prices, new contracts, start or stop a market boom, new cartel shipments.
* **Server:** reload the config, resend the pack to everyone, turn workers on or off, server stats.

## Commands (optional – everything is in the menu)

| Command | Permission | |
|---|---|---|
| `/kush` (`/k`) | `kushcraft.use` | the menu |
| `/kush shop` / `gear` / `drugs` / `trade` / `cartel` / `top` / `awards` | `kushcraft.use` | open a panel |
| `/kush start` | `kushcraft.use` | getting started: your next steps |
| `/kush sell` | `kushcraft.use` | sell all your product |
| `/kush workers` | `kushcraft.use` | your workers, from anywhere |
| `/kush cartel invite <player>` / `join <cartel>` / `leave` | `kushcraft.use` | cartels |
| `/kush pay <player> <amount>` | `kushcraft.use` | send money |
| `/kush guide` / `pack` / `balance` / `strains` | `kushcraft.use` | handbook, re-send the pack, your money, all strains |
| `/kush admin` | `kushcraft.admin` | the **admin panel** (also the red button in the menu) |
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

GitHub Actions builds against the real Paper 26.3 API, boots a real Paper 26.3 server to run `/kush selftest` (including the advancement tab), and tests the hosted pack and the upgrade from 2.0.
