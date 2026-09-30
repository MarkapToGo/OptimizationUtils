package com.markaptogo.optimizationutils.analysis;

import java.util.Comparator;
import java.util.List;

/**
 * The result of one analysis: the stats of every loaded chunk that contains something.
 */
public final class ChunkReport {

    public final Query query;
    public final List<ChunkStats> chunks;
    public final int loadedChunks;
    public final int worlds;
    /**
     * How many ticks the scheduled ticks were counted for.
     */
    public final int tickWindow;
    public final long createdAt;

    public final TypeCounts entityTotals = new TypeCounts();
    public final TypeCounts blockEntityTotals = new TypeCounts();
    public final TypeCounts tickTotals = new TypeCounts();

    public ChunkReport(Query query, List<ChunkStats> chunks, int loadedChunks, int worlds, int tickWindow, long createdAt) {
        this.query = query;
        this.chunks = List.copyOf(chunks);
        this.loadedChunks = loadedChunks;
        this.worlds = worlds;
        this.tickWindow = Math.max(1, tickWindow);
        this.createdAt = createdAt;

        for (ChunkStats chunk : chunks) {
            entityTotals.addAll(chunk.entities);
            blockEntityTotals.addAll(chunk.blockEntities);
            tickTotals.addAll(chunk.ticks);
        }
    }

    /**
     * Converts a number of scheduled ticks counted in this report to ticks per second.
     */
    public double perSecond(int ticks) {
        return ticks * 20.0 / tickWindow;
    }

    /**
     * The chunks that match the query and have any of what they are ranked by, highest first.
     */
    public List<ChunkStats> rank(Sort sort) {
        return chunks.stream()
            .filter(query::matches)
            .filter(chunk -> sort.value(chunk, query.type()) > 0)
            .sorted(Comparator.<ChunkStats>comparingDouble(chunk -> sort.value(chunk, query.type())).reversed()
                .thenComparing(Comparator.comparingDouble(ChunkStats::score).reversed())
                .thenComparing(chunk -> chunk.worldName)
                .thenComparingInt(chunk -> chunk.x)
                .thenComparingInt(chunk -> chunk.z))
            .toList();
    }

    public ChunkStats find(String worldKey, int x, int z) {
        return chunks.stream()
            .filter(chunk -> chunk.worldKey.equals(worldKey) && chunk.x == x && chunk.z == z)
            .findFirst()
            .orElse(null);
    }
}
