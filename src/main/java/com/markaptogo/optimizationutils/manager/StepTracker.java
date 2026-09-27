package com.markaptogo.optimizationutils.manager;

import com.markaptogo.optimizationutils.config.model.PerformanceMetric;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.ToDoubleFunction;

/**
 * Picks the active step from a list of thresholds based on the server performance. Moves to a laggier step once its
 * threshold was reached for the trigger delay, and back to the previous step once the server stayed past its threshold
 * by the recovery margin for the recovery delay.
 */
public final class StepTracker<T> {

    private final PerformanceMetric metric;
    private final long triggerDelayMillis;
    private final double recoveryMargin;
    private final long recoveryDelayMillis;
    private final ToDoubleFunction<T> threshold;
    private final LongSupplier clock;

    /**
     * Sorted from the least to the most laggy threshold.
     */
    private final List<T> steps;

    /**
     * Since when the threshold of each step in {@link #steps} is reached, or -1 when it was not reached at the last check.
     */
    private final long[] reachedSince;

    /**
     * Index of the active step in {@link #steps}, or -1 when no step is active.
     */
    private int activeStep = -1;
    private long recoveringSince = -1;
    private double lastValue = 0;

    public StepTracker(PerformanceMetric metric, int triggerDelaySeconds, double recoveryMargin, int recoveryDelaySeconds,
                       List<T> steps, ToDoubleFunction<T> threshold) {
        this(metric, triggerDelaySeconds, recoveryMargin, recoveryDelaySeconds, steps, threshold, System::currentTimeMillis);
    }

    public StepTracker(PerformanceMetric metric, int triggerDelaySeconds, double recoveryMargin, int recoveryDelaySeconds,
                       List<T> steps, ToDoubleFunction<T> threshold, LongSupplier clock) {
        this.metric = metric;
        this.triggerDelayMillis = Math.max(0, triggerDelaySeconds) * 1000L;
        this.recoveryMargin = recoveryMargin;
        this.recoveryDelayMillis = Math.max(0, recoveryDelaySeconds) * 1000L;
        this.threshold = threshold;
        this.clock = clock;

        Comparator<T> leastLaggyFirst = Comparator.comparingDouble(threshold);
        if (metric == PerformanceMetric.TPS) {
            // Lower TPS is laggier
            leastLaggyFirst = leastLaggyFirst.reversed();
        }
        this.steps = steps.stream()
            .sorted(leastLaggyFirst)
            .toList();

        this.reachedSince = new long[this.steps.size()];
        Arrays.fill(reachedSince, -1);
    }

    public PerformanceMetric metric() {
        return metric;
    }

    /**
     * Measures the server performance and updates the active step. Returns true when the active step changed.
     */
    public boolean update() {
        return update(ThrottleUtils.getValue(metric), ThrottleUtils.tickRate());
    }

    /**
     * Updates the active step with the given performance, in the metric of this tracker. Returns true when the active step changed.
     */
    public boolean update(double value, double tickRate) {
        lastValue = value;
        long now = clock.getAsLong();

        // Laggiest step whose threshold was reached for the whole trigger delay
        int triggeredStep = -1;
        for (int i = 0; i < steps.size(); i++) {
            if (!metric.isReached(value, thresholdOf(i))) {
                reachedSince[i] = -1;
                continue;
            }

            if (reachedSince[i] < 0) {
                reachedSince[i] = now;
            }
            if (now - reachedSince[i] >= triggerDelayMillis) {
                triggeredStep = i;
            }
        }

        if (triggeredStep > activeStep) {
            activeStep = triggeredStep;
            recoveringSince = -1;
            return true;
        }

        if (activeStep < 0 || !metric.isRecovered(value, thresholdOf(activeStep), recoveryMargin, tickRate)) {
            recoveringSince = -1;
            return false;
        }

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
     * The performance measured at the last update, formatted for logs.
     */
    public String formattedValue() {
        return metric.format(lastValue);
    }

    private double thresholdOf(int step) {
        return threshold.applyAsDouble(steps.get(step));
    }
}
