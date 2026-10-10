#!/usr/bin/env python3
"""A rough model of how long each rank takes (docs/PROGRESSION.md).

    python3 tools/progression_model.py [path/to/config.yml]

Reads ranks.ladder from the config and plays it through for a "steady" player
(2 active hours a day) and a "grinder" (5 hours a day, 25% more efficient).
It is a model, not a measurement: income per active hour ramps up as the
player grows (manual farm -> lab drugs -> workers), 55% of income is saved
for rank-ups (the rest goes on seeds, gear, wages, workers and deaths), a
worker is hired whenever a slot is free and affordable, and every worker adds
about 15% (their away work included). Change the numbers below to match what
you see on your server (/kush log, /baltop) and run it again.
"""
import yaml, math
import os, sys
CONFIG = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'config.yml')
cfg = yaml.safe_load(open(CONFIG))
ladder = cfg['ranks']['ladder']
gap_h = cfg['ranks']['min-gap-minutes'] / 60

# gross income per ACTIVE hour, by how far along the player is (active hours played so far)
# (manual farm -> lab drugs -> workers; at 9.0 prices, market saturation included)
def income(h, kind):
    base = [(0, 1500), (3, 4000), (10, 8000), (25, 15000), (60, 28000), (120, 42000), (200, 55000)]
    v = base[0][1]
    for at, val in base:
        if h >= at:
            v = val
    return v * (1.25 if kind == 'grinder' else 1.0)

# share of income left for rank-ups after seeds, gear, workers, training, wages and deaths
SAVE = 0.55
# workers bought when a slot opens (60k each on average) come out of savings first
WORKER_COST = 60000

def simulate(hours_per_day, kind):
    rows = []
    t_play = 0.0  # active hours
    day = 0.0     # calendar days
    money = 250.0
    last_rank_day = 0.0
    slots_used = 0
    step = 0.25
    for r in ladder[1:]:
        need_money = r['cost']
        need_play = r['playtime-hours']
        wait_days = max(r['wait-hours'], gap_h) / 24
        while True:
            # buy a worker when a slot is free and it's affordable (workers make more money later)
            prev = ladder[len(rows)]
            if slots_used < prev['workers'] and money >= WORKER_COST + need_money * 0.0 and t_play > 5:
                money -= WORKER_COST
                slots_used += 1
            ok = money >= need_money and t_play >= need_play and day - last_rank_day >= wait_days
            if ok:
                break
            inc = income(t_play, kind) * (1 + 0.15 * slots_used)  # each worker adds ~15% (incl. away work)
            money += inc * step * SAVE
            t_play += step
            day += step / hours_per_day
        money -= need_money
        last_rank_day = day
        rows.append((r['name'], r['cost'], need_play, r['wait-hours'], r['workers'], t_play, day))
    return rows

for hpd, kind in ((2, 'steady'), (5, 'grinder')):
    print(f"--- {kind}, {hpd} h/day")
    for name, cost, play, wait, workers, t, d in simulate(hpd, kind):
        print(f"{name:12s} ${cost:>11,}  gate {play:>3}h/{wait:>3}h  slots {workers}  -> at {t:6.1f} h played, day {d:6.1f}")
