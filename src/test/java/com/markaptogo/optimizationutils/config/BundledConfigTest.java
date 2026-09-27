package com.markaptogo.optimizationutils.config;

import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.serdes.commons.SerdesCommons;
import eu.okaeri.configs.validator.okaeri.OkaeriValidator;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Checks src/main/resources/config.yml against {@link PluginConfiguration}. The values may differ, since the bundled
 * config is where the defaults are edited, but it has to contain exactly the settings of the code.
 */
class BundledConfigTest {

    @Test
    void bundledConfigHasExactlyTheSettingsOfTheCode(@TempDir Path dir) throws IOException {
        Path generated = dir.resolve("config.yml");
        ConfigManager.create(PluginConfiguration.class, it -> {
            configure(it);
            it.withBindFile(generated);
            it.saveDefaults();
        });

        Set<String> expected = keys(new Yaml().load(Files.readString(generated)));
        Set<String> actual = keys(new Yaml().load(bundled()));

        assertEquals(expected, actual, "src/main/resources/config.yml has other settings than PluginConfiguration."
            + " Settings the code has look like this:\n" + Files.readString(generated));
    }

    @Test
    void bundledConfigLoads() throws IOException {
        PluginConfiguration config = ConfigManager.create(PluginConfiguration.class, BundledConfigTest::configure);
        config.load(bundled());

        assertFalse(config.dynamicMobcap.steps.isEmpty());
        assertFalse(config.dynamicRandomTickSpeed.steps.isEmpty());
        assertNotNull(config.dynamicViewDistance.metric);
    }

    @Test
    void oldRandomTickThresholdBecomesAStep() {
        Map<String, Object> randomTickSpeed = new LinkedHashMap<>(Map.of("enabled", true, "threshold", 42.0));
        Map<String, Object> values = new LinkedHashMap<>(Map.of("dynamicRandomTickSpeed", randomTickSpeed));

        ConfigurationFactory.migrate(values);

        assertEquals(Map.of(
            "enabled", true,
            "steps", List.of(Map.of("threshold", 42.0, "randomTickSpeed", 0))
        ), randomTickSpeed);
    }

    private static void configure(eu.okaeri.configs.OkaeriConfig config) {
        // Like ConfigurationFactory, without SerdesBukkit, which needs a running server
        config.withConfigurer(new OkaeriValidator(new YamlBukkitConfigurer()));
        config.withSerdesPack(registry -> registry.register(new SerdesCommons()));
    }

    private static String bundled() throws IOException {
        try (InputStream stream = BundledConfigTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(stream, "config.yml is missing from src/main/resources");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Returns the paths of all settings, like "dynamicMobcap.steps[].threshold".
     */
    private static Set<String> keys(Object yaml) {
        Set<String> keys = new TreeSet<>();
        collectKeys("", yaml, keys);
        return keys;
    }

    private static void collectKeys(String path, Object value, Set<String> keys) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = path.isEmpty() ? String.valueOf(entry.getKey()) : path + "." + entry.getKey();
                keys.add(key);
                collectKeys(key, entry.getValue(), keys);
            }
        } else if (value instanceof List<?> list) {
            for (Object element : list) {
                collectKeys(path + "[]", element, keys);
            }
        }
    }
}
