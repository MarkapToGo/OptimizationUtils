package com.markaptogo.optimizationutils.analysis;

/**
 * What the chunks are ranked by.
 */
public enum Sort {
    SCORE("score", "Score", "estimated cost"),
    ENTITIES("entities", "Entities", "entities"),
    BLOCK_ENTITIES("blockentities", "Block ent.", "block entities"),
    TICKS("ticks", "Ticks", "scheduled ticks"),
    /**
     * By the count of the type of the query.
     */
    TYPE("type", "Type", "count of the type");

    public final String argument;
    public final String label;
    public final String description;

    Sort(String argument, String label, String description) {
        this.argument = argument;
        this.label = label;
        this.description = description;
    }

    public double value(ChunkStats chunk, String type) {
        return switch (this) {
            case SCORE -> chunk.score();
            case ENTITIES -> chunk.entities.total();
            case BLOCK_ENTITIES -> chunk.blockEntities.total();
            case TICKS -> chunk.ticks.total();
            case TYPE -> type == null ? 0 : chunk.count(type);
        };
    }
}
