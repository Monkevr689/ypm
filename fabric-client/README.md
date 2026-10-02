# YPM Client (Fabric, Minecraft 26.3)

A client-side Fabric mod with aim assist, triggerbot, auto bridge, speed, auto sprint and a HUD, all configured
from an in-game menu.

## Install
1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.3 and drop [Fabric API](https://modrinth.com/mod/fabric-api) into `mods/`.
2. Download `ypm-client-*.jar` from the latest *Build Fabric client* GitHub Actions run (or build it yourself) and put it in `mods/`.

## Controls
| Key | Action |
| --- | --- |
| `Right Ctrl` | Open / close the YPM menu |
| `R` | Toggle aim assist |
| *(unbound)* | Toggle triggerbot / auto bridge / speed / auto sprint |

Rebind any of them under **Options → Controls → Key Binds → YPM Client**.

## Menu tabs
- **Aim**: aim assist (see *How it works*): range, FOV, smoothing, max speed, vertical, hold attack, sticky
  target, stop on target, weapon only.
- **Trigger**: attacks whatever valid target is under your crosshair once your attack cooldown reaches the set
  percentage. Weapon only, pause while using an item.
- **Targets**: shared by Aim and Trigger: players / hostiles / passives, skip invisible, skip teammates, line of
  sight, and priority (angle, distance or lowest health).
- **Bridge**: while you hold blocks, places one under your feet by clicking the side of a neighbouring block
  exactly like a right-click. Only while looking down (min pitch) or sneaking, keep Y, predict, diagonal, off
  hand, auto switch to a hotbar block stack, sneak at edge, swing. It never clicks chests, doors, crafting tables
  or other blocks that would open or toggle instead of placing.
- **Move**: speed multiplier (optionally off while sneaking) and auto sprint.
- **HUD**: enabled-module list (top left/right) and target name + health under the crosshair.

Tab names turn green while their module is on. Everything is saved to `config/ypm-client.json` when you close
the menu; hand-edited values are clamped to their valid ranges on load.

## How it works
While enabled (and, by default, while holding attack), the camera eases toward the nearest valid
target inside a cone around your crosshair. It rotates through the same code path as mouse movement and
runs every frame, so it feels like a gentle pull rather than a snap. If your crosshair is already on the
target's hitbox, pitch is left alone.

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
