package com.markaptogo.optimizationutils.manager;

import com.markaptogo.optimizationutils.OptimizationUtils;
import com.markaptogo.optimizationutils.config.PluginConfiguration;
import com.markaptogo.optimizationutils.config.model.PerformanceMetric;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.scheduler.BukkitTask;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lowers the mobcap of every world in steps while the server lags more, and raises it again step by step once it recovers.
 */
public final class DynamicMobcapManager {

    /**
     * The normal spawn limit of a category and the scaled one we set, so changes made by others can be told apart.
     */
    private record Limit(int base, int applied) {
    }

    private static final Map<UUID, Map<SpawnCategory, Limit>> LIMITS = new HashMap<>();

    private static BukkitTask task = null;
    private static PerformanceMetric metric = PerformanceMetric.MSPT;
    /**
     * Sorted from the least to the most laggy threshold.
     */
    private static List<PluginConfiguration.MobcapStep> steps = List.of();
    private static List<SpawnCategory> categories = List.of();

    /**
     * Index of the active step in {@link #steps}, or -1 when the normal mobcap is used.
     */
    private static int activeStep = -1;

    private DynamicMobcapManager() {
    }

    /**
     * Starts or stops adjusting the mobcap depending on the current configuration.
     */
    public static void sync() {
        disable();

        PluginConfiguration.DynamicMobcap config = config();
        if (!config.enabled) return;

        metric = config.metric;
        Comparator<PluginConfiguration.MobcapStep> leastLaggyFirst = Comparator.comparingDouble(step -> step.threshold);
        if (metric == PerformanceMetric.TPS) {
            // Lower TPS is laggier
            leastLaggyFirst = leastLaggyFirst.reversed();
        }
        steps = config.steps.stream()
            .sorted(leastLaggyFirst)
            .toList();
        // MISC has no spawn limit, World#setSpawnLimit throws for it
        categories = config.categories.stream()
            .filter(category -> category != null && category != SpawnCategory.MISC)
            .distinct()
            .toList();

        long interval = Math.max(1, config.checkInterval);
        task = Bukkit.getScheduler().runTaskTimer(OptimizationUtils.instance(), DynamicMobcapManager::update, interval, interval);
    }

    /**
     * Stops adjusting the mobcap and gives every world its normal mobcap back.
     */
    public static void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        activeStep = -1;
        restoreAll();
    }

    /**
     * Returns the mobcap in percent of the normal one, 100 when not throttled.
     */
    public static int currentPercent() {
        return activeStep < 0 ? 100 : percent(steps.get(activeStep));
    }

    public static boolean shouldThrottleSpawners() {
        return activeStep >= 0 && steps.get(activeStep).throttleSpawners;
    }

    private static void update() {
        double value = ThrottleUtils.getValue(metric);

        // Laggiest step whose threshold is reached
        int reachedStep = -1;
        for (int i = 0; i < steps.size(); i++) {
            if (ThrottleUtils.isReached(metric, value, steps.get(i).threshold)) reachedStep = i;
        }

        int newStep = activeStep;
        if (reachedStep > activeStep) {
            // Lower the mobcap right away
            newStep = reachedStep;
        } else if (activeStep >= 0 && ThrottleUtils.isRecovered(metric, value, steps.get(activeStep).threshold, config().recoveryMargin)) {
            // Raise it again one step at a time
            newStep = activeStep - 1;
        }

        if (newStep != activeStep) {
            activeStep = newStep;
            OptimizationUtils.instance().getLogger().info("Server is at " + ThrottleUtils.format(metric, value) + ", setting mobcap to " + currentPercent() + "% of normal");
        }

        // Also runs without a step change, so worlds loaded in the meantime are covered
        apply();
    }

    private static void apply() {
        LIMITS.keySet().removeIf(worldId -> Bukkit.getWorld(worldId) == null);

        for (World world : Bukkit.getWorlds()) {
            if (activeStep < 0) {
                restore(world);
                continue;
            }

            int percent = percent(steps.get(activeStep));
            Map<SpawnCategory, Limit> limits = LIMITS.computeIfAbsent(world.getUID(), id -> new EnumMap<>(SpawnCategory.class));

            for (SpawnCategory category : categories) {
                int current = world.getSpawnLimit(category);
                Limit limit = limits.get(category);

                // Take the current limit as the normal one, unless it is the one we set (e.g. changed by /ou setspawnlimit)
                int base = limit != null && limit.applied() == current ? limit.base() : current;
                if (base < 0) continue;

                int scaled = (int) Math.round(base * percent / 100.0);
                if (scaled != current) {
                    world.setSpawnLimit(category, scaled);
                }
                limits.put(category, new Limit(base, scaled));
            }
        }
    }

    private static void restoreAll() {
        for (UUID worldId : List.copyOf(LIMITS.keySet())) {
            World world = Bukkit.getWorld(worldId);
            if (world != null) {
                restore(world);
            }
        }

        LIMITS.clear();
    }

    private static void restore(World world) {
        Map<SpawnCategory, Limit> limits = LIMITS.remove(world.getUID());
        if (limits == null) return;

        for (Map.Entry<SpawnCategory, Limit> entry : limits.entrySet()) {
            // Keep limits that were changed by others in the meantime
            if (world.getSpawnLimit(entry.getKey()) == entry.getValue().applied()) {
                world.setSpawnLimit(entry.getKey(), entry.getValue().base());
            }
        }
    }

    private static int percent(PluginConfiguration.MobcapStep step) {
        return Math.clamp(step.mobcapPercent, 0, 100);
    }

    private static PluginConfiguration.DynamicMobcap config() {
        return OptimizationUtils.instance().pluginConfiguration().dynamicMobcap;
    }
}
