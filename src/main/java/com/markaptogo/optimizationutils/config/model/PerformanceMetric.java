package com.markaptogo.optimizationutils.config.model;

import java.util.Locale;

public enum PerformanceMetric {
    /**
     * Milliseconds per tick, higher is worse.
     */
    MSPT,
    /**
     * Ticks per second, lower is worse.
     */
    TPS;

    /**
     * Returns true when the value is at or beyond the threshold, so the server is at least this laggy.
     */
    public boolean isReached(double value, double threshold) {
        return switch (this) {
            case MSPT -> value >= threshold;
            case TPS -> value <= threshold;
        };
    }

    /**
     * Returns true when the value is back on the good side of the threshold by more than the margin.
     */
    public boolean isRecovered(double value, double threshold, double margin, double tickRate) {
        return switch (this) {
            case MSPT -> value < threshold - margin;
            // The TPS never goes above the tick rate, so running at full speed always counts as recovered
            case TPS -> value > threshold + margin || value >= tickRate;
        };
    }

    public String format(double value) {
        return switch (this) {
            case MSPT -> String.format(Locale.ROOT, "%.2fms MSPT", value);
            case TPS -> String.format(Locale.ROOT, "%.2f TPS", value);
        };
    }
}
