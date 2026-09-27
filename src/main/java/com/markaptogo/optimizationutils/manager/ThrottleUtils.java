package com.markaptogo.optimizationutils.manager;

import com.markaptogo.optimizationutils.OptimizationUtils;
import com.markaptogo.optimizationutils.config.model.PerformanceMetric;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.Locale;

public class ThrottleUtils {
    // Avoid spamming logs
    private static long lastLogTime = -1;

    /**
     * Duration of the last finished tick in milliseconds, updated by {@link com.markaptogo.optimizationutils.listeners.ServerTickListener}.
     */
    private static double lastTickMspt = 0;

    public static boolean shouldThrottle(World world, PerformanceMetric metric, float threshold, String action) {
        double value = getValue(metric);

        // If server is overloaded, throttle
        if (isReached(metric, value, threshold)) {
            if (lastLogTime == -1 || System.currentTimeMillis() - lastLogTime > 10000) { // Log every 10 seconds
                lastLogTime = System.currentTimeMillis();
                OptimizationUtils.instance().getLogger().info("Server is overloaded (" + format(metric, value) + "), throttling " + action + ". Entities count: " + world.getEntityCount());
            }

            return true;
        }

        return false;
    }

    /**
     * Returns true when the value is at or beyond the threshold, so the server is at least this laggy.
     */
    public static boolean isReached(PerformanceMetric metric, double value, double threshold) {
        return switch (metric) {
            case MSPT -> value >= threshold;
            case TPS -> value <= threshold;
        };
    }

    /**
     * Returns true when the value is back on the good side of the threshold by more than the margin.
     */
    public static boolean isRecovered(PerformanceMetric metric, double value, double threshold, double margin) {
        return switch (metric) {
            case MSPT -> value < threshold - margin;
            // The TPS never goes above the tick rate, so running at full speed always counts as recovered
            case TPS -> value > threshold + margin || value >= tickRate();
        };
    }

    public static String format(PerformanceMetric metric, double value) {
        return switch (metric) {
            case MSPT -> String.format(Locale.ROOT, "%.2fms MSPT", value);
            case TPS -> String.format(Locale.ROOT, "%.2f TPS", value);
        };
    }

    public static void recordTickDuration(double mspt) {
        lastTickMspt = mspt;
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
        return switch (OptimizationUtils.instance().pluginConfiguration().msptCalculationMode) {
            case AVERAGE_5S -> Bukkit.getAverageTickTime();
            case LAST_TICK -> lastTickMspt;
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

    private static double tickRate() {
        return Bukkit.getServerTickManager().getTickRate();
    }
}
