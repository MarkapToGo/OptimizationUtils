# OptimizationUtils

A minecraft plugin with some useful optimization utils (see below).

## Requirements

- Paper 26.2 (older versions are not supported)
- Java 25+

## Features

- **Dynamic mobcap** - lowers the mobcap in configurable steps as MSPT rises or TPS drops (e.g. 75% at 35ms, 50% at 40ms, 0% at 50ms) and raises it again step by step once the server recovers. Each step can optionally throttle spawners too.
- **Dynamic view and simulation distance** - lowers the view and/or simulation distance of all worlds in configurable steps as MSPT rises or TPS drops (e.g. simulation distance 6 at 40ms, 4 at 50ms), and raises it again step by step once the server stayed recovered for a while.
- **Dynamic random tick speed** - lowers the random tick speed in configurable steps as MSPT rises or TPS drops (e.g. 1 at 45ms, 0 at 50ms) and raises it again step by step once the server recovers.
- **Lag spike protection** - the dynamic features only react once the server lagged for a configurable time, so a single spike (e.g. a garbage collection pause) is ignored. The MSPT can be taken from the last tick or averaged over 5 seconds, 10 seconds or a minute.
- **Disable entity ticking** - stops ticking selected mobs (they stay in the world, but no AI/movement). Two modes: disabled entity ticking entirely, or uses Bukkit's `Mob#setAware`. Mobs can be filtered by entity type or Bukkit class, with include/exclude lists.
- **Runtime tweaks via commands** - change view distance (globally or per player, persisted), simulation distance, mobcaps, mob spawn frequency and villager tick rates without a restart.
- **Diagnostics** - find the chunks with the most entities and block entities (hoppers, furnaces, ...) across all worlds, grouped by type, plus a detailed `/ou info` overview.

## Commands

Main command: `/optimizationutils` (aliases: `/ou`, `/opt`)

| Command | Description |
|---------|-------------|
| `/ou info` | Displays server and plugin information (view/simulation distances, entity counts, loaded chunks, tick rates, feature status) |
| `/ou analyzechunks [all\|entities\|blockentities] [world]` | Lists the top 10 loaded chunks with the most entities and/or block entities, grouped by type (all worlds by default). Click a chunk to teleport there |
| `/ou setviewdistance <distance> [player]` | Sets view distance for all worlds, or for a specific player (persisted across restarts) |
| `/ou resetviewdistance [player]` | Gives all worlds the view distance from server.properties back, or resets a player's view distance to the one of their world |
| `/ou setsimulationdistance <distance>` | Sets simulation distance for all worlds while respecting despawn ranges (persisted across restarts) |
| `/ou resetsimulationdistance` | Gives all worlds the simulation distance from server.properties back |
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
./gradlew build
```

This also runs the tests (`./gradlew test`). They check the step logic of the dynamic features, and that `src/main/resources/config.yml` has exactly the settings of `PluginConfiguration`.

## Authors

- [EpicPlayerA10](https://github.com/EpicPlayerA10) - original author of the based plugin
- [MarkapToGo](https://github.com/MarkapToGo) - maintainer of the forked plugin :D

## Stats

[![](https://bstats.org/signatures/bukkit/OptimizationUtils-Markap.svg)](https://bstats.org/plugin/bukkit/OptimizationUtils-Markap/34344)
