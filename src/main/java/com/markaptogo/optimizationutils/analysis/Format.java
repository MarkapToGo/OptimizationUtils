package com.markaptogo.optimizationutils.analysis;

import java.util.Locale;

/**
 * Formats numbers and bars for the analysis output.
 */
public final class Format {

    private Format() {
    }

    /**
     * Like "4,812".
     */
    public static String count(long count) {
        return String.format(Locale.ROOT, "%,d", count);
    }

    /**
     * One decimal for small values, like "4.2" or "812".
     */
    public static String decimal(double value) {
        return value < 10 && value != Math.rint(value)
            ? String.format(Locale.ROOT, "%.1f", value)
            : String.format(Locale.ROOT, "%,.0f", value);
    }

    /**
     * Up to two decimals at any size, like "20.25", "0.3" or "1,234", so the costs of a score visibly add up to it.
     */
    public static String cost(double value) {
        String formatted = String.format(Locale.ROOT, "%,.2f", value);
        return formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /**
     * Like "120/s".
     */
    public static String perSecond(double value) {
        return decimal(value) + "/s";
    }

    /**
     * How many of the width are filled for the value, at least one for anything above zero.
     */
    public static int filled(double value, double max, int width) {
        if (value <= 0 || max <= 0) return 0;
        return Math.clamp(Math.round(value / max * width), 1, width);
    }

    /**
     * Cuts the text to the length, ending with "…" when cut.
     */
    public static String shorten(String text, int length) {
        return text.length() <= length ? text : text.substring(0, length - 1) + "…";
    }

    /**
     * Like "34s", "5m" or "2h".
     */
    public static String duration(long millis) {
        long seconds = Math.max(0, millis / 1000);
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return seconds / 60 + "m";
        return seconds / 3600 + "h";
    }
}
