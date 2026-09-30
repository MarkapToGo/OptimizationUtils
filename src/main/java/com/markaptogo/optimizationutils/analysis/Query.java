package com.markaptogo.optimizationutils.analysis;

/**
 * What an analysis was asked for, to filter its chunks and to run it again.
 *
 * @param world  only the world with this key (like "minecraft:overworld"), or null for all worlds
 * @param type   only chunks with this type (villager, hopper, repeater, ...), or null
 * @param player only chunks this player is the nearest one to, or null
 */
public record Query(Sort sort, String world, String type, String player) {

    public static Query top(Sort sort, String world) {
        return new Query(sort, world, null, null);
    }

    public static Query type(String type, String world) {
        return new Query(Sort.TYPE, world, type, null);
    }

    public static Query player(String player) {
        return new Query(Sort.SCORE, null, null, player);
    }

    public Query withSort(Sort sort) {
        return new Query(sort, world, type, player);
    }

    public boolean matches(ChunkStats chunk) {
        if (world != null && !world.equals(chunk.worldKey)) return false;
        if (type != null && chunk.count(type) == 0) return false;
        return player == null || player.equalsIgnoreCase(chunk.responsible());
    }

    /**
     * The command that runs this analysis again.
     */
    public String command() {
        String command = "/ou analyzechunks ";
        if (type != null) {
            command += "type " + type;
        } else if (player != null) {
            command += "player " + player;
        } else {
            command += (sort == Sort.TYPE ? Sort.SCORE : sort).argument;
        }

        return world != null ? command + " " + world : command;
    }

    /**
     * Like "villager · world_nether · near Steve", or an empty string without filters.
     */
    public String describeFilters() {
        StringBuilder filters = new StringBuilder();
        if (type != null) filters.append(type);
        if (world != null) filters.append(filters.isEmpty() ? "" : " · ").append(CostWeights.normalize(world));
        if (player != null) filters.append(filters.isEmpty() ? "" : " · ").append("loaded by ").append(player);
        return filters.toString();
    }
}
