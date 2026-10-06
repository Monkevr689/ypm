# KushCraft 5.0 – grow, cook & deal (Paper 26.3)

A **server-side only** Paper plugin with 34 unique strains, climates, custom plants that also **grow wild**, 22 drugs made **step by step**, 34 effects, strain breeding with very rare Mythic strains, **workers you hire** (Farmhand, Dryer and Cook), simple cartels, a sales leaderboard, 50 achievements, an admin panel and a living market. It all comes in **one jar**.
Players don't install any mods. The plugin builds its own resource pack (32px art, 3D plants and blocks, worker skins and hats, animated Mythic buds, menu art and recipe pictures) and every player gets it when they join.

**Download:** [`release/KushCraft-5.0.0.jar`](release/KushCraft-5.0.0.jar). Drop it in `plugins/` and restart. Players get the textures automatically.

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
| **Shop** | seeds of every strain, **Gear & Workers**, the **Market** (contracts and flooded products) and selling (click product, or **Sell all**) |
| **Drugs** | every product in one row per kind; click one to see its recipe picture |
| **Trade** | 7 shelves of resources: ores, farming, wood, building blocks, mob drops, nether & end, lab & tools |
| **Cartel** | your cartel (bank, level, members), how cartels work, the cartel **shipment** and **Top Dealers** |
| **Awards** | 50 achievements on two pages, greyed out until you unlock them |

The **Drug Lab** block opens on **Cook**, with **Roll**, **Dry** and **Mix** tabs and an **Upgrade** button. Recipes you have everything for glow; a red line under a recipe says what's missing **and where to get it**.

## Getting started

New players get the menu book and a small starter kit (2 OG Kush seeds and fertilizer). The glowing **Next** button always shows the next step, and each step pays a reward:

1. **Plant a seed**: buy one in the Shop, or break grass (the biome decides the strain).
2. **Harvest it**: right-click the plant when it's fully grown. Sneak + right-click harvests all your ripe plants around it.
3. **Get a Drug Lab**: Shop > Gear & Workers, or craft one.
4. **Dry your buds**: Drug Lab > Dry (30 seconds).
5. **Roll or cook** something.
6. **Sell** your product.
7. **Pick a wild plant**: they grow out in the world.

`/kush start` shows all the steps.

## Textures for everyone

By default (`resource-pack.url: auto`) players download the pack of **this exact KushCraft version** from GitHub, so it works on every host, including Shockbyte and other hosts that only open the game port. The plugin checks the file's hash itself and checks it again every 20 minutes. If a player's download fails, it checks the link and sends the pack once more. Players who said no get a clickable message, or can type `/kush pack`.

## Workers

Shop > **Gear & Workers** has a hiring board. Buy a worker, then right-click the ground where they should work. They walk to the job, work and walk back. Workers are an investment:

| Worker | Price | Wage | What they do |
|---|---|---|---|
| **Farmhand** | $12,000 | $5 a job | harvests your ripe plants around them and replants; plants seeds and uses fertilizer from their satchel |
| **Dryer** | $9,000 | $4 a job | stands near your Drug Lab: hangs fresh buds on its racks, takes them off dry, fetches fresh buds from your Farmhands |
| **Cook** | $15,000 | $8 a batch | **you pick the drug** (button in their menu); they cook it at your Drug Lab from the ingredients in their satchel, up to 4 batches at a time, and collect it when it's done. They fetch dried buds from your Dryers |

* No money, no work: wages come out of your wallet.
* **Right-click** a worker for their menu: the satchel (click an item to take it, click your own seeds, buds or ingredients to give them), their job, rename, pause, **train** (levels 2 and 3 cost $10,000 and $25,000: further, faster) and dismiss (you get their contract and satchel back).
* A full chain runs by itself: Farmhand → Dryer → Cook (e.g. buds → kief → hash).
* Up to 4 workers each. They only touch the plants and labs of the player who hired them. `/kush workers` lists yours.

## Wild plants

Plants grow by themselves out in the world, 20–56 blocks from players and never near anyone's farm. Cannabis is the strain of the biome, coca grows in jungles and savannas, poppies on plains, peyote in deserts and magic mushrooms in dark forests, swamps and mushroom fields. **Anyone can pick them** for buds and seeds. Unpicked ones wither after 6 hours (all of it is in `config.yml` under `wild`).

## Animals

Right-click an animal with a joint, an edible or any drug and its **eyes go red**. It's high for 90 seconds: slow and dopey on weed and downers, the zoomies on uppers, colours around its head on psychedelics.

## Strains & climates

There are 34 strains and every one looks different: its own bud colour, leaf colour, hair colour and bud shape (**classic**, **foxtail**, **popcorn** or **spear**), plus its own effects, flavour and climate. Seeds cost about $20 (Swamp Skunk) up to $300 (Dragon Fruit).

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

**Drug Lab > Mix:** cross two seeds. The child is random: effects, potency, climate, colours and bud shape come from the parents, or mutate into something new. About **1 in 70** crosses is **Mythic**: animated **rainbow**, **galaxy**, **golden**, **crystal**, **neon** or **inferno** buds that sparkle and sell for 2.5×.

### Effects

Strains carry up to 4 of 27 strain effects, and the hard drugs have their own. **None of them help in a fight** any more: no strength, no damage resistance, no invisibility. They're about farming, mining, getting around and having a weird time. Changed in 5.0:

| Effect | Now |
|---|---|
| Focus | see in the dark and find better loot |
| Rage | smash through stone like a machine (fast mining), but you get hungry |
| Pain Relief | no fall damage, and you heal slowly |
| Ghost | monsters don't notice you |
| Couch Lock | slow as a sloth, but you never get hungry |
| Zen | stand still to heal fast |
| Hyper | still insane speed, without the strength |

## Drugs (Drug Lab > Cook)

Recipes follow the real process **loosely**: most drugs take two or three cooks with an in-between product. They use game items and a made-up **Lab Solvent**, never real chemistry. 27 recipes, 15–60 seconds each. **Shift-click** cooks up to 4 batches.

| Chain | Steps |
|---|---|
| Hash | dried buds → **kief** → hash (+ paper) → wax → vape pen / moon rock |
| Edibles | dried buds + milk → **canna butter** → space brownies; kief + sugar + slime ball → gummies |
| LSD | wheat → ergot → **ergot extract** → LSD tabs on paper |
| Cocaine | coca leaves → **coca paste** → cocaine → crack (+ water, bone meal) |
| Heroin | poppy pods → opium → **morphine base** → heroin |
| Lean | opium + honey → **cough syrup** → lean |
| The rest | shroom tea (+ water), mescaline, DMT, meth, ecstasy, pixie dust, angel dust, ketamine |

Every step is worth more than what went in, and the in-between products sell too. Milk buckets and bottles come back empty.

![recipes](docs/recipes_preview.png)

## Cartels

A cartel is a **team**. Start one for $2,500 (Cartel panel) and invite players from the members list, or `/kush cartel invite <player>`.

* **Bank:** every sale a member makes adds **5%** on top into the bank (nobody pays it). Members put money in; the boss takes it out.
* **Levels:** the boss spends the bank on levels. Each one gives **every member better prices** and more member slots:

| Level | Cost | Members | Better prices |
|---|---|---|---|
| Crew | – | 4 | – |
| Gang | $10,000 | 6 | +4% |
| Syndicate | $40,000 | 8 | +8% |
| Cartel | $120,000 | 12 | +12% |
| Empire | $350,000 | 16 | +16% |

* **Shipments:** each cartel gets a big order, e.g. 75 cocaine within 24 hours. Every member can deliver part of it and gets paid for it; when it's full the bank gets a big bonus.
* **Top Dealers:** the best players and the best cartels.

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

* **Getting started:** seeds about $20–300 by strain, papers 8 for $8, solvent 8 for $18, a Drug Lab $650 (or craft one: crafting table, furnace, 3 iron, 2 bottles).
* **Product pays well** (about a third more than in 4.0): dried bud $20, kief $32, hash $80, cocaine $110, heroin $150, a vape pen $430. Rarer and stronger strains sell for more (Mythic 2.5×).
* **The market fights back:** every item you sell lowers the price of the next one, and prices climb back over time. Sell a mix. Shop > **Market** shows the contracts (big batches for bonus cash) and what's flooded.
* **Resources are expensive**, ores a bit more since 5.0: a diamond costs $1,950, an iron ingot $105, an emerald $780. Resources sell back for 20%.
* **Dying costs 20% of the cash in your wallet.** Nobody gets it, so killing other players doesn't pay (`death.cash-lost`).
* Jobs (mining, farming, hunting, growing) pay a little on the side.
* Built-in wallet, or **Vault** (EssentialsX, CMI…) if installed. Every number is in `config.yml`.

## Achievements

There are 50 awards, each with a cash reward: grow in every climate, pick a wild plant, cook every recipe, hire a Cook, give an animal red eyes, deliver a cartel shipment, build an Empire, breed a Mythic strain, sell $1,000,000 and more. They're also real **advancements** with their own *KushCraft* tab (press L).

## Installing

1. **Paper 26.3** (Java 25). Put the jar in `plugins/` and start the server.
2. That's it: players get the textures from GitHub when they join (`resource-pack.url: auto`). To host the pack yourself instead, set `url: ''` and open port 8163, or paste a direct link to your own copy.

**Updating from 3.x or 4.x:** replace the jar and restart.
* `config.yml` upgrades itself to version 8. These are **replaced** with the 5.0 ones: **shop prices** (with the Cook), worker prices and wages, the **Ores** shelf of Trade, the **cartel levels** and the market recovery. The `death`, `animals` and wild-plant settings are added. **`resource-pack.url` is set to `auto`** if it was empty or the old GitHub link, so textures load for everyone. Your other Trade shelves, cartels, strains and settings are kept.
* `strains.yml` gets any strains you're missing; strains your players bred are kept.

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
| `/kush shop` / `gear` / `market` / `drugs` / `trade` / `cartel` / `top` / `awards` | `kushcraft.use` | open a panel |
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
mvn clean package                 # rebuild the jar from clean, then:
python3 tools/make_pack_zip.py    # release/KushCraft-pack-<version>.zip (url: auto) + KushCraft-pack.zip
```

Always build from clean: old files left in `target/` would end up in the jar's pack. A released `KushCraft-pack-<version>.zip` must never change afterwards, because servers running that version download it.

GitHub Actions builds against the real Paper 26.3 API and boots a real Paper 26.3 server three times:
1. A fresh install: runs `/kush selftest` and checks that the pack hosted on GitHub is exactly the pack the jar builds.
2. An upgrade from a 3.0 config and a 2.0 `strains.yml`.
3. The built-in pack server.
