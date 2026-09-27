package com.markaptogo.optimizationutils.config;

import com.markaptogo.optimizationutils.OptimizationUtils;
import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.serdes.commons.SerdesCommons;
import eu.okaeri.configs.validator.okaeri.OkaeriValidator;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import eu.okaeri.configs.yaml.bukkit.serdes.SerdesBukkit;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConfigurationFactory {

    /**
     * The default plugin configuration, bundled in the jar from src/main/resources/config.yml.
     */
    private static final String BUNDLED_PLUGIN_CONFIGURATION = "config.yml";

    private ConfigurationFactory(){
    }

    public static PluginConfiguration createPluginConfiguration(File pluginConfigurationFile) {
        PluginConfiguration pluginConfiguration = ConfigManager.create(PluginConfiguration.class, (it) -> {
            it.withConfigurer(new OkaeriValidator(new YamlBukkitConfigurer()));
            it.withSerdesPack(registry -> {
                registry.register(new SerdesCommons());
                registry.register(new SerdesBukkit());
            });

            it.withBindFile(pluginConfigurationFile);
            it.withLogger(OptimizationUtils.instance().getLogger());
        });

        loadPluginConfiguration(pluginConfiguration);
        return pluginConfiguration;
    }

    /**
     * Loads the plugin configuration from its file. Every setting missing from the file (also inside sections)
     * is taken from the bundled config.yml, and the complete result is written back to the file.
     */
    public static void loadPluginConfiguration(PluginConfiguration pluginConfiguration) {
        try (InputStream bundled = OptimizationUtils.instance().getResource(BUNDLED_PLUGIN_CONFIGURATION)) {
            loadWithDefaults(pluginConfiguration, bundled);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load " + pluginConfiguration.getBindFile(), e);
        }
    }

    private static void loadWithDefaults(OkaeriConfig config, InputStream defaults) throws IOException {
        Yaml yaml = new Yaml();
        Map<String, Object> values = new LinkedHashMap<>();

        if (defaults != null) {
            merge(values, yaml.load(defaults));
        }

        Path file = config.getBindFile();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                merge(values, yaml.load(reader));
            }
        }

        config.load(values);
        config.save();
    }

    /**
     * Copies the values of the source into the target. Sections present in both are merged key by key,
     * everything else (including lists) is replaced.
     */
    @SuppressWarnings("unchecked")
    private static void merge(Map<String, Object> target, Object source) {
        if (!(source instanceof Map<?, ?> sourceMap)) return;

        for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (entry.getValue() instanceof Map<?, ?> && target.get(key) instanceof Map<?, ?> targetSection) {
                merge((Map<String, Object>) targetSection, entry.getValue());
            } else {
                target.put(key, entry.getValue());
            }
        }
    }

    public static DataConfiguration createDataConfiguration(File dataConfigurationFile) {
        return ConfigManager.create(DataConfiguration.class, (it) -> {
            it.withConfigurer(new OkaeriValidator(new YamlBukkitConfigurer()));
            it.withSerdesPack(registry -> {
                registry.register(new SerdesCommons());
                registry.register(new SerdesBukkit());
            });

            it.withBindFile(dataConfigurationFile);
            it.withLogger(OptimizationUtils.instance().getLogger());
            it.saveDefaults();
            it.load(true);
        });
    }
}
