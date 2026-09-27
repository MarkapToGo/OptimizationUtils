package com.markaptogo.optimizationutils.manager;

import com.markaptogo.optimizationutils.OptimizationUtils;
import com.markaptogo.optimizationutils.config.PluginConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.scheduler.BukkitTask;

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
    private static StepTracker<PluginConfiguration.MobcapStep> tracker = null;
    private static List<SpawnCategory> categories = List.of();

    private DynamicMobcapManager() {
    }

    /**
     * Starts or stops adjusting the mobcap depending on the current configuration.
     */
    public static void sync() {
        // Keep the active step, so a reload does not give back the normal mobcap until a later check lowers it again
        int activeStep = tracker == null ? -1 : tracker.activeStepIndex();
        disable();

        PluginConfiguration.DynamicMobcap config = config();
        if (!config.enabled) return;

        tracker = new StepTracker<>(config.metric, config.triggerDelay, config.recoveryMargin, config.recoveryDelay, config.steps, step -> step.threshold);
        tracker.setActiveStep(activeStep);
        ThrottleUtils.warnAboutAlwaysReachedSteps("dynamicMobcap", tracker);
        // MISC has no spawn limit, World#setSpawnLimit throws for it
        categories = config.categories.stream()
            .filter(category -> category != null && category != SpawnCategory.MISC)
            .distinct()
            .toList();

        long interval = Math.max(1, config.checkInterval);
        task = Bukkit.getScheduler().runTaskTimer(OptimizationUtils.instance(), DynamicMobcapManager::update, interval, interval);

        // In the same tick as disable(), so spawning never sees the normal mobcap in between
        applyActiveStep();
    }

    /**
     * Stops adjusting the mobcap and gives every world its normal mobcap back.
     */
    public static void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        tracker = null;
        restoreAll();
    }

    /**
     * Returns the mobcap in percent of the normal one, 100 when not throttled.
     */
    public static int currentPercent() {
        PluginConfiguration.MobcapStep step = activeStep();
        return step == null ? 100 : percent(step);
    }

    public static boolean shouldThrottleSpawners() {
        PluginConfiguration.MobcapStep step = activeStep();
        return step != null && step.throttleSpawners;
    }

    private static PluginConfiguration.MobcapStep activeStep() {
        return tracker == null ? null : tracker.activeStep();
    }

    private static void update() {
        if (tracker.update()) {
            OptimizationUtils.instance().getLogger().info("Server is at " + tracker.formattedValue() + ", setting mobcap to " + currentPercent() + "% of normal");
        }

        // Also runs without a step change, so worlds loaded in the meantime are covered
        applyActiveStep();
    }

    /**
     * Scales the mobcap of every world to the active step, or gives them their normal mobcap back when no step is
     * active. Also takes a mobcap changed in the meantime (e.g. by /ou setspawnlimit) as the new normal one.
     */
    public static void applyActiveStep() {
        LIMITS.keySet().removeIf(worldId -> Bukkit.getWorld(worldId) == null);

        PluginConfiguration.MobcapStep step = activeStep();
        for (World world : Bukkit.getWorlds()) {
            if (step == null) {
                restore(world);
                continue;
            }

            int percent = percent(step);
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
