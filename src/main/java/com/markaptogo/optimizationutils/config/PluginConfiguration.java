package com.markaptogo.optimizationutils.config;

import com.markaptogo.optimizationutils.config.model.FilterMode;
import com.markaptogo.optimizationutils.config.model.MsptCalculationMode;
import com.markaptogo.optimizationutils.config.model.PerformanceMetric;
import com.markaptogo.optimizationutils.config.model.TickingDisableMode;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.Header;
import org.bukkit.entity.SpawnCategory;

import java.util.ArrayList;
import java.util.List;

@Header("A config file for the plugin.")
@Header("")
public class PluginConfiguration extends OkaeriConfig {
    @Comment("")
    @Comment("Debug mode for the plugin.")
    public boolean debug = false;

    @Comment("")
    @Comment("The method used to calculate the MSPT for dynamic features. Also applies to TPS, which is calculated from the MSPT.")
    @Comment(" - AVERAGE_5S - Uses the average MSPT over the last 5 seconds.")
    @Comment(" - LAST_TICK - Uses the current MSPT of the last tick.")
    public MsptCalculationMode msptCalculationMode = MsptCalculationMode.AVERAGE_5S;

    @Comment("")
    @Comment("This feature allows the plugin to dynamically adjust the mobcap based on server performance.")
    @Comment("The mobcap is lowered in steps while the server lags more, and raised again step by step once it recovers.")
    public DynamicMobcap dynamicMobcap = new DynamicMobcap();

    public static class DynamicMobcap extends OkaeriConfig {
        public boolean enabled = true;

        @Comment("")
        @Comment("What the thresholds below are compared against.")
        @Comment(" - MSPT - milliseconds per tick, higher is worse. Thresholds are in milliseconds (50 = the server starts to lag).")
        @Comment(" - TPS - ticks per second, lower is worse. Thresholds are in TPS (20 = no lag).")
        @Comment("Change the thresholds and recoveryMargin as well when switching, e.g. for TPS: 19 -> 75%, 17 -> 50%, 15 -> 25%, 12 -> 0%.")
        public PerformanceMetric metric = PerformanceMetric.MSPT;

        @Comment("")
        @Comment("How often the server performance is checked and the mobcap adjusted, in ticks (20 ticks = 1 second).")
        public int checkInterval = 20;

        @Comment("")
        @Comment("How far (in the unit of the metric) the server has to recover past the threshold of the active step before the")
        @Comment("mobcap is raised to the previous step. Stops the mobcap from flipping back and forth around a threshold.")
        public float recoveryMargin = 2.0f;

        @Comment("")
        @Comment("The spawn categories whose mobcap is adjusted.")
        @Comment("Available: MONSTER, ANIMAL, WATER_ANIMAL, WATER_AMBIENT, WATER_UNDERGROUND_CREATURE, AMBIENT, AXOLOTL")
        public List<SpawnCategory> categories = new ArrayList<>(List.of(
            SpawnCategory.MONSTER,
            SpawnCategory.ANIMAL,
            SpawnCategory.WATER_ANIMAL,
            SpawnCategory.WATER_AMBIENT,
            SpawnCategory.WATER_UNDERGROUND_CREATURE,
            SpawnCategory.AMBIENT,
            SpawnCategory.AXOLOTL
        ));

        @Comment("")
        @Comment("The steps. The laggiest step whose threshold is reached is active, before the first one the normal mobcap is used.")
        @Comment(" - threshold - the MSPT (reached when at or above) or TPS (reached when at or below) from which on this step is active")
        @Comment(" - mobcapPercent - the mobcap of the categories above, in percent of their normal mobcap (0 stops natural spawning)")
        @Comment(" - throttleSpawners - if spawners should stop spawning while this step is active")
        public List<MobcapStep> steps = new ArrayList<>(List.of(
            new MobcapStep(35.0f, 75, false),
            new MobcapStep(40.0f, 50, false),
            new MobcapStep(45.0f, 25, false),
            new MobcapStep(50.0f, 0, false)
        ));
    }

    public static class MobcapStep extends OkaeriConfig {
        public float threshold;
        public int mobcapPercent;
        public boolean throttleSpawners;

        public MobcapStep() {
        }

        public MobcapStep(float threshold, int mobcapPercent, boolean throttleSpawners) {
            this.threshold = threshold;
            this.mobcapPercent = mobcapPercent;
            this.throttleSpawners = throttleSpawners;
        }
    }

    @Comment("")
    @Comment("Stops ticking the selected mobs. They stay in the world but are not ticked by the server (no AI, no movement).")
    public DisableEntityTicking disableEntityTicking = new DisableEntityTicking();

    public static class DisableEntityTicking extends OkaeriConfig {
        public boolean enabled = false;

        @Comment("")
        @Comment("How ticking is disabled.")
        @Comment(" - ALL_TICKING - removes the mob from the server's entity tick list, so nothing about it is ticked at all.")
        @Comment("                 Nothing is saved to the mob, everything comes back on its own after a restart.")
        @Comment(" - BUKKIT_AWARE - uses Bukkit's Mob#setAware, so only the mob's AI is skipped (it still falls, burns, despawns, ...).")
        @Comment("                  Beware: this is saved to the mob (Bukkit.Aware), so mobs made unaware by other plugins or commands are restored as aware too.")
        public TickingDisableMode mode = TickingDisableMode.ALL_TICKING;

        @Comment("")
        @Comment("How the list below is interpreted.")
        @Comment(" - INCLUDE - only mobs matching the list are not ticked.")
        @Comment(" - EXCLUDE - every mob except those matching the list is not ticked.")
        public FilterMode filterMode = FilterMode.INCLUDE;

        @Comment("")
        @Comment("Mobs that should not be ticked. Accepted values:")
        @Comment(" - ALL - every mob")
        @Comment(" - type:COW - a concrete entity type (ZOMBIE, COW, VILLAGER, ...)")
        @Comment(" - bukkit_class:Monster - a Bukkit interface from org.bukkit.entity (Mob, Monster, Animals, Raider, ...), case sensitive. May break between versions.")
        @Comment("   See: https://jd.papermc.io/paper/org/bukkit/entity/package-summary.html#class-summary")
        public List<String> entities = new ArrayList<>(List.of("bukkit_class:Animals"));
    }

    @Comment("")
    @Comment("This feature allows the plugin to dynamically turn on or off random tick speed.")
    public DynamicRandomTickSpeed dynamicRandomTickSpeed = new DynamicRandomTickSpeed();

    public static class DynamicRandomTickSpeed extends OkaeriConfig {
        public boolean enabled = false;

        @Comment("")
        @Comment("What the threshold below is compared against.")
        @Comment(" - MSPT - milliseconds per tick, random ticks are turned off at or above the threshold.")
        @Comment(" - TPS - ticks per second, random ticks are turned off at or below the threshold.")
        public PerformanceMetric metric = PerformanceMetric.MSPT;

        @Comment("")
        @Comment("The MSPT in milliseconds or the TPS (depending on the metric) at which random ticks are turned off.")
        public float threshold = 45.0f;
    }
}
