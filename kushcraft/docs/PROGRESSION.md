# KushCraft 9.0 – the progression curve (proposal, needs your approval)

Everything here is a **proposal**. The numbers live in `config.yml` (`ranks.ladder`, `shop`, `workers`, `exchange.price-multiplier`) and change with `/kush reload`. Nothing is wiped by changing them. Tell me which way to move them, or edit them yourself.

## How a rank-up works

To go from one rank to the next, a player needs **all three** of these:

1. **Money:** the rank's `cost`, paid when they rank up.
2. **Active playtime this season:** `playtime-hours`. Time only counts while the player is doing something: turning their head, clicking, breaking or placing blocks, typing commands. Standing in an AFK pool or walking in a water stream earns nothing.
3. **Real time since their last rank-up:** `wait-hours`. On top of that there are always at least `ranks.min-gap-minutes` (30) between two rank-ups, whatever the rank says.

A rank-up is one click in `/rankup`, one rank at a time. Two clicks in the same moment can't rank someone up twice, because the click only works from the rank the menu showed.

## The proposed ladder

| # | Rank | Cost | Total active playtime | Wait since last rank-up | Worker slots |
|---|---|---:|---:|---:|---:|
| 1 | Fresh Meat | – | – | – | 1 |
| 2 | Lookout | $3,000 | 2 h | (30 min) | 1 |
| 3 | Corner Boy | $12,500 | 5 h | 12 h | 2 |
| 4 | Pusher | $35,000 | 10 h | 1 day | 2 |
| 5 | Trapper | $85,000 | 18 h | 2 days | 3 |
| 6 | Chemist | $200,000 | 30 h | 3 days | 3 |
| 7 | Distributor | $425,000 | 45 h | 4 days | 4 |
| 8 | Smuggler | $850,000 | 65 h | 5 days | 4 |
| 9 | Lieutenant | $1,600,000 | 90 h | 6 days | 5 |
| 10 | Underboss | $3,000,000 | 120 h | 7 days | 6 |
| 11 | Don | $5,500,000 | 160 h | 10 days | 7 |
| 12 | Drug Lord | $10,000,000 | 220 h | 14 days | 8 |

* **Cost** roughly doubles each rank (×2.0 to ×4 low down, ×1.8 at the top). The whole ladder costs **$21.7M**.
* **Waits** add up to **at least 53 days** of real time from rank 1 to 12, however rich someone is.
* **Worker slots** grow by at most one per rank, from 1 to 8. A full Farmhand → Dryer → Cook → Runner chain needs 4 slots, which is rank 7.

## Rough time to each rank

These numbers come from `tools/progression_model.py`, a model and not a measurement. Its assumptions:

* Income per active hour grows as a player builds up:
  * about $1,500 in the first hours (a few plants, selling dried buds and joints)
  * $4,000 by hour 3
  * $8,000 by hour 10 (lab drugs)
  * $15,000 by hour 25
  * $28,000 by hour 60
  * $42,000 by hour 120
  * $55,000 by hour 200
* Selling a lot of one product drops its price, so these figures already assume a mix of products.
* 55% of income is kept for rank-ups; the rest goes on seeds, gear, wages, new workers and death losses.
* A worker is hired whenever a slot is free and there's $60,000 to spare.
* Each worker adds about 15%, including the work they do while nobody is around.
* "Steady" is 2 active hours a day. "Grinder" is 5 hours a day, 25% more efficient.

| Rank | Steady (2 h/day): hours played · day | Grinder (5 h/day): hours played · day |
|---|---:|---:|
| 2 Lookout | 3 h · day 2 | 3 h · **first session** |
| 3 Corner Boy | 9 h · day 5 | 8 h · day 2 |
| 4 Pusher | 18 h · day 9 | 15 h · day 3 |
| 5 Trapper | 43 h · day 21 | 37 h · day 7 |
| 6 Chemist | 63 h · day 31 | 55 h · day 11 |
| 7 Distributor | 82 h · day 41 | 75 h · day 15 |
| 8 Smuggler | 119 h · day 60 | 102 h · day 21 |
| 9 Lieutenant | 163 h · day 81 | 143 h · day 29 |
| 10 Underboss | 230 h · day 115 | 203 h · day 41 |
| 11 Don | 326 h · day 163 | 280 h · day 56 |
| 12 Drug Lord | 489 h · day 244 | 410 h · day 82 |

What this curve does:

* **Rank 2 is reachable in a first session** of about 3 hours.
* The early ranks come every few play sessions. The **playtime and the wait** set the pace there, so nobody rushes ranks 2–5 with a lucky sale.
* From rank 6 up **money** is what holds people back, because each rank costs about twice the last.
* A dedicated grinder reaches the top in **about 12 weeks, a full season**. A steady player gets to rank 8–9 in a season. **Nobody reaches the top in a weekend**: the waits alone are 53 days.
* Being robbed, dying (20% of carried cash) and losing workers to raids all slow this down. The model leaves them out, so on an anarchy server the real times will be longer.

**Things to check before launch** (the model is a guess until real players run it):

* After the first week, compare `/baltop` and `/kush log` with the income figures above.
* If players are much faster or slower, scale all the `cost` values together. `tools/progression_model.py` recomputes the table.
* If the top players are bored, add more `wait-hours`. If new players quit early, lower ranks 2–4.

## What 9.0 made more expensive

Everything players **buy** costs about **5×** what it did in 8.0. What the dealer **pays** for product is unchanged, so the grind is longer without making product worthless.

| | 8.0 | 9.0 |
|---|---:|---:|
| Cannabis seeds (`seed-price-multiplier`) | ×1.25 | ×6.25 (about $100–1,900, Mythic about $13,500) |
| Drug Lab / Grow Lamp / Dealer Stand | $650 / $200 / $300 | $3,250 / $1,000 / $1,500 |
| Rolling papers (8) / Lab Solvent (8) / fertilizer (4) | $8 / $18 / $16 | $40 / $90 / $80 |
| Farmhand / Dryer / Cook / Runner | $12k / $9k / $15k / $11k | $60k / $45k / $75k / $55k |
| Worker training (level 2 / 3) | $10k / $25k | $50k / $125k |
| Worker wages per job | $5 / $4 / $8, Runner 10% | $10 / $8 / $16, Runner 15% |
| Drug Lab upgrades | $2.5k–50k | $12.5k–250k |
| Strain mix | $250 | $1,250 |
| Trade (vanilla items, `price-multiplier`) | ×1 (diamond $1,950) | ×5 (diamond $9,750) |
| Start a cartel / cartel levels | $2.5k / $10k–350k | $12.5k / $50k–1.75M |
| Starting money | $250 | $250 |

Workers no longer buy their own supplies (`workers.auto-buy: false`). Their owner keeps them stocked with the **Supply your crew** button or a chest near them, which makes running workers part of the grind.

## Workers: how much they add

Workers are a supplement, not an income on their own:

* **Wages** come out of the owner's money for every job, and the Runner keeps 15% of every sale.
* **While nobody is near them** (their chunk is unloaded, e.g. the owner is offline), they keep working at **half speed** (`workers.away.rate`) for at most **12 hours** (`workers.away.max-hours`). When someone comes back, that time is caught up. Server downtime doesn't count.
* **Supplies:** a worker without supplies, money or a free slot does nothing.
