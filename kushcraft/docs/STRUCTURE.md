# KushCraft 9.0 – structure map

What lives where, how data moves, which commands KushCraft owns, what to watch under load, and the code that could go (not deleted: **waiting for your OK**).

## 1. The big picture

```
 player action (menu click, command, block, worker tick)        main server thread
        │  checks + changes in memory (money in whole cents)
        ▼
 Persistence: every 5 ticks, a snapshot of what changed ───────► "KushCraft-DB" thread
        (players, ledger, workers, plants, labs, cartels,          one SQLite transaction
         awards, market/exchange docs)                             (all or nothing)
        ▼
 plugins/KushCraft/kushcraft.db  (SQLite, WAL)  ── /kush backup ──► backups/<stamp>-<reason>/
```

* **Everything a player owns** is in one database file: money, rank, playtime, sales, workers and their satchels, plants, Drug Labs, cartels, awards, market prices, and the transaction log.
* **Changes happen on the server thread**, in memory, so there are no races. The disk work happens on one database thread.
* **One snapshot, one transaction.** A sale (money in, log row) and a worker's satchel change are written together or not at all. If a write fails, the rows are marked changed again and go into the next snapshot.
* **Inventories vs the database:**
  * when a player hands something to KushCraft (selling, a contract placed as a worker), their inventory is saved **before** the snapshot;
  * when KushCraft hands something to them (taking from a satchel, Collect all), it is saved **after** the snapshot is in.

  A crash in between can lose the last ¼ second; it can't duplicate anything. (`Persistence.took` / `gave`)

## 2. Packages

`kushcraft/src/main/java/dev/kushcraft/`:

| Package | Files | What it does |
|---|---|---|
| *(root)* | `KushCraft`, `ConfigMigration`, `LegacyImport`, `Keys` | Start-up and wiring, `reload()`, config upgrades (version 13; older configs are kept as `config-old-v<N>.yml`), the one-time 8.0 YAML → database import, item/data keys |
| `storage` | `Database`, `Persistence`, `PlayerStore`, `PlayerRecord`, `ItemCodec`, `Docs`, `Backups` | **New in 9.0.** SQLite schema and the DB thread, the snapshot/flush loop and save ordering, per-player rows, item serialisation, small YAML documents (market, exchange), verified backups |
| `economy` | `Economy`, `Tx`, `Ledger`, `VaultBridge`, `Market`, `Shop`, `Exchange` | The wallet (cents, refusals, transfers), the transaction types, the log (per-minute totals for frequent events), the Vault economy provider, market demand, the Shop, the Trade shelves |
| `ranks` | `RankLadder`, `Playtime`, `DealerTitles` | **New:** the 12-rank ladder (money + active playtime + wait), AFK-aware playtime. `DealerTitles` is 8.0's sales leaderboard titles, renamed |
| `workers` | `Workers`, `Worker`, `WorkerType` | Hiring (rank slots, chunk cap), the work AI, satchels, chests, wages, Runner sales, **away catch-up**, raid hooks, the tick budget |
| `plants` | `PlantManager`, `Plant`, `WildPlants` | Growth (with away catch-up), harvests, wild plants |
| `machines` | `MachineManager`, `Machine`, `MachineType` | Drug Labs, Dealer Stands, Grow Lamps, Planters; cleanup of Lab blocks after a reset |
| `lab` | `Cooking`, `LabRecipe` | Lab recipes and cooking |
| `strains` | `StrainRegistry`, `Strain`, `Breeding`, … (10) | The 102 strains, climates, looks, breeding |
| `effects` | `EffectManager`, `EffectType`, `Dose`, `HighAnimals` | Drug effects, doses, high animals |
| `items`, `recipes`, `catalog` | `Items`, `ItemType`, `Recipes`, `RecipeBook`, `Catalog` | Custom items, crafting recipes, recipe pictures |
| `cartels` | `Cartels`, `Cartel` | Teams, bank (logged), levels, shipments |
| `awards` | `Awards`, `Award`, `Starter` | 52 achievements / advancements, the "Next step" list |
| `jobs` | `Jobs`, `PlacedBlocks` | Paid vanilla work, **off** (`jobs.enabled: false`) |
| `pvp` | `WorkerRaids` | **New, off** (`pvp.worker-raids.enabled: false`): attacking workers. No bounties: PvP is plain vanilla |
| `reset` | `SeasonReset` | **New:** `/kush reset` with a verified backup, a confirm code and a one-transaction wipe |
| `menus` | 36 files | All GUIs. **New:** `InfoMenu` (`/menu` guide: Economy, Ranks, Rules, Community), `RanksMenu` (`/rankup`), `MenuTexts` (reads `menus.yml`) |
| `listeners` | `PlayerListener`, `Onboarding`, `InteractListener`, `MachineListener`, `PlantListener`, `WorldListener` | Join/quit/death, **first join each season** (kit + guide after the resource pack prompt), block and item events |
| `commands` | `KushCommand`, `ServerCommands`, `AdminCommands`, `SelfTest`, `LiveTest`, `TestSeed` | `/kush …`, the server commands (`/menu`, `/rankup`, `/balance`, `/pay`, `/baltop`), admin commands (rank, playtime, log, backup, db), the tests CI runs |
| `pack` | `ResourcePackManager` | Builds and sends the resource pack |
| `util` | `BlockKey`, `InventoryUtil`, `Protection`, `RateLimit`, `StrainStock`, `Text` | Small helpers; `RateLimit` is new (per-player limits on hire, pay, shop buys, raid hits) |

### Files on the server

| File | What |
|---|---|
| `plugins/KushCraft/config.yml` | All settings, grouped by system (see the list at the top of the file) |
| `plugins/KushCraft/menus.yml` | **All words of the `/menu` guide**: pages, rules, links (Discord, vote sites). Edit and `/kush reload` |
| `plugins/KushCraft/strains.yml` | Strains (bred strains are added here) |
| `plugins/KushCraft/kushcraft.db` (+ `-wal`, `-shm`) | The database. Never copy it while the server runs; use `/kush backup` |
| `plugins/KushCraft/backups/` | Verified backups (`manifest.txt` with counts, total money and sha256) and `resets.log` |
| `plugins/KushCraft/legacy-yaml/<stamp>/` | The 8.0 data files after their one-time import |

### Database tables

`players` (balance in cents, sales, rank, ranked_at, playtime, flags, season), `ledger` (transaction log), `workers` (with satchel), `plants`, `machines`, `cartels` (bank column + data), `awards`, `docs` (market, exchange), `cleanup` (Lab blocks to clear in unloaded chunks after a reset), `meta` (schema, season, last reset).

## 3. Built on vs replaced

**Kept from 8.0** (with the 9.0 prices):

- strains, plants and wild plants;
- the Drug Lab and recipes, effects;
- the worker AI (Farmhand, Dryer, Cook, Runner);
- market, Shop and Trade;
- cartels and awards;
- the `/kush` menus and the resource pack.

**Replaced:**

| 8.0 | 9.0 |
|---|---|
| YAML data files saved every few minutes | SQLite, a snapshot every 5 ticks, one transaction each |
| Money as decimals, Vault or a built-in wallet | Whole cents in the database. KushCraft **is** the Vault economy (Highest priority) |
| No log | Every money event in `ledger` (`/kush log [player]`) |
| "Ranks" = sales leaderboard titles | Real rank ladder (`ranks.ladder`). The sales titles are now `dealer-titles` |
| Unlimited workers | Worker slots by rank, max 6 workers per chunk, tick budget |
| Workers buy their supplies (auto-buy) | Off by default. Owners supply them, with the **Supply your crew** button or a chest |
| Workers stop when the chunk unloads | They catch up at half rate (max 12 h) when the chunk loads again |
| Starter steps only | Plus the `/menu` guide, opened on the first join of each season |
| Nothing | Season reset, backups, rate limits, raid module (off) |

**Removed in this branch** (at your request): SMPSuite (skills, gems, teams, voice groups) and KushCraft 8.1 "add-on mode". `release/KushCraft-8.1.0.jar` was replaced by `KushCraft-9.0.0.jar`; the released pack zips are untouched.

## 4. Commands and collisions

KushCraft registers:

| Command | Aliases | Who |
|---|---|---|
| `/kush` | `/k`, `/kc`, `/kushcraft` | everyone (admin subcommands need `kushcraft.admin`, reset needs `kushcraft.reset`) |
| `/menu` | | everyone: the server guide |
| `/rankup` | | everyone |
| `/balance` | `/bal`, `/money` | everyone |
| `/pay` | | everyone (1 per second) |
| `/baltop` | `/balancetop` | everyone |

**Collisions with EssentialsX** (`/balance`, `/bal`, `/money`, `/pay`, `/baltop`, `/balancetop`, `/eco`, `/sell`, `/worth`). **KushCraft should win all of them**, because KushCraft owns the money. If EssentialsX's versions ran, they would read and change EssentialsX's own wallet, a second currency that is invisible to KushCraft and its log.

* `plugin.yml` has `loadbefore: [Essentials]`, so KushCraft registers first. EssentialsX normally defers to another plugin's command of the same name.
* At start-up KushCraft checks who answers each command and logs either *"Commands: /menu, /rankup, /balance, /pay and /baltop are KushCraft's"* or the plugin that took one.
* Do this in EssentialsX's `config.yml`:
  ```yaml
  disabled-commands:
    - balance
    - bal
    - money
    - pay
    - baltop
    - balancetop
    - eco
    - sell
    - worth
    - setworth
  ```
  and make sure none of those are in `overridden-commands`. Players and admins use `/kush money <player> <amount>` instead of `/eco`.
* `/menu` and `/rankup` aren't EssentialsX commands, but menu plugins (DeluxeMenus) and rank plugins (e.g. a separate rankup plugin) often use them. Don't run a second rank plugin; KushCraft's ladder is the rank system.
* `/k` is short and occasionally taken by another plugin. `/kush` always works.

## 5. Load notes (what to watch, and the fixes)

| # | What | Status / proposal |
|---|---|---|
| L1 | **Saving.** All database writes are on one background thread, batched every 5 ticks; the server thread only collects what changed. | Done. `/kush db` shows how long the last snapshot took, the queue and failures: check it on the test server under load. |
| L2 | **Worker AI** at scale: unlimited workers was 8.0's biggest lag risk. | Done: rank slots (max 8 each), 6 per chunk, and a 5 ms per tick budget with a rotating start. Past the budget, the rest continue next tick. CI: 0.22 ms per tick average with 4 busy workers. |
| L3 | **Away catch-up** after a restart: many crews catching up at once. | Done: 3 ms per tick (`workers.away.budget-ms`), 60-second steps. |
| L4 | **Leaderboard and tab list:** 8.0 re-sorted all players and redrew tab names on every sale. | Done: titles update incrementally; tab names only update when a title changes. |
| L5 | **`/kush backup` pauses the server** while SQLite copies the file and the copy is checked. That's tens of milliseconds now, roughly a second per 100 MB later. | **Flagged.** Make backups at quiet times; the reset's own backups are meant to be blocking. *Proposed fix:* run `/kush backup` fully in the background with a callback. Say if you want it. |
| L6 | **Player inventory saves** around sales and satchel moves (§1): one `saveData()` per affected player per snapshot, at most once every 5 ticks per player. | Fine for normal play. *If* 100+ players mass-sell at once shows up in timings, raise `storage.flush-ticks` to 10. |
| L7 | **Chests are vanilla:** a crash can roll a chest back to the last world save, while KushCraft's satchels can't roll back. | Not a KushCraft fix. Keep the world autosave on (Paper default), and don't `kill -9` the server. |
| L8 | **`/baltop`** sorts all player rows each time. | Fine up to tens of thousands of rows. *Proposed fix if needed:* cache for 30 s. |
| L9 | **Log size:** frequent events are one row per player per minute; rows older than 180 days are removed. | Done (`storage.log-days`). |
| L10 | **All players are in memory** (about 200 bytes each). | Fine. |

## 6. Dead code – waiting for your OK (nothing below has been deleted)

**Safe to delete** (nothing calls them):

| What | Where | Notes |
|---|---|---|
| `Cartel.shipmentsDone()` | `cartels/Cartel.java` | unused getter |
| `EffectManager.pending()` | `effects/EffectManager.java` | unused |
| `Items.sameKind()` | `items/Items.java` | unused |
| `LabRecipe.anyNeeds()` | `lab/LabRecipe.java` | unused |
| `DrugsMenu.cookRecipe()` | `menus/DrugsMenu.java` | unused |
| `PlantManager.strainColor()` | `plants/PlantManager.java` | unused |
| `Recipes.shaped()` | `recipes/Recipes.java` | unused private helper |
| `Breeding.childName()` | `strains/Breeding.java` | unused |
| `StrainType.blurb()` | `strains/StrainType.java` | unused |
| `BlockKey.down()` | `util/BlockKey.java` | unused |
| `Workers.available()`, `hirePrice()`, `pauseAll()` | `workers/Workers.java` | unused |
| `Keys.GOT_GUIDE`, `Keys.BALANCE` | `Keys.java` | old storage keys, never read now |

**Delete after the live server has run 9.0 once** (they're only for the move from 8.0):

| What | Where | Why it's still here |
|---|---|---|
| YAML import (`importYaml` in Workers, PlantManager, MachineManager, Cartels, Awards; `LegacyImport`; the legacy branch in `Docs.read`) | several | Brings your 8.0 data into the database on the first 9.0 start. A reset wipes it anyway, but the import runs before you reset. |
| Base64 item decoding | `Workers`, `MachineManager` | Reads 8.0's YAML satchels and racks |
| 7.x Supplier refund | `Workers` | Only matters if 7.x Supplier data is still around |

**Your call** (switched off by config, still working):

| What | Size | Recommendation |
|---|---|---|
| Worker **auto-buy** | about 50 lines across `Workers`, the worker menus and the tests | Keep while you're unsure. It's off (`workers.auto-buy: false`) and one line turns it back on. Delete if you're sure. |
| **Jobs** (paid mining/farming) | `jobs/`, about 360 lines | Off. Delete if money should only ever come from drugs. |
| Award **cash rewards** | a few lines in `Awards` | Off. Delete or keep. |
| Old weed products (kief, hash, wax, moon rocks, canna butter, brownies, gummies) and retired items (Strain Maker, Rolling Table, Drying Rack, Catalyst) | item types and textures | **Keep for now.** With the "All KushCraft progress" reset inventories stay, so old copies may still be in chests. Without their types they'd become plain paper. |
| `StarterMenu` (the glowing **Next** steps) | `menus/StarterMenu.java`, `awards/Starter.java` | Overlaps the new `/menu` guide but is hands-on (what to do next). Keep both, or drop it and point **Next** at `/menu`. |
| **Dealer title bonus** (+15% for #1 … +2% for top 25) | `dealer-titles` | Not dead, but a **balance question**: it compounds with the rank ladder (the richest seller earns 15% more, so gets richer faster). Proposal: bonuses to 0 and keep the titles as bragging rights. A config change, so it's in your hands. |
| Duplicate sell-price code | `Selling.value` and `Workers.sellAll` | Same maths twice. Merge into one `Shop` method when next touched. |
