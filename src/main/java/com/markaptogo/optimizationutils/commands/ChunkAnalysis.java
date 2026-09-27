package com.markaptogo.optimizationutils.commands;

import com.markaptogo.optimizationutils.manager.ReflectionUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Counts the entities and block entities (hoppers, furnaces, chests, ...) of every loaded chunk, for /ou analyzechunks.
 */
final class ChunkAnalysis {

    private static final int TOP_CHUNKS = 10;
    private static final int TOP_TYPES = 5;
    private static final int HOVER_TYPES = 15;

    /**
     * What the chunks are ranked by.
     */
    enum Ranking {
        ALL("all", "entities and block entities"),
        ENTITIES("entities", "entities"),
        BLOCK_ENTITIES("blockentities", "block entities");

        final String argument;
        final String description;

        Ranking(String argument, String description) {
            this.argument = argument;
            this.description = description;
        }

        int count(ChunkStats chunk) {
            return switch (this) {
                case ALL -> chunk.entityCount + chunk.blockEntityCount;
                case ENTITIES -> chunk.entityCount;
                case BLOCK_ENTITIES -> chunk.blockEntityCount;
            };
        }
    }

    private static final class ChunkStats {
        final World world;
        final int x;
        final int z;
        final Map<String, Integer> entities = new HashMap<>();
        final Map<String, Integer> blockEntities = new HashMap<>();
        int entityCount = 0;
        int blockEntityCount = 0;

        /**
         * Where an entity or block entity of this chunk is, to teleport to.
         */
        Location sample = null;

        ChunkStats(World world, int x, int z) {
            this.world = world;
            this.x = x;
            this.z = z;
        }
    }

    private ChunkAnalysis() {
    }

    static Component analyze(List<World> worlds, Ranking ranking) {
        List<ChunkStats> chunks = new ArrayList<>();
        Map<String, Integer> entityTotals = new HashMap<>();
        Map<String, Integer> blockEntityTotals = new HashMap<>();
        int loadedChunks = 0;

        for (World world : worlds) {
            Map<Long, ChunkStats> byChunk = new HashMap<>();

            for (Entity entity : world.getEntities()) {
                Location location = entity.getLocation();
                int chunkX = location.getBlockX() >> 4;
                int chunkZ = location.getBlockZ() >> 4;
                ChunkStats chunk = byChunk.computeIfAbsent(Chunk.getChunkKey(chunkX, chunkZ), key -> new ChunkStats(world, chunkX, chunkZ));

                String type = entity.getType().name().toLowerCase(Locale.ROOT);
                chunk.entities.merge(type, 1, Integer::sum);
                entityTotals.merge(type, 1, Integer::sum);
                chunk.entityCount++;
                if (chunk.sample == null) {
                    chunk.sample = location;
                }
            }

            ServerLevel level = ReflectionUtils.getNMSWorld(world);
            for (Chunk loadedChunk : world.getLoadedChunks()) {
                loadedChunks++;

                LevelChunk levelChunk = level.getChunkIfLoaded(loadedChunk.getX(), loadedChunk.getZ());
                if (levelChunk == null || levelChunk.getBlockEntities().isEmpty()) continue;

                ChunkStats chunk = byChunk.computeIfAbsent(loadedChunk.getChunkKey(), key -> new ChunkStats(world, loadedChunk.getX(), loadedChunk.getZ()));
                for (BlockEntity blockEntity : levelChunk.getBlockEntities().values()) {
                    String type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()).getPath();
                    chunk.blockEntities.merge(type, 1, Integer::sum);
                    blockEntityTotals.merge(type, 1, Integer::sum);
                    chunk.blockEntityCount++;
                    if (chunk.sample == null) {
                        // On top of the block, not inside it
                        BlockPos pos = blockEntity.getBlockPos();
                        chunk.sample = new Location(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
                    }
                }
            }

            chunks.addAll(byChunk.values());
        }

        int totalEntities = entityTotals.values().stream().mapToInt(Integer::intValue).sum();
        int totalBlockEntities = blockEntityTotals.values().stream().mapToInt(Integer::intValue).sum();

        Component message = Component.text("=== Chunk Analysis ===", NamedTextColor.GREEN)
            .append(Component.newline())
            .append(Component.text(loadedChunks + " loaded chunks in " + (worlds.size() == 1 ? "world " + worlds.getFirst().getName() : worlds.size() + " worlds"), NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("Entities: " + totalEntities, NamedTextColor.AQUA))
            .append(Component.text(totalEntities == 0 ? "" : " (" + formatTypes(entityTotals, TOP_TYPES, ", ") + ")", NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("Block entities: " + totalBlockEntities, NamedTextColor.AQUA))
            .append(Component.text(totalBlockEntities == 0 ? "" : " (" + formatTypes(blockEntityTotals, TOP_TYPES, ", ") + ")", NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.newline());

        List<ChunkStats> topChunks = chunks.stream()
            .filter(chunk -> ranking.count(chunk) > 0)
            .sorted(Comparator.comparingInt(ranking::count).reversed())
            .limit(TOP_CHUNKS)
            .toList();

        if (topChunks.isEmpty()) {
            return message.append(Component.text("No loaded chunk has any " + ranking.description + ".", NamedTextColor.GRAY));
        }

        message = message.append(Component.text("Top " + topChunks.size() + " chunks by " + ranking.description + ":", NamedTextColor.AQUA));
        for (int i = 0; i < topChunks.size(); i++) {
            message = message.append(Component.newline())
                .append(chunkLine(i + 1, topChunks.get(i)));
        }

        return message;
    }

    private static Component chunkLine(int rank, ChunkStats chunk) {
        int blockX = (chunk.x << 4) + 8;
        int blockZ = (chunk.z << 4) + 8;

        Component title = Component.text(rank + ". " + chunk.world.getName() + " " + blockX + " " + blockZ + " (chunk " + chunk.x + " " + chunk.z + "): "
            + chunk.entityCount + " entities, " + chunk.blockEntityCount + " block entities", NamedTextColor.WHITE);

        List<String> types = new ArrayList<>();
        if (!chunk.entities.isEmpty()) types.add(formatTypes(chunk.entities, TOP_TYPES, ", "));
        if (!chunk.blockEntities.isEmpty()) types.add(formatTypes(chunk.blockEntities, TOP_TYPES, ", "));
        Component details = Component.text("    " + String.join(" | ", types), NamedTextColor.GRAY);

        Component hover = Component.text("Entities: " + chunk.entityCount, NamedTextColor.AQUA)
            .append(Component.text(chunk.entities.isEmpty() ? "" : "\n" + formatTypes(chunk.entities, HOVER_TYPES, "\n"), NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Block entities: " + chunk.blockEntityCount, NamedTextColor.AQUA))
            .append(Component.text(chunk.blockEntities.isEmpty() ? "" : "\n" + formatTypes(chunk.blockEntities, HOVER_TYPES, "\n"), NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.newline())
            .append(Component.text("Click to teleport there", NamedTextColor.YELLOW));

        Location sample = chunk.sample;
        String teleport = String.format(Locale.ROOT, "/execute in %s run tp @s %.1f %.1f %.1f",
            chunk.world.getKey().asString(), sample.getX(), sample.getY(), sample.getZ());

        return title
            .append(Component.newline())
            .append(details)
            .hoverEvent(HoverEvent.showText(hover))
            .clickEvent(ClickEvent.runCommand(teleport));
    }

    /**
     * Formats the most common types, like "12 zombie, 5 skeleton, ...".
     */
    private static String formatTypes(Map<String, Integer> counts, int limit, String separator) {
        String types = counts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .limit(limit)
            .map(entry -> entry.getValue() + " " + entry.getKey())
            .collect(Collectors.joining(separator));

        return counts.size() > limit ? types + separator + "..." : types;
    }
}
