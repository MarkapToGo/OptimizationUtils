package com.markaptogo.optimizationutils.manager;

import com.markaptogo.optimizationutils.OptimizationUtils;
import com.markaptogo.optimizationutils.config.model.PerformanceMetric;
import org.bukkit.Bukkit;

public class ThrottleUtils {

    /**
     * Durations of the ticks of the last minute, recorded by {@link com.markaptogo.optimizationutils.listeners.ServerTickListener}.
     */
    private static final TickTimes TICK_TIMES = new TickTimes(60_000);

    public static void recordTickDuration(double mspt) {
        TICK_TIMES.record(nowMillis(), mspt);
    }

    /**
     * Milliseconds for measuring time spans. Unlike the system time, never jumps (e.g. when the clock is synced).
     */
    public static long nowMillis() {
        return System.nanoTime() / 1_000_000;
    }

    /**
     * Warns about steps that would always be active, like a TPS threshold of 20, since the TPS never goes above the tick rate.
     */
    public static void warnAboutAlwaysReachedSteps(String feature, StepTracker<?> tracker) {
        for (double threshold : tracker.alwaysReachedThresholds(tickRate())) {
            OptimizationUtils.instance().getLogger().warning("The step with threshold " + threshold + " in " + feature
                + " is always active, since the " + tracker.metric() + " never gets better than that. Did you switch the"
                + " metric without changing the thresholds? " + (tracker.metric() == PerformanceMetric.TPS
                ? "TPS thresholds have to be below the tick rate (" + tickRate() + ")." : "MSPT thresholds have to be above 0."));
        }
    }

    public static double getValue(PerformanceMetric metric) {
        return switch (metric) {
            case MSPT -> getMspt();
            case TPS -> getTps();
        };
    }

    /**
     * Gets the milliseconds per tick (MSPT) of the server.
     */
    public static double getMspt() {
        long now = nowMillis();
        return switch (OptimizationUtils.instance().pluginConfiguration().msptCalculationMode) {
            case LAST_TICK -> TICK_TIMES.last();
            case AVERAGE_5S -> Bukkit.getAverageTickTime();
            case AVERAGE_10S -> TICK_TIMES.average(now, 10_000);
            case AVERAGE_1M -> TICK_TIMES.average(now, 60_000);
        };
    }

    /**
     * Gets the ticks per second (TPS) of the server, calculated from the MSPT. Never above the server's tick rate
     * (20 unless changed with /tick rate), since the server waits for the next tick when it is done early.
     */
    public static double getTps() {
        double tickRate = tickRate();
        double mspt = getMspt();
        if (mspt <= 0) return tickRate;

        return Math.min(tickRate, 1000.0 / mspt);
    }

    public static double tickRate() {
        return Bukkit.getServerTickManager().getTickRate();
    }
}
