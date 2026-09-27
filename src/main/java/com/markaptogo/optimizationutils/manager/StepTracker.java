package com.markaptogo.optimizationutils.manager;

import com.markaptogo.optimizationutils.config.model.PerformanceMetric;

import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Picks the active step from a list of thresholds based on the server performance. Moves to a laggier step right away,
 * and back to the previous step once the server stayed past its threshold by the recovery margin for the recovery delay.
 */
public final class StepTracker<T> {

    private final PerformanceMetric metric;
    private final double recoveryMargin;
    private final long recoveryDelayMillis;
    private final ToDoubleFunction<T> threshold;

    /**
     * Sorted from the least to the most laggy threshold.
     */
    private final List<T> steps;

    /**
     * Index of the active step in {@link #steps}, or -1 when no step is active.
     */
    private int activeStep = -1;
    private long recoveringSince = -1;

    public StepTracker(PerformanceMetric metric, double recoveryMargin, int recoveryDelaySeconds, List<T> steps, ToDoubleFunction<T> threshold) {
        this.metric = metric;
        this.recoveryMargin = recoveryMargin;
        this.recoveryDelayMillis = Math.max(0, recoveryDelaySeconds) * 1000L;
        this.threshold = threshold;

        Comparator<T> leastLaggyFirst = Comparator.comparingDouble(threshold);
        if (metric == PerformanceMetric.TPS) {
            // Lower TPS is laggier
            leastLaggyFirst = leastLaggyFirst.reversed();
        }
        this.steps = steps.stream()
            .sorted(leastLaggyFirst)
            .toList();
    }

    public PerformanceMetric metric() {
        return metric;
    }

    /**
     * Measures the server performance and updates the active step. Returns true when the active step changed.
     */
    public boolean update() {
        double value = ThrottleUtils.getValue(metric);

        // Laggiest step whose threshold is reached
        int reachedStep = -1;
        for (int i = 0; i < steps.size(); i++) {
            if (ThrottleUtils.isReached(metric, value, threshold.applyAsDouble(steps.get(i)))) reachedStep = i;
        }

        if (reachedStep > activeStep) {
            activeStep = reachedStep;
            recoveringSince = -1;
            return true;
        }

        if (activeStep < 0 || !ThrottleUtils.isRecovered(metric, value, threshold.applyAsDouble(steps.get(activeStep)), recoveryMargin)) {
            recoveringSince = -1;
            return false;
        }

        long now = System.currentTimeMillis();
        if (recoveringSince < 0) {
            recoveringSince = now;
        }
        if (now - recoveringSince < recoveryDelayMillis) {
            return false;
        }

        // Go back one step at a time, the next one has to wait for the delay again
        activeStep--;
        recoveringSince = -1;
        return true;
    }

    /**
     * Returns the active step, or null when the server is below every threshold.
     */
    public T activeStep() {
        return activeStep < 0 ? null : steps.get(activeStep);
    }

    /**
     * The current performance in the metric of this tracker, formatted for logs.
     */
    public String formattedValue() {
        return ThrottleUtils.format(metric, ThrottleUtils.getValue(metric));
    }
}
