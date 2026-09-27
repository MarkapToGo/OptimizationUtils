package com.markaptogo.optimizationutils.manager;

import com.markaptogo.optimizationutils.OptimizationUtils;
import com.markaptogo.optimizationutils.config.PluginConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lowers the random tick speed of every world in steps while the server lags more, and raises it again step by step
 * once it recovers. Never raises it above its normal value.
 * <p>
 * The random tick speed is a game rule, which is saved with the world. So the normal one is kept in data.yml
 * (by world name), to give it back after a crash or while the world was not loaded.
 */
public final class DynamicRandomTickManager {

    /**
     * The lowered random tick speed we set, by world name, so changes made by others can be told apart.
     */
    private static final Map<String, Integer> APPLIED = new HashMap<>();

    private static BukkitTask task = null;
    private static StepTracker<PluginConfiguration.RandomTickStep> tracker = null;
    private static boolean dirty = false;

    private DynamicRandomTickManager() {
    }

    /**
     * Starts or stops adjusting the random tick speed depending on the current configuration.
     */
    public static void sync() {
        disable();

        PluginConfiguration.DynamicRandomTickSpeed config = config();
        if (!config.enabled) return;

        tracker = new StepTracker<>(config.metric, config.triggerDelay, config.recoveryMargin, config.recoveryDelay, config.steps, step -> step.threshold);

        long interval = Math.max(1, config.checkInterval);
        task = Bukkit.getScheduler().runTaskTimer(OptimizationUtils.instance(), DynamicRandomTickManager::update, interval, interval);
    }

    /**
     * Stops adjusting the random tick speed and gives every loaded world its normal one back. Worlds that are not
     * loaded get it back once they are loaded again.
     */
    public static void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        tracker = null;
        for (String worldName : List.copyOf(originals().keySet())) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                restore(world);
            }
        }
        APPLIED.clear();
        save();
    }

    /**
     * Gives a world that was left with a lowered random tick speed (e.g. by a crash) its normal one back.
     */
    public static void onWorldLoad(World world) {
        if (APPLIED.containsKey(world.getName())) return;

        restore(world);
        save();
    }

    /**
     * Returns the maximum random tick speed of the active step, or -1 when the normal one is used.
     */
    public static int currentMaxSpeed() {
        PluginConfiguration.RandomTickStep step = tracker == null ? null : tracker.activeStep();
        return step == null ? -1 : Math.max(0, step.randomTickSpeed);
    }

    private static void update() {
        if (tracker.update()) {
            int maxSpeed = currentMaxSpeed();
            OptimizationUtils.instance().getLogger().info("Server is at " + tracker.formattedValue() + ", "
                + (maxSpeed < 0 ? "restoring normal random tick speed" : "limiting random tick speed to " + maxSpeed));
        }

        // Also runs without a step change, so worlds loaded in the meantime are covered
        int maxSpeed = currentMaxSpeed();
        for (World world : Bukkit.getWorlds()) {
            if (maxSpeed < 0) {
                restore(world);
            } else {
                apply(world, maxSpeed);
            }
        }
        save();
    }

    private static void apply(World world, int maxSpeed) {
        String worldName = world.getName();
        Integer applied = APPLIED.get(worldName);
        if (applied == null) {
            // Left lowered by a previous run, start from the normal random tick speed
            restore(world);
        }

        int current = getSpeed(world);
        Integer base = originals().get(worldName);

        // Changed by someone else (e.g. /gamerule), take it as the new normal random tick speed
        if (applied != null && applied != current) {
            forget(worldName);
            base = null;
        }

        if (base == null) {
            if (current <= maxSpeed) return;

            base = current;
            originals().put(worldName, base);
            dirty = true;
            debug("Storing normal random tick speed of world " + worldName + ": " + base);
        }

        int target = Math.min(base, maxSpeed);
        if (target == base) {
            restore(world);
            return;
        }

        if (target != current) {
            world.setGameRule(GameRules.RANDOM_TICK_SPEED, target);
        }
        APPLIED.put(worldName, target);
    }

    private static void restore(World world) {
        String worldName = world.getName();
        Integer applied = APPLIED.get(worldName);
        Integer base = originals().get(worldName);
        forget(worldName);
        if (base == null) return;

        // Keep a random tick speed changed by others in the meantime. Unknown after a restart, then the stored one is used.
        if (applied == null || getSpeed(world) == applied) {
            world.setGameRule(GameRules.RANDOM_TICK_SPEED, base);
            debug("Restoring normal random tick speed of world " + worldName + ": " + base);
        }
    }

    private static void forget(String worldName) {
        APPLIED.remove(worldName);
        if (originals().remove(worldName) != null) {
            dirty = true;
        }
    }

    private static int getSpeed(World world) {
        return world.getGameRuleValue(GameRules.RANDOM_TICK_SPEED);
    }

    private static void save() {
        if (!dirty) return;

        OptimizationUtils.instance().dataConfiguration().save();
        dirty = false;
    }

    private static void debug(String message) {
        if (OptimizationUtils.instance().pluginConfiguration().debug) {
            OptimizationUtils.instance().getLogger().info(message);
        }
    }

    private static Map<String, Integer> originals() {
        return OptimizationUtils.instance().dataConfiguration().originalRandomTickSpeeds;
    }

    private static PluginConfiguration.DynamicRandomTickSpeed config() {
        return OptimizationUtils.instance().pluginConfiguration().dynamicRandomTickSpeed;
    }
}
