package com.markaptogo.optimizationutils.config.model;

public enum MsptCalculationMode {
    /**
     * Uses the current MSPT of the last tick.
     */
    LAST_TICK,
    /**
     * Uses the average MSPT over the last 5 seconds.
     */
    AVERAGE_5S,
    /**
     * Uses the average MSPT over the last 10 seconds.
     */
    AVERAGE_10S,
    /**
     * Uses the average MSPT over the last minute.
     */
    AVERAGE_1M
}
