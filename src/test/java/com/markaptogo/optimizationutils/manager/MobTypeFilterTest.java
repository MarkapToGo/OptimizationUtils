package com.markaptogo.optimizationutils.manager;

import com.markaptogo.optimizationutils.config.model.FilterMode;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobTypeFilterTest {

    private final List<String> warnings = new ArrayList<>();

    private Set<EntityType> matchingTypes(FilterMode filterMode, String... entries) {
        return MobTypeFilter.matchingTypes(List.of(entries), filterMode, warnings::add);
    }

    @Test
    void allMatchesEveryMobButNothingElse() {
        Set<EntityType> types = matchingTypes(FilterMode.INCLUDE, "ALL");

        assertTrue(types.contains(EntityType.COW));
        assertTrue(types.contains(EntityType.ZOMBIE));
        assertFalse(types.contains(EntityType.PLAYER));
        assertFalse(types.contains(EntityType.ARMOR_STAND));
        assertFalse(types.contains(EntityType.ITEM));
    }

    @Test
    void typeMatchesOnlyThatTypeIgnoringCaseAndSpaces() {
        assertEquals(Set.of(EntityType.COW), matchingTypes(FilterMode.INCLUDE, " type: cow "));
    }

    @Test
    void bukkitClassMatchesItsSubtypes() {
        Set<EntityType> types = matchingTypes(FilterMode.INCLUDE, "bukkit_class:Animals");

        assertTrue(types.contains(EntityType.COW));
        assertTrue(types.contains(EntityType.PIG));
        assertFalse(types.contains(EntityType.ZOMBIE));
    }

    @Test
    void excludeMatchesEveryOtherMob() {
        Set<EntityType> types = matchingTypes(FilterMode.EXCLUDE, "bukkit_class:Monster");

        assertTrue(types.contains(EntityType.COW));
        assertFalse(types.contains(EntityType.ZOMBIE));
        assertFalse(types.contains(EntityType.ARMOR_STAND));
    }

    @Test
    void invalidEntriesAreIgnoredWithAWarning() {
        Set<EntityType> types = matchingTypes(FilterMode.INCLUDE, "COW", "foo:bar", "bukkit_class:NoSuchClass", "type:PIG");

        assertEquals(Set.of(EntityType.PIG), types);
        assertEquals(3, warnings.size());
    }
}
