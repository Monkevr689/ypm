# SMPSuite 1.0 – skills, Bliss gems, teams & voice (Paper 26.3)

A survival-first plugin suite for a school SMP. **Skills are the main progression**: mining, farming, fishing, foraging, digging and fighting level up from normal play, pay a little money and give **small, hard-capped** bonuses. **Bliss gems** add flavour on top, **teams** and **voice chat** are social tools. Nothing stacks into a PvP or economy runaway: combat perks are the smallest and the slowest, and one cap covers skills and gems together.

**Download:** [`release/SMPSuite-1.0.0.jar`](release/SMPSuite-1.0.0.jar) – one jar, Paper 26.3, Java 25. Optional: [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) for voice, Vault (or [KushCraft](../kushcraft)) for jobs pay.

![gems](docs/gems_preview.png)

## Skills

`/skills` shows every skill: level, progress, current bonus, the bonus at level 50, the ability and its cooldown, and what it pays.

| Skill | XP from | Passive bonus (hard cap at level 50) |
|---|---|---|
| **Mining** | ores (coal 3 … diamond 18, ancient debris 40), stone 0.2 | **+4 mining speed** (≈ one extra Efficiency level), 10% double ores |
| **Farming** | fully grown crops, melons, pumpkins, berries, breeding, shearing | 20% double crops |
| **Fishing** | fish 8, treasure 15, junk 3 | fish bite **25% sooner**, 10% double catch |
| **Foraging** | logs 1.5 | **"forager's strength": +1 melee damage** (half vs players), 15% double logs |
| **Excavation** | dirt, sand, gravel, clay… | 15% double drops |
| **Combat** | hostile mobs (zombie 4 … warden 150), animals 0.5, players 15 | **+10% damage** (half vs players) – levels **1.5× slower** |
| **Vitality** | 15% of all XP above + healing by eating | **+0.5 heart at 10, 20, 35 and 50 → 12 hearts max** – levels **2× slower** |

**The curve is gradual:** XP for the next level = 40 × level^1.1 (× 1.5 for Combat, × 2 for Vitality). A skill reaches level 10 after about 2 hours of that activity, 25 after about 15 hours and 50 after about 60 hours.

**No exploits:** blocks players placed give nothing (tracked per chunk, pistons included), cobblestone gives nothing, ores need the right tool, spawner mobs give 25%, killing the same player again within 15 minutes gives nothing, villagers and golems give nothing.

### Abilities – Shift + right-click with the tool

Each one unlocks at **level 10** of its skill and has a real cooldown, so it's a skill moment, not spam. Axes, hoes and shovels trigger on **air** only (sneak-tilling, stripping and path-making still work).

| Ability | Skill / tool | What it does | Cooldown |
|---|---|---|---|
| **Haste Pulse** | Mining / pickaxe | Haste III for 30 seconds | 4 min |
| **Tree Feller** | Foraging / axe | readies the axe for 10 s – the next log you break fells the whole connected tree (up to 150 logs, never logs players placed; it stops before your axe would break; claims and protection plugins are respected) | 4 min |
| **Bountiful Harvest** | Farming / hoe | 30 s: grown crops drop double and replant themselves | 4 min |
| **Lucky Cast** | Fishing / rod | 60 s: fish bite twice as fast, plus Luck | 5 min |
| **Treasure Sense** | Excavation / shovel | 30 s: digging can turn up flint, clay, bones, nuggets (rarely an emerald) | 4 min |

Combat has **no** ability – the server is PvP-light.

## Balance rules (the caps)

* **One damage cap for everything:** Combat %, Foraging's flat bonus and the Strength gem are added up and then capped at **+20% of the hit against mobs** and **+8% against players** (`balance.damage-bonus-cap-*`). Maxed Combat + maxed Foraging + Strength gem is still only +8% on a player.
* **Hearts:** nobody goes above **12 hearts** from SMPSuite, even if another plugin adds health (the Vitality bonus shrinks to fit). No gem adds hearts.
* **No double doubling:** the Wealth gem's Rich Rush and Mining's double-ore chance never both apply to one block; Bountiful Harvest replaces the Farming roll.
* **Haste never stacks:** Haste Pulse (Haste III) and the Speed gem (Haste II) – Minecraft keeps the stronger one.
* **Gem damage to players is halved,** and gem abilities only touch players you're allowed to hit there (PvP on, not a teammate, not inside someone's claim – the server is asked first).

## Jobs pay

Every bit of skill XP also pays money: **XP × the skill's rate** (Mining 1.6, Farming 1.6, Foraging 1.6, Excavation 1.5, Fishing 1.2, Combat 1.0, Vitality nothing), **+1% per skill level (max +50%)**. An hour of mining or farming pays about $2,000–3,500; fighting pays the least. **At most $5,000 an hour** per player (stops AFK farms). Money goes to **Vault** (EssentialsX, CMI…) if installed, otherwise **KushCraft's wallet**, otherwise pay is off.

## Bliss gems

Every player gets **one random gem** on their first join, bound to them. **Hold it in your off hand:** passives work by themselves, **F** (swap hands) uses the **primary** ability and **Shift + F** the **secondary**. `/gem` shows everything.

| Gem | Passives | F – primary | Shift + F – secondary |
|---|---|---|---|
| **Astra** | hostile kills heal half a heart; 10% of projectiles pass through you | **Dimensional Drift**: blink up to 8 blocks (45 s) | **Astral Daggers**: 3 daggers, 4 damage each to mobs (90 s) |
| **Fire** | Fire Resistance; ores you mine come out smelted | **Fireball**: sets mobs alight, no block damage (45 s) | **Cozy Campfire**: Regeneration for you and your team for 10 s (120 s) |
| **Flux** | lightning can't hurt you; Conduit Power in water/rain | **Flux Beam**: zaps and slows the first thing it hits (60 s) | **Static Burst**: knocks everything back (75 s) |
| **Life** | crops near you grow faster; food fills you more | **Vitality Vortex**: Regeneration II for you and your team (90 s) | **Circle of Life**: bone meals every crop and sapling around you (60 s) |
| **Puff** | no fall damage | **Dash**: launch forward (30 s) | **Breezy Bash**: flings mobs into the air; players are only pushed back (90 s) |
| **Speed** | Speed I; Dolphin's Grace while swimming | **Terminal Velocity**: Speed III + Haste II for 15 s (120 s) | **Slipstream**: Speed II for you and your team (120 s) |
| **Strength** | +1 melee damage against **mobs only** (inside the shared cap); 20% knockback resistance | **Frailer**: weakens everything around you (90 s) | **Bloodlust**: 12 s of hits on mobs heal you (120 s) |
| **Wealth** | Luck, Hero of the Village; +10% jobs pay | **Rich Rush**: ores drop double for 30 s (5 min) | **Pockets**: 9 extra storage slots (always) |

**Energy (0–10)** is earned mostly by **playing normally**: every **2,000 skill XP** (any skill) charges **+1**. PvP moves a little: the winner gets 1 and the loser loses 1 (once per pair every 30 minutes, never between teammates; dying any other way costs nothing). Passives need 1 energy, the primary 2, the secondary 4. At 0 the gem sleeps until you recharge it by playing, so PvP-avoidant players are never locked out. 8+ energy shortens cooldowns a little (10%, 15% at 10).

Gems are **soulbound**: they can't be dropped, stored, crafted with or placed, and you keep them when you die. Lost yours? `/gem recover` gives a new copy (old copies stop working). `/gem reroll` swaps it for a random other gem for 5 energy.

Gems have their own textures in a small resource pack (sent next to KushCraft's, never replacing it). Players without it see the vanilla item the gem is made of (amethyst shard, blaze powder…) – everything still works.

## Teams (parties)

Opt-in groups for playing together – no wars, no claims, no forced factions.

* `/party create <name> [TAG] [colour]`, `/party invite <player>` (clickable [Join] / [No thanks]), or make it **open** so anyone can `/party join`.
* Members get the team **colour and [TAG]** on their name tag and in the tab list, and **can't hurt each other** (melee, arrows and gem abilities).
* **Team chat:** `/tc <message>`, or `/party chat` to send everything to the team.
* **Shared XP:** teammates within 48 blocks get **10%** of each other's skill XP on top (nobody loses any; the leader can switch it off).
* **Team stats:** `/party` menu (members, online, total level), `/party info`, `/party list` ranks teams by total skill level.
* Up to 8 members (`teams.max-members`); leaving leaders hand over, the last one out closes the team.

## Voice chat

Install **[Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat)** on the server (its Paper/Bukkit plugin) and players install the Simple Voice Chat **mod** on their client. **Proximity voice** then just works – you hear people near you. SMPSuite adds on top:

* a **private voice group for every team**: `/vc team` (or the button in `/party`),
* **public channels** from the config (Lobby, Builders, Study by default): `/vc join <channel>`,
* `/vc leave` – back to proximity voice.

SMPSuite talks to Simple Voice Chat through its public API (no extra download); without it, `/vc` just says voice chat isn't installed.

## KushCraft

If [KushCraft](../kushcraft) is installed, SMPSuite pays jobs money into its wallet (when there's no Vault), and KushCraft 8.1's **add-on mode** asks SMPSuite for the player's **Farming level** before they can unlock the drug lab (Farming 10 + $2,500 by default). KushCraft's drug income is ×0.35 there, so skills are the better way to earn.

## Commands

| Command | Permission | |
|---|---|---|
| `/skills [player]` (`/sk`) | `smpsuite.use` | the skills menu |
| `/skills top [skill]` | `smpsuite.use` | leaderboard (total level or one skill) |
| `/gem` | `smpsuite.use` | your gem: energy, passives, abilities |
| `/gem use primary\|secondary`, `/gem recover`, `/gem reroll`, `/gem pockets` | `smpsuite.use` | |
| `/party …` (`/teams`, `/pt`) | `smpsuite.use` | teams – `/party help` lists everything |
| `/tc [message]` | `smpsuite.use` | team chat (no message: switch it on/off) |
| `/vc [join <channel> \| team \| leave]` | `smpsuite.use` | voice groups |
| `/smp info \| reload` | `smpsuite.admin` | status (jobs pay, voice, pack) / reload the config |
| `/smp level\|xp <player> <skill> <n>` | `smpsuite.admin` | set a level / give XP |
| `/smp gem <player> <gem>`, `/smp energy <player> <n>`, `/smp cooldowns <player>` | `smpsuite.admin` | |
| `/smp selftest` | console | runs the built-in tests |

## Config

Everything above is a number in `config.yml`: XP per block/mob/catch, the curve, every bonus and its cap, ability durations and cooldowns, the balance caps, jobs pay rates and the hourly cap, gem energy rules and cooldowns, team size and shared XP, voice channels, and the resource pack link. Player data is one small file per player in `plugins/SMPSuite/players/`, teams are in `teams.yml`.

## Building from source

```bash
cd smpsuite
mvn package                    # JDK 25; jar in target/
python3 tools/make_pack.py     # draws the gem textures -> release/SMPSuite-pack-<version>.zip + the jar's copy (needs Pillow)
```

A released `SMPSuite-pack-<version>.zip` must never change (servers on that version download it) – `make_pack.py` refuses to; bump the version first.

GitHub Actions builds SMPSuite and KushCraft and boots a real Paper 26.3 server with both: SMPSuite's self test (the curve, every cap, the damage cap with maxed skills + the Strength gem, gem items and energy, teams and name tags, the placed-block tracker and the tree feller on a real world, pay balance), KushCraft's self test, jobs pay through KushCraft's wallet, the hosted gem pack matching the jar, and – when Modrinth has a Simple Voice Chat build for Paper 26.3 – the voice bridge hooking in.
