# KushCraft 9.1 – the progression curve (proposal, needs your approval)

Everything here is a **proposal**. The numbers live in `config.yml` (`ranks.ladder`, `shop`, `workers`, `exchange.price-multiplier`, `growth`, `market`) and change with `/kush reload`. Nothing is wiped by changing them. Tell me which way to move them, or edit them yourself.

9.1 is **harder than 9.0** on purpose: everything you buy costs about twice as much again, the dealer pays 85% of what it used to, plants and labs are slower, wild seeds are rarer and the market punishes dumping more.

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
| 2 | Lookout | $4,500 | 3 h | (30 min) | 1 |
| 3 | Corner Boy | $18,500 | 7 h | 12 h | 2 |
| 4 | Pusher | $52,500 | 13 h | 1 day | 2 |
| 5 | Trapper | $127,500 | 22 h | 2 days | 3 |
| 6 | Chemist | $300,000 | 36 h | 3 days | 3 |
| 7 | Distributor | $640,000 | 55 h | 4 days | 4 |
| 8 | Smuggler | $1,280,000 | 80 h | 5 days | 4 |
| 9 | Lieutenant | $2,400,000 | 110 h | 6 days | 5 |
| 10 | Underboss | $4,500,000 | 150 h | 7 days | 6 |
| 11 | Don | $8,250,000 | 200 h | 10 days | 7 |
| 12 | Drug Lord | $15,000,000 | 270 h | 14 days | 8 |

* **Cost** roughly doubles each rank (×1.8 at the top, more at the bottom). The whole ladder costs **$32.6M**. (9.0 was $21.7M, but income is lower now, so this is a steeper climb than the numbers suggest.)
* **Waits** add up to **at least 53 days** of real time from rank 1 to 12, however rich someone is.
* **Worker slots** grow by at most one per rank, from 1 to 8. A full Farmhand → Dryer → Cook → Runner chain needs 4 slots, which is rank 7.

## Rough time to each rank

These numbers come from `tools/progression_model.py`, a model and not a measurement. Its assumptions:

* Income per active hour grows as a player builds up. The model starts from the 9.0 figures (about $1,500 an hour in the first hours, up to $55,000 an hour at 200 hours) and multiplies them by **0.64** for 9.1: the dealer pays 85%, and plants and cooking are slower.
* Selling a lot of one product drops its price, so these figures already assume a mix of products.
* Only **half** of the income is kept for rank-ups (it was 55%), because seeds, gear, wages, new workers and death losses cost about twice as much.
* A worker is hired whenever a slot is free and there's $120,000 to spare.
* Each worker adds about 15%, including the work they do while nobody is around.
* "Steady" is 2 active hours a day. "Grinder" is 5 hours a day, 25% more efficient.

| Rank | Steady (2 h/day): hours played · day | Grinder (5 h/day): hours played · day |
|---|---:|---:|
| 2 Lookout | 6 h · day 3 | 5 h · **first session** |
| 3 Corner Boy | 15 h · day 8 | 13 h · day 3 |
| 4 Pusher | 31 h · day 15 | 28 h · day 6 |
| 5 Trapper | 81 h · day 40 | 72 h · day 14 |
| 6 Chemist | 114 h · day 57 | 99 h · day 20 |
| 7 Distributor | 149 h · day 75 | 132 h · day 26 |
| 8 Smuggler | 212 h · day 106 | 185 h · day 37 |
| 9 Lieutenant | 297 h · day 149 | 257 h · day 51 |
| 10 Underboss | 448 h · day 224 | 378 h · day 76 |
| 11 Don | 700 h · day 350 | 579 h · day 116 |
| 12 Drug Lord | 1,120 h · day 560 | 916 h · day 183 |

What this curve does:

* **Rank 2 is reachable in a first session** of about 5 hours.
* The early ranks come every few play sessions. The **playtime and the wait** set the pace there.
* From rank 6 up **money** is what holds people back.
* A dedicated grinder reaches rank 8 in about **5 weeks** and the top in about **6 months**. A steady player (2 hours a day) gets to rank 7 in about a quarter of a year. **Nobody reaches the top in a season unless they play all day, every day**, and the top ranks are a long-term goal on purpose.
* Being robbed, dying (20% of carried cash) and losing workers to raids all slow this down. The model leaves them out, so on an anarchy server the real times will be longer.

**If that's too far for your seasons** (for example you reset every 3 months): scale all the `cost` values down together, for instance ×0.6, and re-run `python3 tools/progression_model.py`. It prints the new table from your own `config.yml`.

**Things to check before launch** (the model is a guess until real players run it):

* After the first week, compare `/baltop` and `/kush log` with the income figures above.
* If players are much faster or slower, scale the costs, or `shop.sell-multiplier` (what the dealer pays).
* If the top players are bored, add more `wait-hours`. If new players quit early, lower ranks 2–4.

## What 9.1 made harder

Prices are compared with 8.0 (the version before the relaunch) and with 9.0 (the first relaunch jar).

| | 8.0 | 9.0 | 9.1 |
|---|---:|---:|---:|
| Cannabis seeds (`seed-price-multiplier`) | ×1.25 | ×6.25 | ×12.5 (about $200–3,800, Mythic about $36,000) |
| Mythic seeds (`mythic-seed-multiplier`) | ×3 | ×3 | ×4 |
| Drug Lab / Grow Lamp / Dealer Stand | $650 / $200 / $300 | $3,250 / $1,000 / $1,500 | $6,500 / $2,000 / $3,000 |
| Rolling papers (8) / Lab Solvent (8) / fertilizer (4) | $8 / $18 / $16 | $40 / $90 / $80 | $80 / $180 / $160 |
| Farmhand / Dryer / Cook / Runner | $12k / $9k / $15k / $11k | $60k / $45k / $75k / $55k | $120k / $90k / $150k / $110k |
| Worker training (level 2 / 3) | $10k / $25k | $50k / $125k | $100k / $250k |
| Worker wages per job (Farmhand / Dryer / Cook) | $5 / $4 / $8 | $10 / $8 / $16 | $20 / $16 / $32, Runner keeps 20% |
| Drug Lab upgrades | $2.5k–50k | $12.5k–250k | $25k–500k |
| Strain mix | $250 | $1,250 | $2,500 |
| Trade (vanilla items, `price-multiplier`) | ×1 (diamond $1,950) | ×5 (diamond $9,750) | ×10 (diamond $19,500) |
| Start a cartel / cartel levels | $2.5k / $10k–350k | $12.5k / $50k–1.75M | $25k / $100k–3.5M |
| Starting money | $250 | $250 | $100 |

And what makes things **harder to get**:

| | 9.0 | 9.1 |
|---|---:|---:|
| What the dealer pays (`shop.sell-multiplier`) | 100% | 85% |
| A cannabis plant ripens in (`growth.cannabis-minutes`) | 20 min | 35 min (mushrooms 20, coca 28, poppy 24, peyote 30) |
| Drug Lab cooking time (`lab.time-multiplier`) and drying | ×1, 30 s | ×1.5, 45 s |
| Each sale drops the price by / lowest price / recovery per minute (`market`) | 0.6% / 40% / 2.5% | 1% / 30% / 1.5% |
| Wild seed chances (grass, mushrooms, coca, poppy, peyote, ergot) | 4% / 12% / 3% / 15% / 8% / 6% | 2.5% / 7% / 1.8% / 9% / 5% / 4% |
| Wild plants on the server / near one player | 80 / 6 | 50 / 4 |
| Player trades / Bazaar sales | – | 2% of the money / 8% of the price is destroyed |

Workers don't buy their own supplies (`workers.auto-buy: false`). Their owner keeps them stocked with the **Supply your crew** button or a chest near them, which makes running workers part of the grind.

## Workers: how much they add

Workers are a supplement, not an income on their own:

* **Wages** come out of the owner's money for every job, and the Runner keeps 20% of every sale.
* **While nobody is near them** (their chunk is unloaded, e.g. the owner is offline), they keep working at **half speed** (`workers.away.rate`) for at most **12 hours** (`workers.away.max-hours`). When someone comes back, that time is caught up. Server downtime doesn't count.
* **Supplies:** a worker without supplies, money or a free slot does nothing.

## The new street drugs

Eight new products, all cooked at the Drug Lab (the Cook page now has 4 rows of recipes, 35 in all). They are worth less than the hard drugs, but most need cheap vanilla ingredients, so they're a way in for new players and a market for trading.

| Drug | Ingredients (per batch) | Makes | Dealer pays (before 85%) |
|---|---|---:|---:|
| Kratom Tea | 6 jungle leaves, water | 3 | $22 |
| Poppers | 2 sweet berries, water, solvent | 4 | $30 |
| Study Pills | 2 gold nuggets, sugar, solvent | 6 | $48 |
| Absinthe | 3 fern, 2 sugar, water | 3 | $55 |
| Spice | 4 dried kelp, 2 fern, solvent | 4 | $60 |
| Poppy Tea | 2 poppy pods, sugar, water | 2 | $62 |
| Bath Salts | 2 quartz, glowstone dust, solvent | 3 | $75 |
| Moonflower Tea | 3 lily of the valley, water | 2 | $95 |

None of them helps in a fight (see the effect rules in the README): Bath Salts and Moonflower Tea come with a chance of a bad trip.
