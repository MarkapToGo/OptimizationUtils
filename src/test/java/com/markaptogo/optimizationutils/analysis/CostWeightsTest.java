package com.markaptogo.optimizationutils.analysis;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CostWeightsTest {

    private final CostWeights weights = new CostWeights(
        1.0, 0.25, Map.of("Villager", 4.0, "minecraft:item", 0.5, "armor_stand", 0.05),
        0.5, Map.of("hopper", 1.5),
        0.5, 0.25
    );

    @Test
    void entitiesUseTheirTypeOrTheDefaultOfMobsOrOtherEntities() {
        assertEquals(4.0, weights.entity("villager", true, true));
        assertEquals(1.0, weights.entity("zombie", true, true));
        assertEquals(0.5, weights.entity("item", false, false));
        assertEquals(0.25, weights.entity("arrow", false, false));
    }

    @Test
    void mobsWithoutAiCostAtMostLikeOtherEntities() {
        assertEquals(0.25, weights.entity("villager", true, false));
        assertEquals(0.25, weights.entity("zombie", true, false));
        assertEquals(0.05, weights.entity("armor_stand", true, false));
    }

    @Test
    void blockEntitiesUseTheirTypeOrTheDefault() {
        assertEquals(1.5, weights.blockEntity("hopper"));
        assertEquals(0.5, weights.blockEntity("furnace"));
    }

    @Test
    void ticksCostPerTickScheduledEachTick() {
        // 80 block ticks in 40 ticks = 2 per tick
        assertEquals(1.0, weights.ticks(80, 40, false));
        assertEquals(0.5, weights.ticks(80, 40, true));
    }

    @Test
    void typesAreNormalized() {
        assertEquals("villager", CostWeights.normalize(" minecraft:Villager "));
        assertEquals("hopper", CostWeights.normalize("HOPPER"));
    }
}
