package com.markaptogo.optimizationutils.manager;

import com.markaptogo.optimizationutils.OptimizationUtils;
import com.markaptogo.optimizationutils.config.PluginConfiguration;
import com.markaptogo.optimizationutils.config.model.TickingDisableMode;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.entity.EntityTickList;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Stops the configured mobs from being ticked, without removing them from the world.
 */
public final class EntityTickManager {

    private static final Field ENTITY_TICK_LIST_FIELD;

    /**
     * Marks mobs frozen by {@link TickingDisableMode#BUKKIT_AWARE}, so they can be restored later even
     * after a restart or a crash. Not needed for {@link TickingDisableMode#ALL_TICKING}, which changes
     * nothing that is saved to the mob.
     */
    private static NamespacedKey frozenKey = null;

    static {
        try {
            ENTITY_TICK_LIST_FIELD = ServerLevel.class.getDeclaredField("entityTickList");
            ENTITY_TICK_LIST_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Failed to get entityTickList field from ServerLevel", e);
        }
    }

    /**
     * Mobs added to a world while {@link TickingDisableMode#ALL_TICKING} runs. The server puts a mob into the tick
     * list right after {@link #onMobAddedToWorld(Mob)}, so they are taken out on the next tick.
     */
    private static final List<net.minecraft.world.entity.Entity> PENDING = new ArrayList<>();

    /**
     * The mob types to freeze, worked out from the configuration once instead of for every mob.
     */
    private static Set<EntityType> frozenTypes = EnumSet.noneOf(EntityType.class);

    private static BukkitTask sweepTask = null;
    private static BukkitTask pendingTask = null;
    private static TickingDisableMode runningMode = null;

    private EntityTickManager() {
    }

    public static boolean isRunning() {
        return runningMode != null;
    }

    /**
     * Starts or stops unticking depending on the current configuration. Restarts when it is running already,
     * so mobs that no longer match the configuration get their ticking back.
     */
    public static void sync() {
        disable();

        PluginConfiguration.DisableEntityTicking config = config();
        if (config.enabled) {
            enable(config);
        }
    }

    /**
     * Starts unticking the configured mobs.
     */
    private static void enable(PluginConfiguration.DisableEntityTicking config) {
        runningMode = config.mode;
        frozenTypes = MobTypeFilter.matchingTypes(config.entities, config.filterMode,
            message -> OptimizationUtils.instance().getLogger().warning(message));

        freezeAll();

        // The server puts mobs back into the tick list when their chunk starts ticking entities again (e.g. a player
        // comes closer), without any event. Unaware mobs stay unaware, so BUKKIT_AWARE needs no sweep.
        if (runningMode == TickingDisableMode.ALL_TICKING) {
            sweepTask = Bukkit.getScheduler().runTaskTimer(OptimizationUtils.instance(), EntityTickManager::sweepTickLists, 20L, 20L);
        }
    }

    /**
     * Stops unticking and gives every affected mob its ticking back.
     */
    public static void disable() {
        if (!isRunning()) return;

        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
        if (pendingTask != null) {
            pendingTask.cancel();
            pendingTask = null;
        }
        PENDING.clear();
        runningMode = null;
        frozenTypes = EnumSet.noneOf(EntityType.class);

        for (World world : Bukkit.getWorlds()) {
            EntityTickList tickList = getTickList(world);

            for (Mob mob : world.getEntitiesByClass(Mob.class)) {
                unfreeze(mob, tickList);
            }
        }
    }

    /**
     * Called when a mob is added to a world. Freezes it when the feature is on,
     * or restores it when it was left frozen by a previous run.
     */
    public static void onMobAddedToWorld(Mob mob) {
        if (isRunning()) {
            if (frozenTypes.contains(mob.getType())) freeze(mob);
        } else if (isMarkedFrozen(mob)) {
            // Heal mobs left frozen by a crash or a config change
            unfreezeAware(mob);
        }
    }

    /**
     * Stops ticking every matching mob that is in a world already.
     */
    private static void freezeAll() {
        for (World world : Bukkit.getWorlds()) {
            for (Mob mob : world.getEntitiesByClass(Mob.class)) {
                if (frozenTypes.contains(mob.getType())) freeze(mob);
            }
        }
    }

    /**
     * Takes the matching mobs out of the tick lists. Frozen mobs are not in there, so this only goes through
     * the entities that are ticked right now.
     */
    private static void sweepTickLists() {
        List<net.minecraft.world.entity.Entity> toFreeze = new ArrayList<>();

        for (World world : Bukkit.getWorlds()) {
            EntityTickList tickList = getTickList(world);
            tickList.forEach(entity -> {
                if (entity instanceof net.minecraft.world.entity.Mob && frozenTypes.contains(entity.getBukkitEntity().getType())) {
                    toFreeze.add(entity);
                }
            });

            toFreeze.forEach(tickList::remove);
            toFreeze.clear();
        }
    }

    private static void freeze(Mob mob) {
        if (runningMode == TickingDisableMode.BUKKIT_AWARE) {
            if (isMarkedFrozen(mob)) return;

            mob.setAware(false);
            mob.getPersistentDataContainer().set(frozenKey(), PersistentDataType.BYTE, (byte) 1);
        } else {
            // Clear a marker left behind by a previous BUKKIT_AWARE run
            unfreezeAware(mob);

            freezeNextTick(ReflectionUtils.getNMSEntity(mob));
        }
    }

    private static void freezeNextTick(net.minecraft.world.entity.Entity entity) {
        PENDING.add(entity);

        if (pendingTask == null) {
            pendingTask = Bukkit.getScheduler().runTask(OptimizationUtils.instance(), EntityTickManager::freezePending);
        }
    }

    private static void freezePending() {
        pendingTask = null;

        for (net.minecraft.world.entity.Entity entity : PENDING) {
            // Removing a mob that is not in the tick list (e.g. its chunk does not tick entities) is a no-op
            if (!entity.isRemoved()) {
                getTickList((ServerLevel) entity.level()).remove(entity);
            }
        }
        PENDING.clear();
    }

    private static void unfreeze(Mob mob, EntityTickList tickList) {
        unfreezeAware(mob);

        if (mob.getChunk().getLoadLevel() == Chunk.LoadLevel.ENTITY_TICKING) {
            // Adding a mob that is already in the list is a no-op
            tickList.add(ReflectionUtils.getNMSEntity(mob));
        }
    }

    /**
     * Makes the mob aware again, but only when we are the ones who made it unaware.
     */
    private static void unfreezeAware(Mob mob) {
        if (!isMarkedFrozen(mob)) return;

        mob.setAware(true);
        mob.getPersistentDataContainer().remove(frozenKey());
    }

    private static boolean isMarkedFrozen(Mob mob) {
        return mob.getPersistentDataContainer().has(frozenKey(), PersistentDataType.BYTE);
    }

    private static EntityTickList getTickList(World world) {
        return getTickList(ReflectionUtils.getNMSWorld(world));
    }

    private static EntityTickList getTickList(ServerLevel level) {
        try {
            return (EntityTickList) ENTITY_TICK_LIST_FIELD.get(level);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static NamespacedKey frozenKey() {
        if (frozenKey == null) {
            frozenKey = new NamespacedKey(OptimizationUtils.instance(), "frozen");
        }

        return frozenKey;
    }

    private static PluginConfiguration.DisableEntityTicking config() {
        return OptimizationUtils.instance().pluginConfiguration().disableEntityTicking;
    }
}
