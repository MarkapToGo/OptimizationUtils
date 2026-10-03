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
- **Chunk analysis** - ranks the loaded chunks by their estimated cost per tick instead of raw counts: ticking entities and block entities weighted by type (a villager costs more than an armor stand, a chest nothing), plus the scheduled block and fluid ticks of redstone clocks and flowing water/lava. Shows who loads each chunk (nearest player, force load or plugin), can search for a type (villager, hopper, repeater, ...) or a player, and has a clickable compact chat view with sorting, pages, details, teleport and CSV export. Scheduled ticks are only counted while an analysis runs, by reading what the server schedules anyway, so normal gameplay is unaffected.
- **Diagnostics** - a detailed `/ou info` overview.

## Commands

Main command: `/optimizationutils` (aliases: `/ou`, `/opt`)

| Command | Description |
|---------|-------------|
| `/ou info` | Displays server and plugin information (view/simulation distances, entity counts, loaded chunks, tick rates, feature status) |
| `/ou analyzechunks [score\|entities\|blockentities\|ticks] [world]` | Ranks the loaded chunks by estimated cost (default), entities, block entities or scheduled ticks per second (all worlds by default). Takes `tickSampleDuration` (2s by default) to count scheduled ticks. Click a chunk for details, its coordinates to teleport |
| `/ou analyzechunks type <type> [world]` | Ranks the chunks by one type, like `villager`, `hopper`, `repeater` or `flowing_water` |
| `/ou analyzechunks player <player>` | Ranks the chunks the player is the nearest one to (in view distance) |
| `/ou analyzechunks here` / `chunk <world> <x> <z>` | Everything about one chunk (chunk coordinates): cost per type, load status, who loads it, clickable positions |
| `/ou analyzechunks show <world> <x> <z>` | One chunk as the last result found it, instantly (what clicking a chunk in the list does); its ↻ button analyzes it again |
| `/ou analyzechunks page [page]` / `sort <sort>` / `export` / `refresh` | Pages through or re-sorts the last result, saves it to `plugins/OptimizationUtils/exports/` as CSV, or runs it again |
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
