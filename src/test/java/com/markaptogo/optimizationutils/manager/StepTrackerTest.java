package com.markaptogo.optimizationutils.manager;

import com.markaptogo.optimizationutils.config.model.PerformanceMetric;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepTrackerTest {

    private static final double TICK_RATE = 20;
    private static final List<Double> MSPT_STEPS = List.of(35.0, 40.0, 45.0, 50.0);

    private long now = 0;

    private StepTracker<Double> tracker(PerformanceMetric metric, int triggerDelay, double recoveryMargin, int recoveryDelay, List<Double> steps) {
        return new StepTracker<>(metric, triggerDelay, recoveryMargin, recoveryDelay, steps, Double::doubleValue, () -> now);
    }

    /**
     * Updates the tracker at the given time in seconds.
     */
    private boolean update(StepTracker<Double> tracker, int second, double value) {
        now = second * 1000L;
        return tracker.update(value, TICK_RATE);
    }

    @Test
    void noStepIsActiveBelowEveryThreshold() {
        StepTracker<Double> tracker = tracker(PerformanceMetric.MSPT, 0, 2, 0, MSPT_STEPS);

        assertFalse(update(tracker, 0, 20));
        assertNull(tracker.activeStep());
    }

    @Test
    void withoutTriggerDelayTheLaggiestReachedStepIsActiveRightAway() {
        StepTracker<Double> tracker = tracker(PerformanceMetric.MSPT, 0, 2, 0, MSPT_STEPS);

        assertTrue(update(tracker, 0, 47));
        assertEquals(45.0, tracker.activeStep());

        assertTrue(update(tracker, 1, 80));
        assertEquals(50.0, tracker.activeStep());
    }

    @Test
    void triggerDelayIgnoresShortLagSpikes() {
        StepTracker<Double> tracker = tracker(PerformanceMetric.MSPT, 10, 2, 0, MSPT_STEPS);

        for (int second = 0; second < 6; second++) {
            assertFalse(update(tracker, second, 60));
        }
        assertFalse(update(tracker, 6, 30));
        assertFalse(update(tracker, 20, 30));
        assertNull(tracker.activeStep());
    }

    @Test
    void triggerDelayActivatesAStepOnceItsThresholdWasReachedLongEnough() {
        StepTracker<Double> tracker = tracker(PerformanceMetric.MSPT, 10, 2, 0, MSPT_STEPS);

        assertFalse(update(tracker, 0, 47));
        assertFalse(update(tracker, 9, 47));
        assertTrue(update(tracker, 10, 47));
        assertEquals(45.0, tracker.activeStep());
    }

    @Test
    void triggerDelayIsTrackedPerStep() {
        StepTracker<Double> tracker = tracker(PerformanceMetric.MSPT, 10, 2, 0, MSPT_STEPS);

        update(tracker, 0, 41);
        update(tracker, 5, 51);

        // 40 has been reached for 10 seconds, 50 only for 5
        assertTrue(update(tracker, 10, 51));
        assertEquals(40.0, tracker.activeStep());

        assertTrue(update(tracker, 15, 51));
        assertEquals(50.0, tracker.activeStep());
    }

    @Test
    void triggerDelayRestartsWhenTheThresholdIsLeft() {
        StepTracker<Double> tracker = tracker(PerformanceMetric.MSPT, 10, 2, 0, MSPT_STEPS);

        update(tracker, 0, 47);
        update(tracker, 5, 30);
        assertFalse(update(tracker, 6, 47));
        assertFalse(update(tracker, 15, 47));
        assertTrue(update(tracker, 16, 47));
    }

    @Test
    void recoveryNeedsTheMarginAndTheDelayAndGoesBackOneStepAtATime() {
        StepTracker<Double> tracker = tracker(PerformanceMetric.MSPT, 0, 2, 30, MSPT_STEPS);
        update(tracker, 0, 55);
        assertEquals(50.0, tracker.activeStep());

        // Below the threshold, but not by more than the margin
        assertFalse(update(tracker, 1, 49));

        assertFalse(update(tracker, 2, 30));
        assertFalse(update(tracker, 31, 30));
        assertTrue(update(tracker, 32, 30));
        assertEquals(45.0, tracker.activeStep());

        // The next step has to wait for the delay again
        assertFalse(update(tracker, 33, 30));
        assertTrue(update(tracker, 63, 30));
        assertEquals(40.0, tracker.activeStep());

        update(tracker, 93, 30);
        assertTrue(update(tracker, 123, 30));
        assertEquals(35.0, tracker.activeStep());

        update(tracker, 124, 30);
        assertTrue(update(tracker, 154, 30));
        assertNull(tracker.activeStep());
    }

    @Test
    void recoveryDelayRestartsWhenTheLagReturns() {
        StepTracker<Double> tracker = tracker(PerformanceMetric.MSPT, 0, 2, 30, MSPT_STEPS);
        update(tracker, 0, 55);

        update(tracker, 1, 30);
        update(tracker, 20, 49);
        assertFalse(update(tracker, 21, 30));
        assertFalse(update(tracker, 50, 30));
        assertTrue(update(tracker, 51, 30));
    }

    @Test
    void lowerTpsIsLaggierAndStepsCanBeInAnyOrder() {
        StepTracker<Double> tracker = tracker(PerformanceMetric.TPS, 0, 1, 0, List.of(15.0, 19.0, 12.0, 17.0));

        assertTrue(update(tracker, 0, 16));
        assertEquals(17.0, tracker.activeStep());

        assertTrue(update(tracker, 1, 11));
        assertEquals(12.0, tracker.activeStep());
    }

    @Test
    void fullTickRateAlwaysCountsAsRecovered() {
        // 19 + 2 can never be reached, since the TPS stops at the tick rate
        StepTracker<Double> tracker = tracker(PerformanceMetric.TPS, 0, 2, 0, List.of(19.0));
        update(tracker, 0, 18.5);
        assertEquals(19.0, tracker.activeStep());

        assertTrue(update(tracker, 1, TICK_RATE));
        assertNull(tracker.activeStep());
    }

    @Test
    void formattedValueIsTheLastMeasuredValue() {
        StepTracker<Double> tracker = tracker(PerformanceMetric.MSPT, 0, 2, 0, MSPT_STEPS);
        update(tracker, 0, 47.123);

        assertEquals("47.12ms MSPT", tracker.formattedValue());
    }
}
