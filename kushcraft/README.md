# KushCraft 8.0 – grow, cook & deal (Paper 26.3)

A **server-side only** Paper plugin with **102 strains** in weird colours and two-tone patterns, climates, custom plants that also **grow wild**, 26 drugs made **step by step**, 34 effects, strain breeding with **Mythic** and even rarer **Exotic** strains (Mythic seeds are in the Shop too), **as many workers as you like** (Farmhand, Dryer, Cook and Runner) that **use any of your chests, buy what they need and run the whole chain by themselves**, simple cartels, a sales leaderboard, 52 achievements, an admin panel and a living market where **money only comes from selling drugs**. It all comes in **one jar**.
Players don't install any mods. The plugin builds its own resource pack (32px art, 3D plants and blocks, worker skins and hats, animated Mythic and Exotic buds, menu art and recipe pictures) and every player gets it when they join.

**Download:** [`release/KushCraft-8.0.0.jar`](release/KushCraft-8.0.0.jar). Drop it in `plugins/` and restart. Players get the textures automatically.

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
| **Shop** | seeds of every strain (page arrows; **Mythic seeds** on their own page), **Gear & Workers** (with the workers' **auto-buy** switch), the **Market** (contracts and flooded products) and selling (click product, or **Sell all**) |
| **Drugs** | every product in one row per kind; click one to see its recipe picture |
| **Trade** | spend your money on 11 shelves: lab ingredients, ores, farming & food, wood, building blocks, colours, decoration, redstone, tools, mob drops, the Nether. **Buy only** |
| **Cartel** | your cartel (bank, level, members), how cartels work, the cartel **shipment** and **Top Dealers** |
| **Awards** | 52 achievements on two pages, greyed out until you unlock them |

The **Drug Lab** block opens on **Cook**, with **Roll**, **Dry** and **Mix** tabs and an **Upgrade** button. Recipes you have everything for glow; a red line under a recipe says what's missing **and where to get it**.

## Getting started

New players get the menu book and a small starter kit (2 OG Kush seeds and fertilizer). The glowing **Next** button always shows the next step:

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

Shop > **Gear & Workers** has a hiring board. Buy a worker, then right-click the ground where they should work. They walk to the job and plan the next one right where they are – they only walk home after a while with nothing to do. **Hire as many as you like** (`workers.max-per-player: 0`). Workers are an investment:

| Worker | Price | Pay | What they do |
|---|---|---|---|
| **Farmhand** | $12,000 | $5 a job | harvests your ripe plants around them, 3–5 per round, and replants; **plants seeds on every empty farmland and Planter**, fertilizes; spare seeds become fertilizer when the satchel and chests are full |
| **Dryer** | $9,000 | $4 a job | stands near your Drug Lab: hangs fresh buds on its racks and takes them off dry |
| **Cook** | $15,000 | $8 a batch | **you pick what they make** (button in their menu): any Drug Lab recipe, up to 4 batches at a time, or **rolling joints or blunts** (no lab needed). They **take the ingredients from any of your chests – several chests in one trip** – and fill empty bottles at water nearby. The recipe picker shows what they have (✔), what they'll buy ($) and what's missing (✘) |
| **Runner** | $11,000 | 10% of sales | **sells everything your workers make, the moment they get it** (at your Dealer Stand if one is near); brings workers what they're missing from your chests and puts what doesn't sell away. The money goes to your wallet and counts for the leaderboard |

**Your chests.** Workers use **any chest, trapped chest or barrel of yours** within their reach (a Runner: the whole crew's area). They take what they need from them – seeds, fertilizer, buds, solvent, sugar… – and put what they make in them. Chests other players placed are never touched.

**Auto-buy.** When a worker can't find something in your chests or with the rest of the crew – seeds for empty farmland, fertilizer, a recipe ingredient, papers, water – **they buy it with your money**, right where they are (Shop prices for KushCraft supplies, Trade prices for vanilla items). Switch it off for all your workers in Shop > **Gear & Workers** (or in any worker's menu); `workers.auto-buy: false` turns it off for the whole server. Workers never buy Mythic seeds.

**The work chain runs by itself.** Your workers within 32 blocks of each other (`workers.chain-radius`) are a crew: each one fetches what they need from the others and from your chests.

* Farmhand → Dryer (fresh buds) → Cook (dried buds) → Runner (joints, vape pens…)
* Cook → Cook works too: one makes coca paste, the next makes cocaine from it.
* Nobody takes what another worker needs for their own job, and the Runner only sells what no worker in the crew needs.
* A Cook whose lab holds a finished batch of another drug (you switched their recipe) collects it first, so the lab never stays stuck.

More:
* Satchels hold **54 stacks**.
* No money, no work: wages come out of your wallet.
* **Right-click** a worker for their menu: what they do and who they work with, the satchel on pages (click an item to take it, click your own items to give them), **Take all**, auto-buy (a Runner: **Sell now**), the chests they use, rename, pause, **train** (levels 2 and 3 cost $10,000 and $25,000: reach 8 → 12 → 16 blocks, rest less, walk faster) and dismiss (you get their contract and satchel back).
* `/kush workers` lists all of yours. **Collect everything** takes what they all made into your inventory.
* They only touch the plants, labs, Dealer Stands and chests of the player who hired them.
* **Smooth and light:** workers move every tick while someone can see them and only now and then when nobody can, think on different ticks, and find chests, plants and labs by chunk, so a big farm costs the server very little.

**Updating from 7.x:** Suppliers are gone – every Supplier is refunded to its owner ($13,000, `workers.supplier-refund`) and the others buy for themselves now. A Farmhand's seed backpack goes into their (bigger) satchel. Cooks on *Auto* or mixing strains ask you to pick a drug again.

## Wild plants

Plants grow by themselves out in the world, 20–56 blocks from players and never near anyone's farm. Cannabis is the strain of the biome, coca grows in jungles and savannas, poppies on plains, peyote in deserts and magic mushrooms in dark forests, swamps and mushroom fields. **Anyone can pick them** for buds and seeds. Unpicked ones wither after 6 hours (all of it is in `config.yml` under `wild`).

## Animals

Right-click an animal with a joint, an edible or any drug and its **eyes go red**. It's high for 90 seconds: slow and dopey on weed and downers, the zoomies on uppers, colours around its head on psychedelics.

## Strains & climates

There are **102 strains** and every one looks different: its own bud colour (jet black, ghost white, acid lime, hot magenta, ultraviolet…), leaf colour, hair colour and bud shape (**classic**, **foxtail**, **popcorn** or **spear**), many with a **second colour in a pattern**: frosted **tips**, tiger **stripes**, leopard **spots**, **marble**, **speckles**, glowing **halo** edges or **split** two-faced buds – on the seeds, the buds and the growing plant. Each has its own effects, flavour and climate. 61 are sold in the Shop (about $20 up to $375), plus **17 Mythic ones** on their own page (about $2,700 each); the rest are found in the wild or bred.

New in 8.0: Banana Kush, Jungle Juice, Frostbite, Desert Rose, Lotus Haze, Thunderhead, Black Cherry Punch, Kryptonite and Sunstone in the Shop, Fairy Dust and Mangrove Mist in the wild, four new Mythic strains (Starfall OG, Koi Kush, Lava Lamp, Frozen Rainbow) and a new Exotic one, Nebula Dream.

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

### Breeding, Mythic & Exotic strains

**Drug Lab > Mix:** cross two seeds. The child is random: effects, potency, climate, colours, two-tone pattern and bud shape come from the parents, or mutate into something new (weird colours included).

* **Mythic** (about **1 in 15** crosses, **about 1 in 4 from two Legendary parents**, more with a Mythic parent): animated buds and plants – **rainbow**, **galaxy**, **golden**, **crystal**, **neon**, **inferno**, **aurora**, **toxic**, **sakura**, **plasma**, **blood moon**, **ocean** or **candy**. They sparkle, some glow at night, and they sell for **4×**. **You can buy Mythic seeds in the Shop** (last page, `shop.mythic-seed-multiplier` × the normal price; `shop.mythic-seeds: false` to stop selling them), and they grow wild once in a blue moon.
* **Exotic** – rarer still, and **the only way to get one is crossing two Mythic strains** (3%, more with Exotic parents): **void**, **prism**, **celestial**, **phoenix**, **quantum** or **eclipse** buds that sell for **7×**. Eleven famous Exotic strains (Event Horizon, Prismatic Runtz, Phoenix Tears, Nebula Dream…) can only be found by crossing the right two Mythic strains. Exotic strains are never sold and never wild.

### Effects

Strains carry up to 4 of 27 strain effects, and the hard drugs have their own. **None of them help in a fight**: no strength, no damage resistance, no invisibility. They're about farming, mining, getting around and having a weird time:

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

Recipes follow the real process **loosely**: many drugs take two or three cooks with an in-between product. They use game items and a made-up **Lab Solvent**, never real chemistry. 27 recipes, 15–60 seconds each. **Shift-click** cooks up to 4 batches.

**Weed is kept simple:** fresh bud → dried bud → joint, blunt (rolled) or **vape pen** (4 dried buds + solvent + 2 iron nuggets + a glass pane). Kief, hash, wax, moon rocks, canna butter, brownies and gummies are no longer made; old ones still work and still sell.

| Chain | Steps |
|---|---|
| LSD | wheat → ergot → **ergot extract** → LSD tabs on paper |
| Cocaine | coca leaves → **coca paste** → cocaine → crack (+ water, bone meal) |
| Heroin | **4 poppy seeds → morphine base** (right in a crafting table, or a Cook makes it) → heroin |
| Oxy | morphine base + sugar + solvent → **oxy pills** |
| Lean | opium + honey → **cough syrup** → lean |
| Ayahuasca | glow berries → DMT → **ayahuasca** (+ vines, water) |
| The rest | shroom tea, **shroom chocolate**, mescaline, meth, **speed**, ecstasy, pixie dust, angel dust, ketamine, **xanny bars**, **moonshine**, **laughing gas** |

**Morphine base** comes straight from poppy seeds: put **4 Poppy Seeds** in a crafting table. Harvested poppies drop 2–4 seeds (one to plant again, the rest for morphine); opium is still made from poppy pods for cough syrup and lean. Every step is worth more than what went in, and the in-between products sell too. Water bottles come back empty.

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

* **Money only comes from selling drugs:** the Shop, contracts, cartel shipments and your Runners. Seeds, gear and vanilla items don't sell; jobs and awards pay nothing (both can be turned back on: `jobs.enabled`, `awards.cash-rewards`).
* **Getting started:** seeds about $20–300 by strain, papers 8 for $8, solvent 8 for $18, a Drug Lab $650 (or craft one: crafting table, furnace, 3 iron, 2 bottles).
* **Product pays well:** dried bud $20, a joint $32, a vape pen $150, cocaine $110, heroin $150, DMT $160. Rarer and stronger strains sell for more (Mythic 4×, Exotic 7×).
* **The market fights back:** every item you sell lowers the price of the next one, and prices climb back over time. Sell a mix. Shop > **Market** shows the contracts (big batches for bonus cash) and what's flooded.
* **Trade only sells** and it's expensive: a diamond costs $1,950, an iron ingot $105. 260+ items on 11 shelves, including a **Lab Ingredients** shelf with everything the recipes need. **No OP PvP gear and nothing from the End**: no netherite, totems, golden apples, ender pearls, elytra, shulkers or End blocks.
* **Dying costs 20% of the cash in your wallet.** Nobody gets it, so killing other players doesn't pay (`death.cash-lost`).
* Built-in wallet, or **Vault** (EssentialsX, CMI…) if installed. Every number is in `config.yml`.

## Achievements

There are 52 awards: grow in every climate, pick a wild plant, cook every recipe, hire a Cook, run a full **Assembly Line** (Farmhand, Dryer, Cook and Runner together), give an animal red eyes, deliver a cartel shipment, build an Empire, breed a Mythic strain, breed an Exotic one, sell $1,000,000 and more. They're also real **advancements** with their own *KushCraft* tab (press L).

## Installing

1. **Paper 26.3** (Java 25). Put the jar in `plugins/` and start the server.
2. That's it: players get the textures from GitHub when they join (`resource-pack.url: auto`). To host the pack yourself instead, set `url: ''` and open port 8163, or paste a direct link to your own copy.

**Updating from 7.x:** replace the jar and restart. `config.yml` upgrades itself to version 11: the Supplier leaves the shop and its settings go (its water price becomes `workers.water-price`), the market goes back to the 6.0 settings and `workers.auto-buy` is added. Suppliers are refunded to their owners. `strains.yml` gets the new strains and the Mythic ones go on sale; strains your players bred are kept.

**Updating from 3.x, 4.x, 5.x or 6.x:** replace the jar and restart.
* `config.yml` upgrades itself to version 11. These are **replaced** with the 6.0 ones: **shop prices** (the new drugs, the Runner, no selling seeds), **all Trade shelves**, `jobs.enabled` (off), and the worker limit (none), reach and rest times. From 3.x/4.x also the worker prices, the cartel levels and the market recovery. Missing settings are added. **`resource-pack.url` is set to `auto`** if it was empty or the old GitHub link. Your cartels, strains, workers and other settings are kept.
* Cooks that made kief, hash, wax, moon rock, butter, brownies or gummies ask you to pick a new drug.
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
| `/kush selftest live` | console | a minute or two of real worker work on a test world (logs LIVETEST PASS / FAIL) |

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

GitHub Actions builds against the real Paper 26.3 API and boots a real Paper 26.3 server four times:
1. A fresh install: runs `/kush selftest`, then `/kush selftest live` (a Farmhand, Dryer, Cook and Runner work a small farm on the server's own clock: harvest, plant, fetch from a chest, buy seeds and papers, dry, roll and sell – and the worker tick has to stay under 1.5 ms on average), and checks that the pack hosted on GitHub is exactly the pack the jar builds.
2. An upgrade from an old config (3.0 style, with 5.0 worker and Trade settings) and a 2.0 `strains.yml`.
3. An upgrade from 7.2 (a Supplier, the 7.x market, a version 5 `strains.yml`).
4. The built-in pack server.
