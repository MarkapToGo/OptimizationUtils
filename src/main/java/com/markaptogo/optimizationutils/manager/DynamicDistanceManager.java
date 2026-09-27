package com.markaptogo.optimizationutils.manager;

import com.markaptogo.optimizationutils.OptimizationUtils;
import com.markaptogo.optimizationutils.config.PluginConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lowers the view or simulation distance of every world in steps while the server lags more, and raises it again
 * step by step once it recovers. Never raises a distance above its normal value.
 */
public final class DynamicDistanceManager {

    public static final DynamicDistanceManager VIEW = new DynamicDistanceManager(false);
    public static final DynamicDistanceManager SIMULATION = new DynamicDistanceManager(true);

    // Limits of World#setViewDistance and World#setSimulationDistance
    private static final int MIN_DISTANCE = 2;
    private static final int MAX_DISTANCE = 32;

    /**
     * The normal distance of a world and the lowered one we set, so changes made by others can be told apart.
     * For the simulation distance, also the spawn ranges the world had before we changed them.
     */
    private record State(int base, int applied, NMSUtils.SpawnRanges spawnRanges) {
    }

    private final boolean simulation;
    private final Map<UUID, State> states = new HashMap<>();

    private BukkitTask task = null;
    private StepTracker<PluginConfiguration.DistanceStep> tracker = null;

    private DynamicDistanceManager(boolean simulation) {
        this.simulation = simulation;
    }

    /**
     * Starts or stops adjusting the distance depending on the current configuration.
     */
    public void sync() {
        disable();

        PluginConfiguration.DynamicDistance config = config();
        if (!config.enabled) return;

        tracker = new StepTracker<>(config.metric, config.recoveryMargin, config.recoveryDelay, config.steps, step -> step.threshold);

        long interval = Math.max(1, config.checkInterval);
        task = Bukkit.getScheduler().runTaskTimer(OptimizationUtils.instance(), this::update, interval, interval);
    }

    /**
     * Stops adjusting the distance and gives every world its normal distance back.
     */
    public void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        tracker = null;
        for (UUID worldId : List.copyOf(states.keySet())) {
            World world = Bukkit.getWorld(worldId);
            if (world != null) {
                restore(world);
            }
        }
        states.clear();
    }

    /**
     * Returns the maximum distance of the active step, or -1 when the normal distance is used.
     */
    public int currentMaxDistance() {
        PluginConfiguration.DistanceStep step = tracker == null ? null : tracker.activeStep();
        return step == null ? -1 : clamp(step.distance);
    }

    private void update() {
        if (tracker.update()) {
            int maxDistance = currentMaxDistance();
            OptimizationUtils.instance().getLogger().info("Server is at " + tracker.formattedValue() + ", "
                + (maxDistance < 0 ? "restoring normal " + name() : "limiting " + name() + " to " + maxDistance));
        }

        // Also runs without a step change, so worlds loaded in the meantime are covered
        states.keySet().removeIf(worldId -> Bukkit.getWorld(worldId) == null);
        int maxDistance = currentMaxDistance();
        for (World world : Bukkit.getWorlds()) {
            if (maxDistance < 0) {
                restore(world);
            } else {
                apply(world, maxDistance);
            }
        }
    }

    private void apply(World world, int maxDistance) {
        int current = getDistance(world);
        State state = states.get(world.getUID());

        // Changed by someone else (e.g. /ou setviewdistance), take it as the new normal distance
        if (state != null && state.applied() != current) {
            states.remove(world.getUID());
            state = null;
        }

        if (state == null) {
            if (current <= maxDistance) return;
            state = new State(current, current, simulation ? NMSUtils.getNMSSpawnRanges(world) : null);
        }

        int target = Math.min(state.base(), maxDistance);
        if (target == state.base()) {
            restore(world);
            return;
        }

        if (target != current) {
            setDistance(world, target);
            if (simulation) {
                NMSUtils.setNMSSimulationDistance(world, target);
            }
        }
        states.put(world.getUID(), new State(state.base(), target, state.spawnRanges()));
    }

    private void restore(World world) {
        State state = states.remove(world.getUID());
        if (state == null) return;

        // Keep distances that were changed by others in the meantime
        if (getDistance(world) != state.applied()) return;

        setDistance(world, state.base());
        if (state.spawnRanges() != null) {
            NMSUtils.setNMSSpawnRanges(world, state.spawnRanges());
        }
    }

    private int getDistance(World world) {
        return simulation ? world.getSimulationDistance() : world.getViewDistance();
    }

    private void setDistance(World world, int distance) {
        if (simulation) {
            world.setSimulationDistance(distance);
        } else {
            world.setViewDistance(distance);
        }
    }

    private String name() {
        return simulation ? "simulation distance" : "view distance";
    }

    private static int clamp(int distance) {
        return Math.clamp(distance, MIN_DISTANCE, MAX_DISTANCE);
    }

    private PluginConfiguration.DynamicDistance config() {
        PluginConfiguration configuration = OptimizationUtils.instance().pluginConfiguration();
        return simulation ? configuration.dynamicSimulationDistance : configuration.dynamicViewDistance;
    }
}
