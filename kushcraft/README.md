# KushCraft 7.2 – grow, cook & deal (Paper 26.3)

A **server-side only** Paper plugin with **86 strains** in weird colours and two-tone patterns, climates, custom plants that also **grow wild**, 26 drugs made **step by step**, each with **its own signature effect**, strain breeding with **Mythic** and even rarer **Exotic** strains, **as many workers as you like** (Farmhand, Dryer, Cook, Runner and Supplier) that **run the whole chain by themselves**, simple cartels, a sales leaderboard, 55 achievements, an admin panel and a living market where **money only comes from selling drugs**. It all comes in **one jar**.
Players don't install any mods. The plugin builds its own resource pack (32px art, 3D plants and blocks, worker skins and hats, animated Mythic and Exotic buds, menu art and recipe pictures) and every player gets it when they join.

**Download:** [`release/KushCraft-7.2.0.jar`](release/KushCraft-7.2.0.jar). Drop it in `plugins/` and restart. Players get the textures automatically.

| Items | Plants | Blocks |
|---|---|---|
| ![items](docs/items_preview.png) | ![plants](docs/plants3d_preview.png) | ![blocks](docs/blocks_preview.png) |

![strains](docs/strains_preview.png)

![menus](docs/gui_preview.png)

![workers](docs/workers_preview.png)

## The menu

Open it with **`/kush`**, **Shift + F**, or the **KushCraft Menu** book (or straight to a tab: `/kush shop`, `/kush workers`...). There are six tabs. In the top bar next to them are the glowing **Next** button (your next step, see *Getting started*) and your money. Admins also get a red **Admin** button. Long pages (seeds, awards, your workers) have **page arrows** in the bottom corners, and every page inside a tab has a **Back** button.

| Panel | What's there |
|---|---|
| **Shop** | seeds of every strain (two pages), **Gear**, the **Market** (contracts and flooded products) and selling (click product, or **Sell all**) |
| **Drugs** | every product in one row per kind; click one to see its recipe picture |
| **Trade** | spend your money on 11 shelves: lab ingredients, ores, farming & food, wood, building blocks, colours, decoration, redstone, tools, mob drops, the Nether. **Buy only** |
| **Workers** | hire all five kinds, see every worker of yours (what they're doing, what they're missing), **Collect everything**, pause / restart everyone |
| **Cartel** | your cartel (bank, level, members), how cartels work, the cartel **shipment** and **Top Dealers** |
| **Awards** | 55 achievements on two pages, greyed out until you unlock them |

The **Drug Lab** block opens on **Cook**, with **Roll**, **Dry** and **Mix** tabs and an **Upgrade** button. Recipes you have everything for glow; a red line under a recipe says what's missing **and where to get it**.

## Getting started

New players get the menu book and a small starter kit (2 OG Kush seeds and fertilizer). The glowing **Next** button always shows the next step:

1. **Plant a seed**: buy one in the Shop, or break grass (the biome decides the strain).
2. **Harvest it**: right-click the plant when it's fully grown. Sneak + right-click harvests all your ripe plants around it.
3. **Get a Drug Lab**: Shop > Gear, or craft one.
4. **Dry your buds**: Drug Lab > Dry (30 seconds).
5. **Roll or cook** something.
6. **Sell** your product.
7. **Pick a wild plant**: they grow out in the world.

`/kush start` shows all the steps.

## Textures for everyone

By default (`resource-pack.url: auto`) players download the pack of **this exact KushCraft version** from GitHub, so it works on every host, including Shockbyte and other hosts that only open the game port. The plugin checks the file's hash itself and checks it again every 20 minutes. If a player's download fails, it checks the link and sends the pack once more. Players who said no get a clickable message, or can type `/kush pack`.

## Workers

The **Workers** tab has a hiring board. Buy a worker, then right-click the ground where they should work. They walk to the job (around walls, through open and wooden doors) and plan the next one right where they are – they only walk home when there's nothing to do (idle Farmhands stroll among the plants). **Hire as many as you like** (`workers.max-per-player: 0`). Workers are an investment:

| Worker | Price | Pay | What they do |
|---|---|---|---|
| **Farmhand** | $12,000 | $5 a job | **plants every empty farmland and Planter** near them first, from their **seed backpack** (45 stacks – thousands of seeds, never in the satchel), harvests your ripe plants 3–5 per round and replants, fertilizes; keeps 32 seeds of each kind and hands on spare ones |
| **Dryer** | $9,000 | $4 a job | stands near your Drug Lab: hangs fresh buds on its racks and takes them off dry. **Sell what I dry** switch: every dried bud goes to the Runner to sell |
| **Cook** | $15,000 | $8 a batch | **you pick what they make**: any Drug Lab recipe (up to 4 batches at a time), **rolling joints or blunts**, **mixing strains** (they cross the two best strains they have seeds of and keep the ones rare enough – you pick how rare), or **Auto**: the most valuable drug they have everything for. They fill empty bottles at water nearby |
| **Runner** | $11,000 | 10% of sales | **empties your workers' satchels** and carries the work from worker to worker – the Farmhand's harvest to the Dryer, the Dryer's buds to a Cook – and **sells whatever nobody needs the moment they pick it up** (never seeds). Through walls the back way |
| **Supplier** | $13,000 | $6 a delivery | **buys whatever your workers are short of** with your money (**seeds for empty farmland**, fertilizer while plants grow, Trade items, Lab Solvent, papers, wraps, water) and brings it to them. **No budget**: they only stop when you can't pay |

**No chests – hand to hand.** Farmhand → **Runner** → Dryer → Cook, or the Runner, who sells it on the spot. With a Runner in the crew the others stay put and the Runner brings them what they need. **Nobody gets stuck with a full satchel**: seeds go in the Farmhand's backpack, nobody is given more than they have room for (a Dryer always keeps room for the dried buds), the Runner empties filling satchels first and sells what nobody can take in time, and a full backpack turns common seeds past 64 a kind into fertilizer (Rare and better strains never). **Stuck workers tell you**: a chat message with what's wrong and where (a ⚠ in the Workers tab), and a list when you join.

**The work chain runs by itself.** Your workers within 32 blocks of each other (`workers.chain-radius`) are a crew: each one takes what they need from the others they can walk to (Farmhand → Dryer → Cook → Runner, or Cook → Cook). When something is behind a wall or too far, the worker says what's missing and a **Runner** brings it from another worker, **walls or not**. A **Supplier** buys it if it can be bought. Nobody takes what another worker needs for their own job.

More:
* No money, no work: wages come out of your wallet.
* **Right-click** a worker for their menu: what they do and what they're missing, who they work with, **Show where they work** (a ring around their area, sparks over their crew), an option (a Dryer: sell what it dries; a Cook mixing strains: how rare a new strain must be to keep; a Supplier: what they spent; the others: how full their satchel is), the satchel (click an item to take it, click your own items to give them), a bottom bar with their own button (a Farmhand's **seed backpack**, a Runner's **Sell now**, a Dryer's racks, a Cook's shopping list, a Supplier's next buys) and what they're doing right now, rename, pause, **train** (levels 2 and 3 cost $10,000 and $25,000: reach 8 → 12 → 16 blocks, rest less, walk faster) and dismiss (you get their contract and satchel back).
* The **Workers** tab lists all of yours from anywhere (shift-click to pause one). **Collect everything** takes what they all made into your inventory.

## Wild plants

Plants grow by themselves out in the world, 20–56 blocks from players and never near anyone's farm. Cannabis is the strain of the biome, coca grows in jungles and savannas, poppies on plains, peyote in deserts and magic mushrooms in dark forests, swamps and mushroom fields. **Anyone can pick them** for buds and seeds. Unpicked ones wither after 6 hours (all of it is in `config.yml` under `wild`).

## Animals

Right-click an animal with a joint, an edible or any drug and its **eyes go red**. It's high for 90 seconds: slow and dopey on weed and downers, the zoomies on uppers, colours around its head on psychedelics.

## Strains & climates

There are **86 strains** and every one looks different: its own bud colour (jet black, ghost white, acid lime, hot magenta, ultraviolet…), leaf colour, hair colour and bud shape (**classic**, **foxtail**, **popcorn** or **spear**), many with a **second colour in a pattern**: frosted **tips**, tiger **stripes**, leopard **spots**, **marble**, **speckles**, glowing **halo** edges or **split** two-faced buds – on the seeds, the buds and the growing plant. Each has its own effects, flavour and climate. 52 are sold in the Shop (about $20 up to $300), the rest are found in the wild or bred.

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

**Drug Lab > Mix:** your seeds lie on a tray – click one strain, then another, check the chances and press **MIX**. Keep the child (it names itself; **Rename** if you like) or throw it away. A **Cook** can mix for you. The child is random: effects, potency, climate, colours, two-tone pattern and bud shape come from the parents, or mutate into something new (weird colours included).

* **Mythic** (about **1 in 15** crosses, more with a Mythic parent): animated buds and plants – **rainbow**, **galaxy**, **golden**, **crystal**, **neon**, **inferno**, **aurora**, **toxic**, **sakura**, **plasma**, **blood moon**, **ocean** or **candy**. They sparkle, some glow at night, and they sell for **4×**. 13 Mythic strains also grow wild, once in a blue moon.
* **Exotic** – rarer still, and **the only way to get one is crossing two Mythic strains** (3%, more with Exotic parents): **void**, **prism**, **celestial**, **phoenix**, **quantum** or **eclipse** buds that sell for **7×**. Ten famous Exotic strains (Event Horizon, Prismatic Runtz, Phoenix Tears…) can only be found by crossing the right two Mythic strains. Exotic strains are never sold and never wild.

### Effects

Strains carry up to 4 of 27 strain effects, and the hard drugs have their own. **None of them help in a fight against other players**: no strength, no invisibility, nothing that changes player damage. They're about farming, mining, getting around and having a weird time:

| Effect | Now |
|---|---|
| Focus | see in the dark and find better loot |
| Rage | smash through stone like a machine (fast mining), but you get hungry |
| Pain Relief | no fall damage, and you heal slowly |
| Ghost | monsters don't notice you |
| Couch Lock | slow as a sloth, but you never get hungry |
| Zen | stand still to heal fast |
| Hyper | still insane speed, without the strength |

### Every drug has its own signature

On top of its effects, **every drug has one thing nothing else does** (it's on the item, in the Drugs tab and in the handbook):

| Drug | Signature |
|---|---|
| Joint / Blunt / Vape Pen | perfect smoke rings / a hotbox cloud that gives friends a contact high / huge vapour clouds and a slow, drifting fall |
| Magic Mushroom | **Fairy Ring**: spores swirl round you and animals walk over to you |
| Shroom Tea | **Sunny Mood**: the rain stops – just for you |
| Shroom Chocolate | **Sweet Tooth**: every bite of food also heals 2 hearts |
| LSD | **Kaleidoscope**: the ground around you flickers into colours (only you see it) |
| Peyote | **Spirit Fox**: a glowing ghost fox walks with you |
| Mescaline | **Cactus Skin**: cactus, berry bushes and magma can't hurt you; the sky stays at sunset |
| DMT | **Machine Elves**: glowing elves circle you under a frozen night sky |
| Ayahuasca | **Vine Sight**: ores near you shimmer through the walls |
| Cocaine | **Nose Candy**: sprinting doesn't make you hungry |
| Crack | **Tweaking**: lightning-fast hands, a twitchy head |
| Meth | **Chemist**: your Drug Labs (and your Cooks) cook 25% faster |
| Speed | **Quick Step**: walk straight up blocks without jumping |
| Ecstasy | **Rave**: a beat drops, lights flash, animals dance |
| Pixie Dust | **Fairy Wings**: jump again in mid-air |
| Angel Dust | **Angel Wings**: glide forward when you fall (sneak to drop) |
| Opium | **Poppy Trail**: poppies bloom where you walk (only you see them) |
| Heroin | **Numb**: 35% less damage from mobs and the world (never from players) |
| Oxy | **Bounce**: no fall damage – big falls bounce you back up |
| Lean | **Slow-Mo**: monsters near you move in slow motion |
| Ketamine | **Moon Gravity**: huge jumps, slow falls |
| Xanny Bars | **Chill Pill**: no paranoia, bad trips or spinning |
| Moonshine | **Beer Goggles**: villagers give you discounts |
| Laughing Gas | **Balloon**: you float up, then drift down |

## Drugs (Drug Lab > Cook)

Recipes follow the real process **loosely**: many drugs take two or three cooks with an in-between product. They use game items and a made-up **Lab Solvent**, never real chemistry. 27 recipes, 15–60 seconds each. **Shift-click** cooks up to 4 batches.

**Weed is kept simple:** fresh bud → dried bud → joint, blunt (rolled) or **vape pen** (4 dried buds + solvent + 2 iron nuggets + a glass pane). Kief, hash, wax, moon rocks, canna butter, brownies and gummies are no longer made; old ones still work and still sell.

| Chain | Steps |
|---|---|
| LSD | wheat → ergot → **ergot extract** → LSD tabs on paper |
| Cocaine | coca leaves → **coca paste** → cocaine → crack (+ water, bone meal) |
| Heroin | **4 poppy seeds → morphine base** (right in a crafting table, or a Cook makes it) → heroin |
| Oxy | morphine base + sugar + solvent → **oxy pills** |
| Opium | poppy pods → opium |
| Lean | opium + honey → **cough syrup** → lean |
| Ayahuasca | glow berries → DMT → **ayahuasca** (+ vines, water) |
| The rest | shroom tea, **shroom chocolate**, mescaline, meth, **speed**, ecstasy, pixie dust, angel dust, ketamine, **xanny bars**, **moonshine**, **laughing gas** |

**Morphine base** comes straight from poppy seeds: put **4 Poppy Seeds** in a crafting table. Harvested poppies now drop 2–4 seeds (one to plant again, the rest for morphine). Every step is worth more than what went in, and the in-between products sell too. Water bottles come back empty.

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
* **Product pays well:** dried bud $20, a joint $32, a vape pen $150, cocaine $110, heroin $150, DMT $160. Rarer and stronger strains sell for more: **Mythic 4×, Exotic 7×**.
* **Mass production still pays:** every item you sell lowers the price of the next one by just 0.25%, never below **70%**, and prices are back to normal within minutes. Shop > **Market** shows the contracts (big batches for bonus cash) and what's flooded.
* **Trade only sells** and it's expensive: a diamond costs $1,950, an iron ingot $105. 260+ items on 11 shelves, including a **Lab Ingredients** shelf with everything the recipes need. **No OP PvP gear and nothing from the End**: no netherite, totems, golden apples, ender pearls, elytra, shulkers or End blocks.
* **Dying costs 20% of the cash in your wallet.** Nobody gets it, so killing other players doesn't pay (`death.cash-lost`).
* Built-in wallet, or **Vault** (EssentialsX, CMI…) if installed. Every number is in `config.yml`.

## Achievements

There are 55 awards: grow in every climate, pick a wild plant, cook every recipe, hire a Cook and a Supplier, have a Runner take a harvest to a Dryer, run a full **Assembly Line** (Farmhand, Dryer, Cook and Runner together), feel 10 drug signatures, give an animal red eyes, deliver a cartel shipment, build an Empire, breed a Mythic and an **Exotic** strain, sell $1,000,000 and more. They're also real **advancements** with their own *KushCraft* tab (press L).

## Installing

1. **Paper 26.3** (Java 25). Put the jar in `plugins/` and start the server.
2. That's it: players get the textures from GitHub when they join (`resource-pack.url: auto`). To host the pack yourself instead, set `url: ''` and open port 8163, or paste a direct link to your own copy.

**Updating from 3.x – 6.x:** replace the jar and restart.
* `config.yml` upgrades itself to version 10. From 6.0: the market (`demand-drop`, `min-price`, `recovery-per-minute`) and Trade price climb (`price-step`, `recovery-per-minute`) are **replaced** with the 7.0 ones, the **Supplier** is added to your `shop.buy` list and the new `workers.supplier` settings are added. From 3.x–5.x also everything 6.0 replaced (shop prices, Trade shelves, jobs off, no worker limit…). Missing settings are added. **`resource-pack.url` is set to `auto`** if it was empty or the old GitHub link. Your cartels, strains, workers and other settings are kept.
* `strains.yml` gets the 52 new strains (Mythic and Exotic ones too) and the two-tone patterns of the built-in ones; strains your players bred are kept.
* Workers stop reaching through walls: if one says they can't reach their Drug Lab, open a way or hire a Runner.
* **7.2:** workers that **actually keep working**: Farmhands keep seeds in a **seed backpack** (45 stacks) and plant every empty farmland first, Runners **empty everyone's satchels** and sell whatever nobody needs on the spot, nobody is given more than they have room for (the Dryer / Cook deadlock with full satchels is gone), Suppliers **buy seeds for empty farmland** and fertilizer, drying racks topped up by workers **dry again** (topping up used to restart the timer), two **Legendary** parents give a Mythic about **1 in 4**, a **new look for every menu** plus a seed backpack menu and a reworked worker menu, and less lag (chunk look-ups, quieter mannequins). CI now runs a live test: four workers run a small farm on a real server.
* **7.1:** **no chests** – work goes hand to hand (Farmhand → Runner → Dryer → Cook or sold). Runners sell what they pick up right away (never seeds), workers walk much less (no trip home between jobs), Cooks can **mix strains**, a new **mixer** (seed tray, chances card, keep without typing), Suppliers have **no budget** and buy seeds too, stuck workers tell you, and far less lag (walk areas are worked out once and refreshed when you build). Chests you used before keep their items – take them out yourself.
* **7.0.2:** workers **keep working while you're offline or far away** (`workers.work-offline: true` keeps the chunks around them loaded, up to `work-offline-max-chunks: 100`; set it to `false` to let them sleep). A **Dryer** has a **Sell what I dry** switch in its menu: ON, every dried bud goes to your Runners to sell (Cooks don't get any from that Dryer).

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
| `/kush shop` / `gear` / `market` / `drugs` / `trade` / `workers` / `cartel` / `top` / `awards` | `kushcraft.use` | open a panel |
| `/kush start` | `kushcraft.use` | getting started: your next steps |
| `/kush sell` | `kushcraft.use` | sell all your product |
| `/kush workers` | `kushcraft.use` | the Workers tab: hire, and all your workers from anywhere |
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
2. An upgrade from an old config (3.0 style, with 5.0 worker and Trade settings) and a 2.0 `strains.yml`.
3. The built-in pack server.
