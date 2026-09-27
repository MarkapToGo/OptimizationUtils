package com.markaptogo.optimizationutils.manager;

import io.papermc.paper.configuration.WorldConfiguration;
import io.papermc.paper.configuration.type.DespawnRange;
import io.papermc.paper.configuration.type.number.IntOr;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import org.bukkit.World;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.OptionalInt;

public class NMSUtils {

    /**
     * Sets the simulation distance for the given world and updates related configurations.
     */
    public static void setNMSSimulationDistance(World world, int newSimulationDistance) {
        // Set other values
        // All these calculations come from: https://paper-chan.moe/paper-optimization/?ref=paper-chan.moe#despawn-ranges-notes
        int configBasedSimulationDistance = Math.min(newSimulationDistance, 9);
        ServerLevel serverLevel = ReflectionUtils.getNMSWorld(world);
        // Set mob spawn range
        int mobSpawnRange = Math.max(3, Math.min(8, configBasedSimulationDistance - 1));
        serverLevel.spigotConfig.mobSpawnRange = (byte) mobSpawnRange;

        // Set monster despawn range
        WorldConfiguration.Entities.Spawning.DespawnRangePair oldDespawnRangePair = serverLevel.paperConfig().entities.spawning.despawnRanges.get(MobCategory.MONSTER);

        // Based on the (clamped) mob spawn range, so monsters never spawn outside the hard despawn range
        // and get removed right away (happened for simulation distances below 4)
        IntOr.Default horizontalLimit = new IntOr.Default(OptionalInt.of(mobSpawnRange * 16));
        IntOr.Default verticalLimit = ReflectionUtils.getDespawnRangesVerticalLimit(oldDespawnRangePair.hard());
        serverLevel.paperConfig().entities.spawning.despawnRanges.replace(
            MobCategory.MONSTER,
            new WorldConfiguration.Entities.Spawning.DespawnRangePair(new DespawnRange(horizontalLimit, verticalLimit, true), oldDespawnRangePair.soft())
        );
        try {
            serverLevel.paperConfig().entities.spawning.precomputeDespawnDistances();
        } catch (SerializationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the view or simulation distance the world was loaded with, from spigot.yml (server.properties by default).
     */
    public static int getNMSConfiguredDistance(World world, boolean simulation) {
        ServerLevel serverLevel = ReflectionUtils.getNMSWorld(world);
        return simulation ? serverLevel.spigotConfig.simulationDistance : serverLevel.spigotConfig.viewDistance;
    }

    /**
     * The values changed by {@link #setNMSSimulationDistance(World, int)} besides the simulation distance itself.
     */
    public record SpawnRanges(byte mobSpawnRange, WorldConfiguration.Entities.Spawning.DespawnRangePair monsterDespawnRange) {
    }

    public static SpawnRanges getNMSSpawnRanges(World world) {
        ServerLevel serverLevel = ReflectionUtils.getNMSWorld(world);
        return new SpawnRanges(
            serverLevel.spigotConfig.mobSpawnRange,
            serverLevel.paperConfig().entities.spawning.despawnRanges.get(MobCategory.MONSTER)
        );
    }

    public static void setNMSSpawnRanges(World world, SpawnRanges spawnRanges) {
        ServerLevel serverLevel = ReflectionUtils.getNMSWorld(world);
        serverLevel.spigotConfig.mobSpawnRange = spawnRanges.mobSpawnRange();
        serverLevel.paperConfig().entities.spawning.despawnRanges.replace(MobCategory.MONSTER, spawnRanges.monsterDespawnRange());
        try {
            serverLevel.paperConfig().entities.spawning.precomputeDespawnDistances();
        } catch (SerializationException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setNMSVillagerSensorTickRate(World world, int ticks) {
        ServerLevel serverLevel = ReflectionUtils.getNMSWorld(world);
        serverLevel.paperConfig().tickRates.sensor.put(EntityTypes.VILLAGER, "secondarypoisensor", ticks);
    }

    public static void setNMSVillagerBehaviorTickRate(World world, int ticks) {
        ServerLevel serverLevel = ReflectionUtils.getNMSWorld(world);
        serverLevel.paperConfig().tickRates.behavior.put(EntityTypes.VILLAGER, "validatenearbypoi", ticks);
    }

    /**
     * Returns the villager sensor tick rate for the given world, or null if not set.
     */
    public static Integer getNMSVillagerSensorTickRate(World world) {
        ServerLevel serverLevel = ReflectionUtils.getNMSWorld(world);
        return serverLevel.paperConfig().tickRates.sensor.get(EntityTypes.VILLAGER, "secondarypoisensor");
    }

    /**
     * Returns the villager behavior tick rate for the given world, or null if not set.
     */
    public static Integer getNMSVillagerBehaviorTickRate(World world) {
        ServerLevel serverLevel = ReflectionUtils.getNMSWorld(world);
        return serverLevel.paperConfig().tickRates.behavior.get(EntityTypes.VILLAGER, "validatenearbypoi");
    }
}
