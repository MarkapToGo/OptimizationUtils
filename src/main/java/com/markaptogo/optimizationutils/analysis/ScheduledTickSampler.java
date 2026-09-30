package com.markaptogo.optimizationutils.analysis;

import com.markaptogo.optimizationutils.OptimizationUtils;
import com.markaptogo.optimizationutils.manager.ReflectionUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongPredicate;

/**
 * Counts the scheduled block and fluid ticks (redstone, observers, flowing water, ...) every chunk schedules.
 * <p>
 * Every scheduled tick gets the next number of a counter of its world. Looking at the waiting ticks once per tick
 * and counting those with a number the counter did not reach at the last look gives exactly the ticks scheduled in
 * between, since a tick runs one tick after it was scheduled at the earliest. This only reads what the server keeps
 * anyway, and only while sampling, so nothing about the game changes.
 */
public final class ScheduledTickSampler {

    private static final Field ALL_CONTAINERS;
    private static final Field SUB_TICK_COUNT;

    static {
        try {
            ALL_CONTAINERS = LevelTicks.class.getDeclaredField("allContainers");
            ALL_CONTAINERS.setAccessible(true);
            SUB_TICK_COUNT = Level.class.getDeclaredField("subTickCount");
            SUB_TICK_COUNT.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Failed to get the scheduled tick fields", e);
        }
    }

    /**
     * How many ticks of one type (a block or a fluid) a chunk scheduled, and where one of them was.
     */
    public static final class Counter {
        int count = 0;
        BlockPos position = null;

        public int count() {
            return count;
        }

        public BlockPos position() {
            return position;
        }
    }

    /**
     * The counted ticks: world -> chunk key -> type (a Block or a Fluid) -> counter.
     */
    public record Samples(Map<UUID, Long2ObjectMap<Map<Object, Counter>>> byWorld, int duration) {

        public Long2ObjectMap<Map<Object, Counter>> of(World world) {
            return byWorld.getOrDefault(world.getUID(), Long2ObjectMaps.emptyMap());
        }
    }

    private ScheduledTickSampler() {
    }

    /**
     * Counts the ticks of the chunks accepted by the filter (by chunk key) for the given number of ticks,
     * then calls done on the main thread.
     */
    public static void sample(List<World> worlds, LongPredicate chunkFilter, int duration, Consumer<Samples> done) {
        Map<UUID, Long2ObjectMap<Map<Object, Counter>>> byWorld = new HashMap<>();
        Map<UUID, Long> counters = new HashMap<>();
        for (World world : worlds) {
            byWorld.put(world.getUID(), new Long2ObjectOpenHashMap<>());
            counters.put(world.getUID(), subTickCount(ReflectionUtils.getNMSWorld(world)));
        }

        new BukkitRunnable() {
            private int sampled = 0;

            @Override
            public void run() {
                for (Map.Entry<UUID, Long> entry : counters.entrySet()) {
                    World world = Bukkit.getWorld(entry.getKey());
                    if (world == null) continue;

                    ServerLevel level = ReflectionUtils.getNMSWorld(world);
                    long since = entry.getValue();
                    entry.setValue(subTickCount(level));

                    Long2ObjectMap<Map<Object, Counter>> chunks = byWorld.get(entry.getKey());
                    count(level.getBlockTicks(), since, chunkFilter, chunks);
                    count(level.getFluidTicks(), since, chunkFilter, chunks);
                }

                if (++sampled >= duration) {
                    cancel();
                    done.accept(new Samples(byWorld, duration));
                }
            }
        }.runTaskTimer(OptimizationUtils.instance(), 1, 1);
    }

    private static <T> void count(LevelTicks<T> levelTicks, long since, LongPredicate chunkFilter, Long2ObjectMap<Map<Object, Counter>> chunks) {
        for (Long2ObjectMap.Entry<LevelChunkTicks<T>> entry : Long2ObjectMaps.fastIterable(containers(levelTicks))) {
            LevelChunkTicks<T> container = entry.getValue();
            if (container.count() == 0 || !chunkFilter.test(entry.getLongKey())) continue;

            Map<Object, Counter> types = null;
            Iterator<ScheduledTick<T>> ticks = container.getAll().iterator();
            while (ticks.hasNext()) {
                ScheduledTick<T> tick = ticks.next();
                // Older ticks were counted at an earlier look, loaded ones have negative numbers
                if (tick.subTickOrder() < since) continue;

                if (types == null) {
                    types = chunks.computeIfAbsent(entry.getLongKey(), key -> new HashMap<>());
                }
                Counter counter = types.computeIfAbsent(tick.type(), type -> new Counter());
                counter.count++;
                if (counter.position == null) {
                    counter.position = tick.pos();
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Long2ObjectMap<LevelChunkTicks<T>> containers(LevelTicks<T> levelTicks) {
        try {
            return (Long2ObjectMap<LevelChunkTicks<T>>) ALL_CONTAINERS.get(levelTicks);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static long subTickCount(ServerLevel level) {
        try {
            return SUB_TICK_COUNT.getLong(level);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
