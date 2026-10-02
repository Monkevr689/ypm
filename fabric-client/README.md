# YPM Client (Fabric, Minecraft 26.3)

A client-side Fabric mod with **aim assist**, **auto bridge** and **speed**, configured from an in-game menu.

## Install
1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.3 and drop [Fabric API](https://modrinth.com/mod/fabric-api) into `mods/`.
2. Download `ypm-client-*.jar` from the latest *Build Fabric client* GitHub Actions run (or build it yourself) and put it in `mods/`.

## Controls
| Key | Action |
| --- | --- |
| `Right Ctrl` | Open / close the YPM menu |
| `R` | Toggle aim assist |
| *(unbound)* | Toggle auto bridge |
| *(unbound)* | Toggle speed |

Rebind any of them under **Options → Controls → Key Binds → YPM Client**.

## Modules
- **Aim Assist**: see *How it works* below.
- **Auto Bridge**: while you hold blocks (main hand, or off hand if enabled), places one under your feet whenever
  there's air there, by clicking the side of a neighbouring block exactly like a right-click would. *Keep Y* keeps the
  bridge level while you jump, *Predict* also fills the block you're about to step onto, and *Diagonal* builds a
  supporting block first when there's nothing directly adjacent to click.
- **Speed**: multiplies your ground movement speed.

## How it works
While enabled (and, by default, while holding attack), the camera eases toward the nearest valid
target inside a cone around your crosshair. It rotates through the same code path as mouse movement and
runs every frame, so it feels like a gentle pull rather than a snap. If your crosshair is already on the
target's hitbox, pitch is left alone.

## Config — `config/ypm-client.json`
Everything is editable in the YPM menu and saved when you close it. Aim assist settings:

| Setting | Default | Meaning |
| --- | --- | --- |
| `enabled` | `false` | Start enabled |
| `range` | `4.5` | Max distance to target (blocks) |
| `fov` | `70` | Cone (degrees, full width) in which targets are picked |
| `smoothing` | `6.0` | Ease rate; higher = snappier |
| `maxSpeed` | `240` | Rotation cap in degrees/second |
| `vertical` | `true` | Also adjust pitch |
| `requireAttackKey` | `true` | Only assist while attack is held |
| `stickyTarget` | `true` | Keep the current target while it remains valid |
| `targetPlayers` / `targetHostiles` / `targetPassives` | `true` / `true` / `false` | Target types |
| `ignoreInvisible` / `ignoreTeammates` | `true` / `true` | Filters |
| `requireLineOfSight` | `true` | Skip targets behind walls |

Auto bridge: `placeDelay` (1 tick), `reach` (4.5), `keepY`, `predict`, `diagonal`, `useOffhand` (all on).
Speed: `multiplier` (1.3).

## Build
Requires JDK 25.
```
./gradlew build
```
The jar lands in `build/libs/`. Version numbers live in `gradle.properties`; see
<https://fabricmc.net/develop> for the latest loader/Fabric API versions.

> These modules are not hidden from anti-cheat: automated placement, aim correction and modified speed are all
> visible to a server in your movement and interaction packets. Most multiplayer servers forbid them. Use them in
> singleplayer or on servers that allow it.
