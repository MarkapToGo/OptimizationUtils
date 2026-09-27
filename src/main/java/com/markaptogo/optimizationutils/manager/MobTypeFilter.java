package com.markaptogo.optimizationutils.manager;

import com.markaptogo.optimizationutils.config.model.FilterMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Works out which mob types the entries of disableEntityTicking.entities select, once per config load instead of
 * for every mob.
 */
final class MobTypeFilter {

    private MobTypeFilter() {
    }

    /**
     * Returns the mob types selected by the entries ({@code ALL}, {@code type:COW} or {@code bukkit_class:Mob}).
     * Invalid entries are passed to warn and ignored.
     */
    static Set<EntityType> matchingTypes(List<String> entries, FilterMode filterMode, Consumer<String> warn) {
        List<Predicate<EntityType>> listed = new ArrayList<>();
        for (String entry : entries) {
            Predicate<EntityType> predicate = parse(entry.trim(), warn);
            if (predicate != null) {
                listed.add(predicate);
            }
        }

        Set<EntityType> types = EnumSet.noneOf(EntityType.class);
        for (EntityType type : EntityType.values()) {
            Class<? extends Entity> entityClass = type.getEntityClass();
            if (entityClass == null || !Mob.class.isAssignableFrom(entityClass)) continue;

            boolean isListed = listed.stream().anyMatch(predicate -> predicate.test(type));
            if (isListed == (filterMode == FilterMode.INCLUDE)) {
                types.add(type);
            }
        }

        return types;
    }

    /**
     * Parses a single config entry, or returns null when it is invalid.
     */
    private static Predicate<EntityType> parse(String entry, Consumer<String> warn) {
        if (entry.equalsIgnoreCase("ALL")) return type -> true;

        int separator = entry.indexOf(':');
        if (separator < 0) {
            warn.accept("Ignoring \"" + entry + "\" in disableEntityTicking.entities, it has no type prefix (ALL, type:, bukkit_class:)");
            return null;
        }

        String prefix = entry.substring(0, separator).trim();
        String value = entry.substring(separator + 1).trim();

        return switch (prefix.toLowerCase(Locale.ROOT)) {
            case "type" -> type -> value.equalsIgnoreCase(type.name());
            case "bukkit_class" -> {
                Class<?> bukkitClass = bukkitClass(value, warn);
                yield bukkitClass == null ? null : type -> bukkitClass.isAssignableFrom(type.getEntityClass());
            }
            default -> {
                warn.accept("Unknown type prefix \"" + prefix + "\" in disableEntityTicking.entities");
                yield null;
            }
        };
    }

    /**
     * Resolves a class from {@code org.bukkit.entity} by its simple name (case sensitive, like {@code Mob} or {@code Monster}).
     * Returns null when it does not exist.
     */
    private static Class<?> bukkitClass(String name, Consumer<String> warn) {
        try {
            return Class.forName("org.bukkit.entity." + name);
        } catch (ClassNotFoundException e) {
            warn.accept("Unknown bukkit class \"" + name + "\" in disableEntityTicking.entities");
            return null;
        }
    }
}
