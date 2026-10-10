# KushCraft 9.0 – dupe, exploit and launch testing checklist

Work through this on a **copy of the server** (same plugins, same configs, a copy of the world) before the relaunch, with two or three people. Each test says what to do and what must happen. Tick it off, or write down what happened instead.

Part A runs by itself in CI on every push. Parts B–H need real players: CI has no players, so it can't click menus, die, or kill a worker.

Tip: `/kush log <player>` shows a player's transactions (newest first), so after each test you can see exactly what moved. `/kush db` shows the database state and when it last saved.

---

## A. What CI already checks on a real Paper 26.3 server

Each push builds the jar and starts a real Paper 26.3 server with Vault (`.github/workflows/kushcraft.yml`). On that server it:

- [x] creates `kushcraft.db` and passes `/kush selftest`, more than 26,000 checks, including:
  - money in whole cents;
  - refusals: negative, NaN, infinite, too large, more than the balance;
  - a deliberately failing save that has to roll back completely (balance, log and worker in one transaction);
  - the per-minute log totals, the Vault bridge, and making and checking a backup;
  - the rank ladder: money, playtime, wait, and no double rank-up from two clicks;
  - worker slots per rank, raids (when on), away catch-up, and the guide menu.
- [x] passes `/kush selftest live`: four workers really work a small farm on the server clock with nobody online. They take seeds, papers and fertilizer from a chest and never buy anything. The worker tick has to average under 1.5 ms.
- [x] checks that `/menu`, `/rankup`, `/balance`, `/pay` and `/baltop` belong to KushCraft and that KushCraft is the Vault economy.
- [x] runs `/kush backup`, which must say "verified".
- [x] runs a season reset with the **kushcraft** scope on a made-up player who has:
  - $12,345, rank 3, 2 h of playtime and $5,000 of sales;
  - a Farmhand, a plant and a cartel.

  For the reset, CI checks that:
  - a wrong confirm code is refused;
  - the two backups (before and final) are verified and still hold that player exactly as they were;
  - the live database has that player at $250, rank 1, 0 playtime and 0 sales;
  - no workers, plants, Drug Labs, cartels or awards are left;
  - there is one RESET row in the log and the season is 2.
- [x] restarts the server: season 2 is still season 2 and the self test passes again.
- [x] upgrades an 8.0 server, with its YAML files and old config:
  - balances, sales, the auto-buy switch, the cartel bank, plants, awards and the market are imported;
  - the old Supplier is refunded and the refund is logged;
  - the old files are moved to `legacy-yaml/`;
  - the config is upgraded with the old one kept, and the new prices are in.
- [x] upgrades an 8.1 config: add-on settings are removed and the 8.1 starter kit is fixed.

What CI **can't** check: anything that needs a player clicking, a real crash in the middle of a save, real lag with 100 players, or other plugins (EssentialsX, protection, anti-cheat) running next to KushCraft.

---

## B. Before testing: set up the test copy like the live server

- [ ] Vault is installed. The startup log says **"KushCraft registered as the Vault economy"**.
- [ ] The startup log says **"Commands: /menu, /rankup, /balance, /pay and /baltop are KushCraft's"**. If it names another plugin, see `STRUCTURE.md` → *Command collisions*.
- [ ] EssentialsX `config.yml`:
  - `disabled-commands: [balance, bal, money, pay, baltop, balancetop, eco, sell, worth, setworth]`;
  - `/balance` is **not** in `overridden-commands`.

  Restart, then `/bal` must show the KushCraft wallet.
- [ ] Your protection plugin (WorldGuard, GriefPrevention, ...) is running. Worker raids respect it: a protected worker can't be hit by someone who can't build there.
- [ ] `/kush backup` works and the folder `plugins/KushCraft/backups/` is on a disk that **you also back up somewhere else**.

---

## C. Money

| # | Test | Must happen |
|---|---|---|
| C1 | `/pay` yourself, `/pay <p> -5`, `/pay <p> 0`, `/pay <p> NaN`, `/pay <p> 1e309`, `/pay <p> 0.001`, `/pay <offline player> 5` | All refused. Nobody's balance changes. |
| C2 | `/pay <p> 10.555` | $10.55 or $10.56 arrives; never more than left the sender (whole cents). |
| C3 | Spam `/pay <p> 1` with a macro (20 times a second) | At most one payment per second goes through; the rest get "One payment a second". The total sent equals what arrived. |
| C4 | Two players `/pay` each other their whole balance at the same moment | Each payment either goes through or is refused. Total money of the two is unchanged. |
| C5 | `/pay` a player who is logging out at that moment | Money arrives or is refused, never lost or doubled (check `/kush log` for both). |
| C6 | Sell a stack in the dealer menu while spam-clicking and shift-clicking | Each item is paid once. Inventory count × price matches the SELL rows in `/kush log`. |
| C7 | Sell an item, then close the menu with the item on the cursor | The item is either sold (and gone) or back in your inventory, never both. |
| C8 | Buy in `/kush` → Shop with a macro clicking fast | At most one buy every 150 ms; money spent matches items received. |
| C9 | Buy with exactly not enough money | Refused, nothing given. |
| C10 | Use another Vault plugin (a chest shop, a job plugin) to pay and take money | It works. `/kush log` shows VAULT_IN / VAULT_OUT rows. |
| C11 | `/eco give` (EssentialsX) | Disabled. If it still works, B3 isn't done: Essentials would be printing money in its own wallet. |
| C12 | Die with $1,000 | You lose 20% ($200). Nobody gets it, so killing your own alt is never profitable. A DEATH row is logged. |

## D. Ranks

| # | Test | Must happen |
|---|---|---|
| D1 | New player: `/rankup` | Shows the money, playtime and wait still needed for the next rank. |
| D2 | Stand AFK for 10 minutes, or stand in a water stream | Playtime does not go up (it stops after `ranks.afk-minutes`, 5). |
| D3 | Give a test player the money (`/kush money <p> <amount>`) but not the playtime | The rank-up is refused and the menu says why. |
| D4 | Have all three requirements, then double-click / macro the rank-up button | Exactly **one** rank-up and **one** payment (RANK_UP row). |
| D5 | Rank up, then immediately again with plenty of money and playtime | Refused until the wait (and at least `min-gap-minutes`, 30) is over. |
| D6 | Open `/rankup` on two clients logged into the same account (or rank up and relog fast) | Never two rank-ups for one payment. |
| D7 | `/kush rank <p> 5` and `/kush playtime <p> 10` (hours) as an admin | Works and is logged as ADMIN. |

## E. Workers

| # | Test | Must happen |
|---|---|---|
| E1 | Rank 1: place a second worker contract | Refused; the message says how many worker slots your rank has. |
| E2 | Buy more worker contracts in the shop than your slots allow | Refused once placed workers + contracts in your inventory reach your slots. |
| E3 | Give a contract to a friend who has free slots | They can place it; you still can't place more than your slots. |
| E4 | Place six workers in one chunk (with an admin's `kushcraft.workers.unlimited`), then a seventh | The seventh is refused (`workers.max-per-chunk`, 6). |
| E5 | Hire spam: place contracts as fast as possible | One hire per 2 seconds. Each hire uses up exactly one contract. |
| E6 | Open a worker's satchel, take everything out while the worker is also working | No item ends up in both places. Count the items before and after. |
| E7 | Two players open the same worker's satchel and take the same stack | Only one of them gets it. |
| E8 | Dismiss a worker with a full satchel and a full inventory | The contract and the satchel items go to your inventory; whatever doesn't fit drops at your feet. Nothing vanishes and nothing is doubled. |
| E9 | Workers with no supplies, no money for wages, or a full chest | They stop and say why (in `/kush` → Workers). Nothing is created from nothing. |
| E10 | Log off for 2 hours next to a Farmhand with seeds in a chest, then log back in | The farm caught up at half speed (about 1 hour of work), no more than 12 hours. The harvest is in the satchel or chest, nothing elsewhere. |
| E11 | Stop the server for 2 hours, then start it | Server downtime does **not** count as away time: no extra harvest from the downtime. |
| E12 | Spam `/kush` → Workers → **Supply your crew** while the workers are busy | Each seed or paper moves once: what left your inventory is what arrived in the satchels. |

## F. PvP and raids (raids are **off**: `pvp.worker-raids.enabled: false`)

The PvP side is plain vanilla PvP: no bounties, no kill rewards. The only KushCraft PvP rule while raids are off is the 20% cash loss on death (C12).

If you turn worker raids on for testing:

| # | Test | Must happen |
|---|---|---|
| F1 | Hit someone else's worker in the open | It loses health and, at 0, is knocked out for 30 minutes (asleep pose). Its satchel spills on the ground. The owner gets an alert. |
| F2 | Hit your own or your cartel's worker | Nothing happens. |
| F3 | Hit a worker inside a claim you can't build in | Nothing happens. |
| F4 | Spam-click a worker with an auto-clicker | At most one hit counts every 0.4 s. |
| F5 | Knock out a worker, pick up its spill, then crash the server (F6 below) | After the restart the items are either on the ground / in your inventory **or** back in the satchel, never both. |

## G. Crashes (the important dupe tests)

Run each **twice**. "Crash" means `kill -9` on the server process (or pull the plug), **not** `/stop`.

| # | Test | Must happen after the restart |
|---|---|---|
| G1 | Sell a full inventory, crash within 1 second | Either the items are gone and the money is there, or the items are gone and the money isn't (a loss of at most the last ~¼ second). **Never** items back *and* money kept. |
| G2 | Take a stack out of a worker satchel, crash within 1 second | The stack is in the satchel **or** in your inventory, not both. |
| G3 | `/pay` someone $1,000, crash within 1 second | Both balances agree with each other: the payment happened or it didn't. |
| G4 | Rank up, crash within 1 second | Either ranked and paid, or neither. |
| G5 | Put items into a **vanilla chest** next to a worker, crash | Minecraft itself can roll a chest back to its last world save. That's vanilla behaviour and not something KushCraft can fix. Keep the world autosave frequent and use an anti-crash/watchdog setup. |
| G6 | Crash during a season reset, after you typed the confirm code | After the restart it is either fully reset (season 2) or not at all (season 1). There is never half a reset. The backups are in `backups/` either way. |

After each restart, run `/kush backup`: it runs SQLite's integrity check and must say **verified**.

## H. Season reset (on the test copy only)

- [ ] `/kush reset` lists the three scopes, with **kushcraft** as the default.
- [ ] `/kush reset kushcraft`:
  - makes a backup and shows its checks: integrity, player count, total money;
  - gives a 6-character code;
  - nothing is wiped yet.
- [ ] A wrong code is refused. The right code after 5 minutes is refused (it expires).
- [ ] With the right code:
  - a final backup is made, then the wipe;
  - online players see the welcome guide again and get the starter kit again;
  - their inventories stay.
- [ ] Workers, Drug Labs and KushCraft plants are gone from the world.

  Drug Labs in chunks nobody had loaded disappear the first time someone loads that chunk.
- [ ] `backups/resets.log` has a line for the reset.
- [ ] Restore test, so you know you can undo it:
  1. stop the server;
  2. copy `backups/<stamp>-final-before-reset-kushcraft/kushcraft.db` over `plugins/KushCraft/kushcraft.db` and delete `kushcraft.db-wal` and `kushcraft.db-shm` if present;
  3. start the server.

  Everyone's old money and ranks are back. (Workers come back as data; their mannequins respawn.)

## I. Load (with as many players as you can get)

- [ ] `/kush db` shows the saves ("snapshots") counting up. "queued" stays at 0 or 1, "failed" stays at 0, and the console has no save warnings.
- [ ] With 20+ workers loaded, the worker tick shown in `/kush db` stays low (the live test measures about 0.2 ms on average). `workers.tick-budget-ms` (5) caps it: past the budget, the rest wait until the next tick.
- [ ] `/kush backup` on a large database causes at most a short pause (it is a database copy, see `STRUCTURE.md` → *Load notes*). Make backups at quiet times; the reset makes its own.
- [ ] Watch `/tps` / spark for KushCraft after a restart with many owners offline: the away catch-up is spread out over time (`workers.away.budget-ms`, 3 ms per tick).
