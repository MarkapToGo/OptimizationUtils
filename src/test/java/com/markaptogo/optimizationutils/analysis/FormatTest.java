package com.markaptogo.optimizationutils.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatTest {

    @Test
    void numbers() {
        assertEquals("4,812", Format.count(4812));
        assertEquals("4.2", Format.decimal(4.24));
        assertEquals("4", Format.decimal(4));
        assertEquals("812", Format.decimal(812.4));
        assertEquals("1,234", Format.decimal(1234.4));
        assertEquals("120/s", Format.perSecond(120));
    }

    @Test
    void barsShowAnythingAboveZero() {
        assertEquals(10, Format.filled(812, 812, 10));
        assertEquals(6, Format.filled(488, 812, 10));
        assertEquals(1, Format.filled(1, 812, 10));
        assertEquals(0, Format.filled(0, 812, 10));
    }

    @Test
    void textIsShortened() {
        assertEquals("villager×180", Format.shorten("villager×180", 16));
        assertEquals("calibrated_scul…", Format.shorten("calibrated_sculk_sensor×3", 16));
    }

    @Test
    void durations() {
        assertEquals("34s", Format.duration(34_000));
        assertEquals("5m", Format.duration(300_000));
        assertEquals("2h", Format.duration(7_200_000));
    }
}
