package com.markaptogo.optimizationutils.analysis;

import java.util.Comparator;
import java.util.stream.Stream;

/**
 * What one loaded chunk contains and how much it costs the server each tick.
 */
public final class ChunkStats {

    /**
     * One kind of thing a chunk contains.
     */
    public enum Kind {
        ENTITY, BLOCK_ENTITY, TICK
    }

    /**
     * A type of one kind, like the villagers or the repeaters of a chunk.
     */
    public record TypeRef(Kind kind, String type) {
    }

    public final String worldName;
    /**
     * Like "minecraft:overworld", for /execute in.
     */
    public final String worldKey;
    /**
     * Short name of the world, like "OW" or "N".
     */
    public final String worldLabel;
    public final int x;
    public final int z;

    public final TypeCounts entities = new TypeCounts();
    public final TypeCounts blockEntities = new TypeCounts();
    /**
     * Scheduled block and fluid ticks, counted over {@link ChunkReport#tickWindow} ticks.
     */
    public final TypeCounts ticks = new TypeCounts();

    /**
     * Entities that are not ticked (chunk not entity ticking, ticking disabled) or mobs without AI.
     */
    int idleEntities = 0;
    int tickingBlockEntities = 0;

    LoadStatus loadStatus = LoadStatus.UNKNOWN;
    /**
     * The nearest player that has this chunk in view distance, or null.
     */
    String responsible = null;
    int responsibleDistance = 0;
    /**
     * What keeps the chunk loaded besides a near player, like "force loaded", or why it is loaded when no player is near.
     */
    String loadReason = null;

    public ChunkStats(String worldName, String worldKey, String worldLabel, int x, int z) {
        this.worldName = worldName;
        this.worldKey = worldKey;
        this.worldLabel = worldLabel;
        this.x = x;
        this.z = z;
    }

    public void addEntity(String type, double cost, boolean idle, Position position) {
        entities.add(type, 1, cost, position);
        if (idle) idleEntities++;
    }

    public void addBlockEntity(String type, double cost, boolean ticking, Position position) {
        blockEntities.add(type, 1, cost, position);
        if (ticking) tickingBlockEntities++;
    }

    public void addTicks(String type, int count, double cost, Position position) {
        ticks.add(type, count, cost, position);
    }

    public double score() {
        return entities.cost() + blockEntities.cost() + ticks.cost();
    }

    public int idleEntities() {
        return idleEntities;
    }

    public int tickingBlockEntities() {
        return tickingBlockEntities;
    }

    public LoadStatus loadStatus() {
        return loadStatus;
    }

    public String responsible() {
        return responsible;
    }

    public int responsibleDistance() {
        return responsibleDistance;
    }

    public String loadReason() {
        return loadReason;
    }

    public TypeCounts of(Kind kind) {
        return switch (kind) {
            case ENTITY -> entities;
            case BLOCK_ENTITY -> blockEntities;
            case TICK -> ticks;
        };
    }

    /**
     * How many of the type (in any kind) this chunk has. Scheduled ticks count once per tick.
     */
    public int count(String type) {
        return entities.count(type) + blockEntities.count(type) + ticks.count(type);
    }

    /**
     * The type that costs the most, or the most common one when nothing costs anything.
     */
    public TypeRef costliestType() {
        return Stream.of(Kind.values())
            .flatMap(kind -> of(kind).byCost().stream().limit(1).map(type -> new TypeRef(kind, type)))
            .max(Comparator.<TypeRef>comparingDouble(ref -> of(ref.kind()).cost(ref.type()))
                .thenComparingInt(ref -> of(ref.kind()).count(ref.type())))
            .orElse(null);
    }

    /**
     * Where to teleport to: one of the costliest type.
     */
    public Position teleportTarget() {
        TypeRef costliest = costliestType();
        if (costliest != null && of(costliest.kind()).position(costliest.type()) != null) {
            return of(costliest.kind()).position(costliest.type());
        }

        for (Kind kind : Kind.values()) {
            Position position = of(kind).anyPosition();
            if (position != null) return position;
        }

        return Position.aboveBlock((x << 4) + 8, 64, (z << 4) + 8);
    }

    public int centerX() {
        return (x << 4) + 8;
    }

    public int centerZ() {
        return (z << 4) + 8;
    }

    /**
     * How far the server loads the chunk, from the most to the least that is ticked.
     */
    public enum LoadStatus {
        ENTITY_TICKING("entity ticking", "everything ticks"),
        BLOCK_TICKING("block ticking", "entities do not tick"),
        BORDER("border", "nothing ticks"),
        UNKNOWN("unknown", "not fully loaded");

        public final String label;
        public final String meaning;

        LoadStatus(String label, String meaning) {
            this.label = label;
            this.meaning = meaning;
        }
    }
}
