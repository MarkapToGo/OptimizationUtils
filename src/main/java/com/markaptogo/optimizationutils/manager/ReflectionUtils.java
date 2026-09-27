package com.markaptogo.optimizationutils.manager;

import io.papermc.paper.configuration.type.DespawnRange;
import io.papermc.paper.configuration.type.number.IntOr;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;

import java.lang.reflect.Field;

public class ReflectionUtils {

    private static final Field despawnRangeVerticalLimit;

    static {
        try {
            despawnRangeVerticalLimit = DespawnRange.class.getDeclaredField("verticalLimit");
            despawnRangeVerticalLimit.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public static ServerLevel getNMSWorld(World world) {
        return ((CraftWorld) world).getHandle();
    }

    public static net.minecraft.world.entity.Entity getNMSEntity(org.bukkit.entity.Entity entity) {
        return ((CraftEntity) entity).getHandle();
    }

    public static IntOr.Default getDespawnRangesVerticalLimit(DespawnRange despawnRange) {
        try {
            return (IntOr.Default) despawnRangeVerticalLimit.get(despawnRange);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
