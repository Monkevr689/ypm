# YPM Client (Fabric, Minecraft 26.3)

A client-side Fabric mod with a smooth, configurable **aim assist**.

## Install
1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.3 and drop [Fabric API](https://modrinth.com/mod/fabric-api) into `mods/`.
2. Download `ypm-client-*.jar` from the latest *Build Fabric client* GitHub Actions run (or build it yourself) and put it in `mods/`.

## Controls
| Key | Action |
| --- | --- |
| `R` | Toggle aim assist |
| *(unbound)* | Reload config |

Both can be rebound under **Options → Controls → Key Binds → YPM Client**.

## How it works
While enabled (and, by default, while holding attack), the camera eases toward the nearest valid
target inside a cone around your crosshair. It rotates through the same code path as mouse movement and
runs every frame, so it feels like a gentle pull rather than a snap. If your crosshair is already on the
target's hitbox, pitch is left alone.

## Config — `config/ypm-client.json`
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

Edit the file and press the reload key to apply without restarting.

## Build
Requires JDK 25.
```
./gradlew build
```
The jar lands in `build/libs/`. Version numbers live in `gradle.properties`; see
<https://fabricmc.net/develop> for the latest loader/Fabric API versions.

> Most multiplayer servers forbid aim assist and anti-cheat plugins can detect it. Use it in singleplayer
> or on servers that allow it.
