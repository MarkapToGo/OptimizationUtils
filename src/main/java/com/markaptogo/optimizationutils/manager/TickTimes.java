package com.markaptogo.optimizationutils.manager;

/**
 * The durations of the ticks within a time window, oldest first, to calculate the MSPT from.
 */
public final class TickTimes {

    private final long maxAgeMillis;

    // Ring buffer of when each tick ended and how long it took
    private long[] endTimes = new long[1280];
    private double[] durations = new double[1280];
    private int start = 0;
    private int size = 0;

    /**
     * @param maxAgeMillis how long ticks are kept, the longest window that can be averaged over
     */
    public TickTimes(long maxAgeMillis) {
        this.maxAgeMillis = maxAgeMillis;
    }

    public void record(long nowMillis, double durationMillis) {
        // Drop ticks that are too old for any window
        while (size > 0 && endTimes[start] < nowMillis - maxAgeMillis) {
            start = (start + 1) % endTimes.length;
            size--;
        }

        if (size == endTimes.length) {
            grow();
        }

        int index = (start + size) % endTimes.length;
        endTimes[index] = nowMillis;
        durations[index] = durationMillis;
        size++;
    }

    /**
     * Returns the duration of the last tick, or 0 when no tick was recorded yet.
     */
    public double last() {
        return size == 0 ? 0 : durations[(start + size - 1) % endTimes.length];
    }

    /**
     * Returns the average duration of the ticks that ended within the window, or 0 when there are none.
     */
    public double average(long nowMillis, long windowMillis) {
        double sum = 0;
        int count = 0;

        for (int i = size - 1; i >= 0; i--) {
            int index = (start + i) % endTimes.length;
            if (endTimes[index] < nowMillis - windowMillis) break;

            sum += durations[index];
            count++;
        }

        return count == 0 ? 0 : sum / count;
    }

    // Only needed when the tick rate is raised with /tick rate, 1280 ticks cover a minute at 20 TPS
    private void grow() {
        long[] newEndTimes = new long[endTimes.length * 2];
        double[] newDurations = new double[durations.length * 2];
        for (int i = 0; i < size; i++) {
            int index = (start + i) % endTimes.length;
            newEndTimes[i] = endTimes[index];
            newDurations[i] = durations[index];
        }

        endTimes = newEndTimes;
        durations = newDurations;
        start = 0;
    }
}
