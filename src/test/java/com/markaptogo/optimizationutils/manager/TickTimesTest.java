package com.markaptogo.optimizationutils.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TickTimesTest {

    @Test
    void isZeroWithoutTicks() {
        TickTimes tickTimes = new TickTimes(60_000);

        assertEquals(0, tickTimes.last());
        assertEquals(0, tickTimes.average(0, 5_000));
    }

    @Test
    void lastIsTheLatestTick() {
        TickTimes tickTimes = new TickTimes(60_000);
        tickTimes.record(0, 12);
        tickTimes.record(50, 34);

        assertEquals(34, tickTimes.last());
    }

    @Test
    void averageOnlyCoversTicksWithinTheWindow() {
        TickTimes tickTimes = new TickTimes(60_000);
        tickTimes.record(0, 100);
        tickTimes.record(5_000, 10);
        tickTimes.record(9_000, 20);

        assertEquals(15, tickTimes.average(10_000, 5_000), 1e-9);
        assertEquals(130 / 3.0, tickTimes.average(10_000, 10_000), 1e-9);
        assertEquals(0, tickTimes.average(20_000, 5_000));
    }

    @Test
    void ticksOlderThanTheMaximumAgeAreDropped() {
        TickTimes tickTimes = new TickTimes(1_000);
        tickTimes.record(0, 100);
        tickTimes.record(2_000, 10);

        assertEquals(10, tickTimes.average(2_000, 5_000));
    }

    @Test
    void keepsEveryTickWhenMoreThanTheInitialCapacityAreRecorded() {
        TickTimes tickTimes = new TickTimes(60_000);
        for (int i = 0; i < 5_000; i++) {
            tickTimes.record(i, i % 2 == 0 ? 10 : 30);
        }

        assertEquals(30, tickTimes.last());
        assertEquals(20, tickTimes.average(5_000, 60_000), 1e-9);
    }
}
