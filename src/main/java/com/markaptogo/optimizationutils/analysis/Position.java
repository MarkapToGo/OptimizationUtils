package com.markaptogo.optimizationutils.analysis;

/**
 * Where something of a chunk is, to teleport to.
 */
public record Position(double x, double y, double z) {

    /**
     * On top of the block, not inside it.
     */
    public static Position aboveBlock(int x, int y, int z) {
        return new Position(x + 0.5, y + 1, z + 0.5);
    }
}
