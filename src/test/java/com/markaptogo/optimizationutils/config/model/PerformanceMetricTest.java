package com.markaptogo.optimizationutils.config.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceMetricTest {

    @Test
    void msptIsReachedAtOrAboveTheThreshold() {
        assertTrue(PerformanceMetric.MSPT.isReached(45, 45));
        assertTrue(PerformanceMetric.MSPT.isReached(46, 45));
        assertFalse(PerformanceMetric.MSPT.isReached(44.9, 45));
    }

    @Test
    void tpsIsReachedAtOrBelowTheThreshold() {
        assertTrue(PerformanceMetric.TPS.isReached(17, 17));
        assertTrue(PerformanceMetric.TPS.isReached(16, 17));
        assertFalse(PerformanceMetric.TPS.isReached(17.1, 17));
    }

    @Test
    void msptRecoversBelowTheThresholdMinusTheMargin() {
        assertFalse(PerformanceMetric.MSPT.isRecovered(43, 45, 2, 20));
        assertTrue(PerformanceMetric.MSPT.isRecovered(42.9, 45, 2, 20));
    }

    @Test
    void tpsRecoversAboveTheThresholdPlusTheMarginOrAtTheTickRate() {
        assertFalse(PerformanceMetric.TPS.isRecovered(18, 17, 1, 20));
        assertTrue(PerformanceMetric.TPS.isRecovered(18.1, 17, 1, 20));
        assertTrue(PerformanceMetric.TPS.isRecovered(20, 19, 2, 20));
    }

    @Test
    void thresholdsBeyondWhatTheMetricCanReachAreAlwaysReached() {
        assertTrue(PerformanceMetric.TPS.isAlwaysReached(20, 20));
        assertTrue(PerformanceMetric.TPS.isAlwaysReached(45, 20));
        assertFalse(PerformanceMetric.TPS.isAlwaysReached(19.9, 20));
        assertTrue(PerformanceMetric.MSPT.isAlwaysReached(0, 20));
        assertFalse(PerformanceMetric.MSPT.isAlwaysReached(45, 20));
    }

    @Test
    void formatsWithADotRegardlessOfTheLocale() {
        assertEquals("45.50ms MSPT", PerformanceMetric.MSPT.format(45.5));
        assertEquals("19.25 TPS", PerformanceMetric.TPS.format(19.25));
    }
}
