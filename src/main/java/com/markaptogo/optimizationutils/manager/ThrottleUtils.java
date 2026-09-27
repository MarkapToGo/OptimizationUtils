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
        TICK_TIMES.record(System.currentTimeMillis(), mspt);
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
        long now = System.currentTimeMillis();
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
