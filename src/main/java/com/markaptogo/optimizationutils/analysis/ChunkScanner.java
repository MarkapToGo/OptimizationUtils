package com.markaptogo.optimizationutils.analysis;

import com.markaptogo.optimizationutils.manager.EntityTickManager;
import com.markaptogo.optimizationutils.manager.ReflectionUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.material.Fluid;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.stream.Collectors;

/**
 * Collects the stats of the loaded chunks. Runs on the main thread.
 */
public final class ChunkScanner {

    private ChunkScanner() {
    }

    /**
     * Scans the chunks accepted by the filter (by chunk key) of the worlds, with the scheduled ticks counted before.
     */
    public static ChunkReport scan(Query query, List<World> worlds, LongPredicate chunkFilter, ScheduledTickSampler.Samples samples, CostWeights weights) {
        List<ChunkStats> chunks = new ArrayList<>();
        int loadedChunks = 0;
        int scannedWorlds = 0;

        for (World world : worlds) {
            // The world may have been unloaded while the ticks were counted
            if (Bukkit.getWorld(world.getUID()) == null) continue;
            scannedWorlds++;

            ServerLevel level = ReflectionUtils.getNMSWorld(world);
            String worldLabel = worldLabel(world);
            Long2ObjectMap<ChunkStats> byChunk = new Long2ObjectOpenHashMap<>();
            LongFunction<ChunkStats> stats = key -> new ChunkStats(world.getName(), world.getKey().asString(), worldLabel,
                (int) key, (int) (key >> 32));

            scanEntities(level, chunkFilter, weights, byChunk, stats);
            addTicks(samples.of(world), samples.duration(), weights, byChunk, stats);

            for (Chunk loadedChunk : world.getLoadedChunks()) {
                if (!chunkFilter.test(loadedChunk.getChunkKey())) continue;
                loadedChunks++;

                LevelChunk levelChunk = level.getChunkIfLoaded(loadedChunk.getX(), loadedChunk.getZ());
                if (levelChunk == null) continue;

                FullChunkStatus status = levelChunk.getFullStatus();
                ChunkStats chunk = levelChunk.getBlockEntities().isEmpty()
                    ? byChunk.get(loadedChunk.getChunkKey())
                    : byChunk.computeIfAbsent(loadedChunk.getChunkKey(), stats);
                if (chunk == null) continue;

                chunk.loadStatus = loadStatus(status);
                scanBlockEntities(level, levelChunk, status.isOrAfter(FullChunkStatus.BLOCK_TICKING), weights, chunk);
            }

            findResponsible(world, byChunk.values());
            chunks.addAll(byChunk.values());
        }

        return new ChunkReport(query, chunks, loadedChunks, scannedWorlds, samples.duration(), System.currentTimeMillis());
    }

    private static void scanEntities(ServerLevel level, LongPredicate chunkFilter, CostWeights weights,
                                     Long2ObjectMap<ChunkStats> byChunk, LongFunction<ChunkStats> stats) {
        EntityTickList tickList = EntityTickManager.getTickList(level);

        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof net.minecraft.world.entity.player.Player) continue;

            long key = Chunk.getChunkKey(entity.getBlockX() >> 4, entity.getBlockZ() >> 4);
            if (!chunkFilter.test(key)) continue;

            String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
            // Not in the tick list: in a chunk that does not tick entities, or its ticking is disabled
            boolean ticking = tickList.contains(entity);
            boolean mob = entity instanceof Mob;
            boolean ai = entity instanceof Mob m && m.aware;
            double cost = ticking ? weights.entity(type, mob, ai) : 0;

            byChunk.computeIfAbsent(key, stats)
                .addEntity(type, cost, !ticking || (mob && !ai), new Position(entity.getX(), entity.getY(), entity.getZ()));
        }
    }

    private static void addTicks(Long2ObjectMap<Map<Object, ScheduledTickSampler.Counter>> ticks, int window, CostWeights weights,
                                 Long2ObjectMap<ChunkStats> byChunk, LongFunction<ChunkStats> stats) {
        for (Long2ObjectMap.Entry<Map<Object, ScheduledTickSampler.Counter>> entry : Long2ObjectMaps.fastIterable(ticks)) {
            ChunkStats chunk = byChunk.computeIfAbsent(entry.getLongKey(), stats);

            for (Map.Entry<Object, ScheduledTickSampler.Counter> type : entry.getValue().entrySet()) {
                boolean fluid = type.getKey() instanceof Fluid;
                String name = fluid
                    ? BuiltInRegistries.FLUID.getKey((Fluid) type.getKey()).getPath()
                    : BuiltInRegistries.BLOCK.getKey((Block) type.getKey()).getPath();
                BlockPos pos = type.getValue().position();

                chunk.addTicks(name, type.getValue().count(), weights.ticks(type.getValue().count(), window, fluid),
                    Position.aboveBlock(pos.getX(), pos.getY(), pos.getZ()));
            }
        }
    }

    private static void scanBlockEntities(ServerLevel level, LevelChunk levelChunk, boolean blockTicking, CostWeights weights, ChunkStats chunk) {
        for (BlockEntity blockEntity : levelChunk.getBlockEntities().values()) {
            String type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()).getPath();
            // Chests, beds, ... have no ticker and never tick
            boolean ticking = blockTicking && blockEntity.getBlockState().getTicker(level, blockEntity.getType()) != null;
            BlockPos pos = blockEntity.getBlockPos();

            chunk.addBlockEntity(type, ticking ? weights.blockEntity(type) : 0, ticking, Position.aboveBlock(pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    private static ChunkStats.LoadStatus loadStatus(FullChunkStatus status) {
        return switch (status) {
            case ENTITY_TICKING -> ChunkStats.LoadStatus.ENTITY_TICKING;
            case BLOCK_TICKING -> ChunkStats.LoadStatus.BLOCK_TICKING;
            case FULL -> ChunkStats.LoadStatus.BORDER;
            case INACCESSIBLE -> ChunkStats.LoadStatus.UNKNOWN;
        };
    }

    /**
     * Finds the nearest player that has the chunk in view distance, or else why it is loaded.
     */
    private static void findResponsible(World world, Collection<ChunkStats> chunks) {
        List<Player> players = world.getPlayers();
        int[] playerChunkX = new int[players.size()];
        int[] playerChunkZ = new int[players.size()];
        for (int i = 0; i < players.size(); i++) {
            playerChunkX[i] = players.get(i).getLocation().getBlockX() >> 4;
            playerChunkZ[i] = players.get(i).getLocation().getBlockZ() >> 4;
        }

        for (ChunkStats chunk : chunks) {
            Player nearest = null;
            int nearestDistance = Integer.MAX_VALUE;
            for (int i = 0; i < players.size(); i++) {
                int distance = Math.max(Math.abs(playerChunkX[i] - chunk.x), Math.abs(playerChunkZ[i] - chunk.z));
                if (distance < nearestDistance) {
                    nearest = players.get(i);
                    nearestDistance = distance;
                }
            }

            if (nearest != null && nearestDistance <= viewDistance(nearest) + 1) {
                chunk.responsible = nearest.getName();
                chunk.responsibleDistance = nearestDistance;
                continue;
            }

            Collection<Plugin> tickets = world.getPluginChunkTickets(chunk.x, chunk.z);
            if (world.isChunkForceLoaded(chunk.x, chunk.z)) {
                chunk.loadReason = "force loaded (/forceload)";
            } else if (!tickets.isEmpty()) {
                chunk.loadReason = "kept loaded by " + tickets.stream().map(Plugin::getName).sorted().collect(Collectors.joining(", "));
            } else if (nearest != null) {
                chunk.loadReason = "no player in view distance (nearest: " + nearest.getName() + ", " + nearestDistance + " chunks away)";
            } else {
                chunk.loadReason = "no player in this world";
            }
        }
    }

    private static int viewDistance(Player player) {
        int viewDistance = player.getViewDistance();
        return viewDistance > 0 ? viewDistance : player.getWorld().getViewDistance();
    }

    /**
     * "OW", "N" or "E" when the world is the only one of its environment, or else its name.
     */
    private static String worldLabel(World world) {
        long sameEnvironment = Bukkit.getWorlds().stream().filter(other -> other.getEnvironment() == world.getEnvironment()).count();
        if (sameEnvironment > 1) return world.getName();

        return switch (world.getEnvironment()) {
            case NORMAL -> "OW";
            case NETHER -> "N";
            case THE_END -> "E";
            default -> world.getName();
        };
    }
}
