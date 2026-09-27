# OptimizationUtils

A minecraft plugin with some useful optimization utils (see below).

## Requirements

- Paper 26.2 (older versions are not supported)
- Java 25+

## Features

- **Dynamic mobcap** - lowers the mobcap in configurable steps as MSPT rises or TPS drops (e.g. 75% at 35ms, 50% at 40ms, 0% at 50ms) and raises it again step by step once the server recovers. Each step can optionally throttle spawners too.
- **Dynamic random tick speed** - automatically turns random ticks off when MSPT exceeds (or TPS drops below) a configurable threshold.
- **Disable entity ticking** - stops ticking selected mobs (they stay in the world, but no AI/movement). Two modes: disabled entity ticking entirely, or uses Bukkit's `Mob#setAware`. Mobs can be filtered by entity type or Bukkit class, with include/exclude lists.
- **Runtime tweaks via commands** - change view distance (globally or per player, persisted), simulation distance, mobcaps, mob spawn frequency and villager tick rates without a restart.
- **Diagnostics** - analyze which chunks hold the most entities, plus a detailed `/ou info` overview.

## Commands

Main command: `/optimizationutils` (aliases: `/ou`, `/opt`)

| Command | Description |
|---------|-------------|
| `/ou info` | Displays server and plugin information (view/simulation distances, entity counts, loaded chunks, tick rates, feature status) |
| `/ou analyzechunks` | Lists the top 10 loaded chunks with the most entities |
| `/ou setviewdistance <distance> [player]` | Sets view distance for all worlds, or for a specific player (persisted across restarts) |
| `/ou resetviewdistance <player>` | Resets a player's view distance to the server default |
| `/ou setsimulationdistance <distance>` | Sets simulation distance for all worlds while respecting despawn ranges |
| `/ou setspawnlimit <spawn category> <limit>` | Sets the mobcap for all worlds |
| `/ou setticksperspawn <spawn category> <ticks>` | Sets mob spawn frequency (ticks between spawn attempts) for all worlds |
| `/ou setvillagersensortickrate <ticks>` | Sets villager sensor tick rate for all worlds |
| `/ou setvillagerbehaviortickrate <ticks>` | Sets villager behavior tick rate for all worlds |
| `/ou reload` | Reloads the configuration |

## Permissions

- `optimizationutils.admin` - Access to all commands

## Configuration

The default configuration lives in [`src/main/resources/config.yml`](src/main/resources/config.yml). On first start it is written to `plugins/OptimizationUtils/config.yml`; settings missing from an existing server config are filled in from it. Apply changes on a running server with `/ou reload`.

## Contributing

You can build the project using IntelliJ IDEA

```bash
gradlew build
```

## Authors

- [EpicPlayerA10](https://github.com/EpicPlayerA10) - original author of the based plugin
- [MarkapToGo](https://github.com/MarkapToGo) - maintainer of the forked plugin :D

## Stats

![](https://bstats.org/signatures/bukkit/OptimizationUtils.svg)
