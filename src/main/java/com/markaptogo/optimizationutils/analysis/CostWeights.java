package com.markaptogo.optimizationutils.analysis;

import com.markaptogo.optimizationutils.config.PluginConfiguration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The estimated cost per tick of everything a chunk can contain, from the configuration.
 */
public record CostWeights(
    double defaultMobWeight,
    double defaultEntityWeight,
    Map<String, Double> entityWeights,
    double defaultBlockEntityWeight,
    Map<String, Double> blockEntityWeights,
    double blockTickWeight,
    double fluidTickWeight
) {

    public CostWeights {
        entityWeights = normalizeKeys(entityWeights);
        blockEntityWeights = normalizeKeys(blockEntityWeights);
    }

    public static CostWeights from(PluginConfiguration.ChunkAnalysis config) {
        return new CostWeights(
            config.defaultMobWeight,
            config.defaultEntityWeight,
            config.entityWeights,
            config.defaultBlockEntityWeight,
            config.blockEntityWeights,
            config.blockTickWeight,
            config.fluidTickWeight
        );
    }

    /**
     * The cost of a ticking entity of the type. Mobs without AI cost at most as much as other entities.
     */
    public double entity(String type, boolean mob, boolean ai) {
        if (!mob) return entityWeights.getOrDefault(type, defaultEntityWeight);

        double weight = entityWeights.getOrDefault(type, defaultMobWeight);
        return ai ? weight : Math.min(weight, defaultEntityWeight);
    }

    /**
     * The cost of a ticking block entity of the type.
     */
    public double blockEntity(String type) {
        return blockEntityWeights.getOrDefault(type, defaultBlockEntityWeight);
    }

    /**
     * The cost of the scheduled ticks, counted over the window (in ticks).
     */
    public double ticks(int count, int window, boolean fluid) {
        return (double) count / Math.max(1, window) * (fluid ? fluidTickWeight : blockTickWeight);
    }

    /**
     * Turns "minecraft:Villager " into "villager", like the types are named in the analysis.
     */
    public static String normalize(String type) {
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("minecraft:") ? normalized.substring("minecraft:".length()) : normalized;
    }

    private static Map<String, Double> normalizeKeys(Map<String, Double> weights) {
        Map<String, Double> normalized = new HashMap<>();
        if (weights != null) {
            weights.forEach((type, weight) -> {
                if (type != null && weight != null) normalized.put(normalize(type), Math.max(0, weight));
            });
        }
        return Map.copyOf(normalized);
    }
}
