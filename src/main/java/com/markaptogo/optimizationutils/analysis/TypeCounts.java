package com.markaptogo.optimizationutils.analysis;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How many of each type (villager, hopper, repeater, ...) there are and what they cost, for one kind of thing
 * (entities, block entities or scheduled ticks) of one chunk or of all chunks together.
 */
public final class TypeCounts {

    private final Map<String, Integer> counts = new HashMap<>();
    private final Map<String, Double> costs = new HashMap<>();
    private final Map<String, Position> positions = new HashMap<>();
    private int total = 0;
    private double cost = 0;

    void add(String type, int count, double cost, Position position) {
        counts.merge(type, count, Integer::sum);
        costs.merge(type, cost, Double::sum);
        if (position != null) {
            positions.putIfAbsent(type, position);
        }
        this.total += count;
        this.cost += cost;
    }

    void addAll(TypeCounts other) {
        for (Map.Entry<String, Integer> entry : other.counts.entrySet()) {
            add(entry.getKey(), entry.getValue(), other.costs.get(entry.getKey()), null);
        }
    }

    public int total() {
        return total;
    }

    public double cost() {
        return cost;
    }

    public boolean isEmpty() {
        return counts.isEmpty();
    }

    public int types() {
        return counts.size();
    }

    public Set<String> typeNames() {
        return Collections.unmodifiableSet(counts.keySet());
    }

    public int count(String type) {
        return counts.getOrDefault(type, 0);
    }

    public double cost(String type) {
        return costs.getOrDefault(type, 0.0);
    }

    /**
     * Where one of this type is, or null.
     */
    public Position position(String type) {
        return positions.get(type);
    }

    public Position anyPosition() {
        return positions.values().stream().findFirst().orElse(null);
    }

    public List<String> byCount() {
        return counts.keySet().stream()
            .sorted(Comparator.comparingInt(this::count).reversed().thenComparing(Comparator.naturalOrder()))
            .toList();
    }

    public List<String> byCost() {
        return counts.keySet().stream()
            .sorted(Comparator.<String>comparingDouble(this::cost).reversed()
                .thenComparing(Comparator.comparingInt(this::count).reversed())
                .thenComparing(Comparator.naturalOrder()))
            .toList();
    }
}
