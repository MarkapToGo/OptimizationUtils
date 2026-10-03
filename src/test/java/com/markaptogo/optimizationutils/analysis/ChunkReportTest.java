package com.markaptogo.optimizationutils.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkReportTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    private static ChunkStats chunk(String worldKey, int x, int z) {
        return new ChunkStats(worldKey.substring("minecraft:".length()), worldKey, "OW", x, z);
    }

    /**
     * Many cheap armor stands, few expensive villagers, a redstone clock and a storage room.
     */
    private final ChunkStats armorStands = chunk(OVERWORLD, 0, 0);
    private final ChunkStats villagers = chunk(OVERWORLD, 1, 0);
    private final ChunkStats redstone = chunk(NETHER, 0, 0);
    private final ChunkStats storage = chunk(OVERWORLD, 2, 0);

    {
        for (int i = 0; i < 100; i++) armorStands.addEntity("armor_stand", 0.05, false, null);
        for (int i = 0; i < 10; i++) villagers.addEntity("villager", 4, false, new Position(20, 64, 5));
        villagers.addEntity("villager", 0, true, null);
        redstone.addTicks("repeater", 400, 10, new Position(1, 2, 3));
        for (int i = 0; i < 50; i++) storage.addBlockEntity("chest", 0, false, null);
        storage.addBlockEntity("hopper", 1.5, true, new Position(40, 70, 8));
        villagers.responsible = "Steve";
    }

    private ChunkReport report(Query query) {
        return new ChunkReport(query, List.of(armorStands, villagers, redstone, storage), 100, 2, 40, 0);
    }

    @Test
    void scoreRanksByCostNotByCount() {
        assertEquals(List.of(villagers, redstone, armorStands, storage), report(Query.top(Sort.SCORE, null)).rank(Sort.SCORE));
        assertEquals(List.of(armorStands, villagers), report(Query.top(Sort.ENTITIES, null)).rank(Sort.ENTITIES));
    }

    @Test
    void chunksWithoutWhatTheyAreRankedByAreLeftOut() {
        assertEquals(List.of(redstone), report(Query.top(Sort.TICKS, null)).rank(Sort.TICKS));
        assertEquals(List.of(storage), report(Query.top(Sort.BLOCK_ENTITIES, null)).rank(Sort.BLOCK_ENTITIES));
    }

    @Test
    void queriesFilterByWorldTypeAndPlayer() {
        assertEquals(List.of(redstone), report(Query.top(Sort.SCORE, NETHER)).rank(Sort.SCORE));
        assertEquals(List.of(storage), report(Query.type("hopper", null)).rank(Sort.TYPE));
        assertEquals(List.of(villagers), report(Query.player("steve")).rank(Sort.SCORE));
    }

    @Test
    void statsAddUp() {
        assertEquals(11, villagers.entities.total());
        assertEquals(1, villagers.idleEntities());
        assertEquals(40.0, villagers.score());
        assertEquals(1, storage.tickingBlockEntities());

        ChunkReport report = report(Query.top(Sort.SCORE, null));
        assertEquals(111, report.entityTotals.total());
        assertEquals(51, report.blockEntityTotals.total());
        // 400 ticks in 40 ticks
        assertEquals(200.0, report.perSecond(report.tickTotals.total()));
    }

    @Test
    void teleportsToTheCostliestType() {
        assertEquals(new Position(20, 64, 5), villagers.teleportTarget());
        assertEquals(new Position(40, 70, 8), storage.teleportTarget());
        assertEquals(new ChunkStats.TypeRef(ChunkStats.Kind.TICK, "repeater"), redstone.costliestType());
    }

    @Test
    void viewsArePaged() {
        ReportView view = new ReportView(report(Query.top(Sort.SCORE, null)), Sort.SCORE, 2, 3);

        assertEquals(2, view.pages());
        assertEquals(List.of(storage), view.rows());
        assertEquals(4, view.firstRank());
        assertEquals(40.0, view.maxValue());
        assertEquals(2, view.withPage(99).clampedPage());
        assertEquals(1, view.withSort(Sort.TICKS).clampedPage());
    }

    @Test
    void queriesKnowTheirCommand() {
        assertEquals("/ou analyzechunks score", Query.top(Sort.SCORE, null).command());
        assertEquals("/ou analyzechunks ticks minecraft:the_nether", Query.top(Sort.TICKS, NETHER).command());
        assertEquals("/ou analyzechunks type hopper minecraft:the_nether", Query.type("hopper", NETHER).command());
        assertEquals("/ou analyzechunks player Steve", Query.player("Steve").withSort(Sort.ENTITIES).command());
        assertEquals("hopper · the_nether", Query.type("hopper", NETHER).describeFilters());
    }

    @Test
    void csvHasOneLinePerRankedChunk() {
        String csv = ChunkReportMessages.csv(new ReportView(report(Query.top(Sort.SCORE, null)), Sort.SCORE, 1, 2));
        String[] lines = csv.split("\n");

        assertEquals(5, lines.length);
        assertTrue(lines[0].startsWith("rank,world,chunk_x,chunk_z"));
        assertTrue(lines[1].startsWith("1,overworld,1,0,24,8,40.00,"), lines[1]);
        assertEquals("\"a, \"\"b\"\"\"", ChunkReportMessages.csvCell("a, \"b\""));
    }

    @Test
    void forceLoadingIsNamedEvenWithAPlayerNear() {
        villagers.responsibleDistance = 2;
        assertEquals("by Steve (2 chunks away)", ChunkReportMessages.loadedBy(villagers));

        villagers.loadReason = "force loaded (/forceload)";
        assertEquals("force loaded (/forceload), and by Steve (2 chunks away)", ChunkReportMessages.loadedBy(villagers));

        storage.loadReason = "force loaded (/forceload)";
        assertEquals("force loaded (/forceload)", ChunkReportMessages.loadedBy(storage));
        assertEquals("unknown", ChunkReportMessages.loadedBy(armorStands));
    }
}
