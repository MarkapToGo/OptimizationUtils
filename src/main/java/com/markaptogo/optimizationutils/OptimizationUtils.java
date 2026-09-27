package com.markaptogo.optimizationutils;

import com.markaptogo.optimizationutils.commands.OptimizationUtilsCommand;
import com.markaptogo.optimizationutils.config.ConfigurationFactory;
import com.markaptogo.optimizationutils.config.DataConfiguration;
import com.markaptogo.optimizationutils.config.PluginConfiguration;
import com.markaptogo.optimizationutils.listeners.EntityListener;
import com.markaptogo.optimizationutils.listeners.PlayerListener;
import com.markaptogo.optimizationutils.listeners.ServerTickListener;
import com.markaptogo.optimizationutils.manager.DynamicDistanceManager;
import com.markaptogo.optimizationutils.manager.DynamicMobcapManager;
import com.markaptogo.optimizationutils.manager.EntityTickManager;
import com.markaptogo.optimizationutils.manager.ThrottleUtils;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Iterator;
import java.util.Map;

public final class OptimizationUtils extends JavaPlugin {

    private PluginConfiguration pluginConfiguration;
    private DataConfiguration dataConfiguration;

    private static OptimizationUtils instance;

    @Override
    public void onEnable() {
        instance = this;

        setupMetrics();

        this.pluginConfiguration = ConfigurationFactory.createPluginConfiguration(new File(this.getDataFolder(), "config.yml"));
        this.dataConfiguration = ConfigurationFactory.createDataConfiguration(new File(this.getDataFolder(), "data.yml"));

        // Restore original random tick speeds on enable if server has been stopped incorrectly
        restoreOriginalRandomTickSpeeds();

        EntityTickManager.sync();
        DynamicMobcapManager.sync();
        DynamicDistanceManager.VIEW.sync();
        DynamicDistanceManager.SIMULATION.sync();

        Bukkit.getPluginManager().registerEvents(new EntityListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(), this);
        Bukkit.getPluginManager().registerEvents(new ServerTickListener(), this);

        // Plugin startup logic
        registerCommands();

        // Dynamic Random Tick Speed Task
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!this.pluginConfiguration().dynamicRandomTickSpeed.enabled) {
                // Give worlds their random ticks back if the feature was turned off while throttling
                restoreOriginalRandomTickSpeeds();
                return;
            }

            for (World world : Bukkit.getWorlds()) {
                if (ThrottleUtils.shouldThrottle(world, this.pluginConfiguration().dynamicRandomTickSpeed.metric, this.pluginConfiguration().dynamicRandomTickSpeed.threshold, "RandomTickSpeed")) {
                    // Store original randomtickspeed if not already stored
                    int currentRandomTickSpeed = world.getGameRuleValue(GameRules.RANDOM_TICK_SPEED);

                    if (!this.dataConfiguration().originalRandomTickSpeeds.containsKey(world.getName())) {
                        if (this.pluginConfiguration().debug) {
                            this.getLogger().info("Storing original random tick speed for world " + world.getName() + ": " + currentRandomTickSpeed);
                        }
                        this.dataConfiguration().originalRandomTickSpeeds.put(world.getName(), currentRandomTickSpeed);
                        this.dataConfiguration().save();
                    } else {
                        if (currentRandomTickSpeed != 0) {
                            // Update in case it was changed manually
                            if (this.pluginConfiguration().debug) {
                                this.getLogger().info("Updating original random tick speed for world " + world.getName() + ": " + currentRandomTickSpeed);
                            }
                            this.dataConfiguration().originalRandomTickSpeeds.put(world.getName(), currentRandomTickSpeed);
                            this.dataConfiguration().save();
                        }
                    }

                    // Disable random ticks
                    world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
                } else {
                    // Restore original random tick speed if it was changed
                    if (this.dataConfiguration().originalRandomTickSpeeds.containsKey(world.getName())) {
                        if (this.pluginConfiguration().debug) {
                            this.getLogger().info("Restoring original random tick speed for world " + world.getName() + ": " + this.dataConfiguration().originalRandomTickSpeeds.get(world.getName()));
                        }
                        // Restore
                        int originalRandomTickSpeed = this.dataConfiguration().originalRandomTickSpeeds.get(world.getName());
                        world.setGameRule(GameRules.RANDOM_TICK_SPEED, originalRandomTickSpeed);

                        // Remove from map
                        this.dataConfiguration().originalRandomTickSpeeds.remove(world.getName());
                        this.dataConfiguration().save();
                    }
                }
            }
        }, 1L, 1L);
    }

    @Override
    public void onDisable() {
        // Restore original random tick speeds on disable
        restoreOriginalRandomTickSpeeds();

        // Give unticked mobs back to the tick list
        EntityTickManager.disable();

        // Give worlds their normal mobcap, view and simulation distance back
        DynamicMobcapManager.disable();
        DynamicDistanceManager.VIEW.disable();
        DynamicDistanceManager.SIMULATION.disable();
    }

    /**
     * Restores the stored random tick speed of every loaded world. Entries of worlds that are not
     * loaded are kept, so they can be restored once the world is loaded again.
     */
    private void restoreOriginalRandomTickSpeeds() {
        boolean changed = false;

        Iterator<Map.Entry<String, Integer>> iterator = this.dataConfiguration().originalRandomTickSpeeds.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Integer> entry = iterator.next();
            World world = Bukkit.getWorld(entry.getKey());
            if (world == null) continue;

            world.setGameRule(GameRules.RANDOM_TICK_SPEED, entry.getValue());
            iterator.remove();
            changed = true;
        }

        if (changed) {
            this.dataConfiguration().save();
        }
    }

    private void registerCommands() {
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> OptimizationUtilsCommand.register(event.registrar()));
    }

    private void setupMetrics() {
        int pluginId = 26099;
        Metrics metrics = new Metrics(this, pluginId);
    }

    public static OptimizationUtils instance() {
        return instance;
    }

    public PluginConfiguration pluginConfiguration() {
        return pluginConfiguration;
    }

    public DataConfiguration dataConfiguration() {
        return dataConfiguration;
    }

    public void reloadConfiguration() {
        ConfigurationFactory.loadPluginConfiguration(this.pluginConfiguration);
        this.dataConfiguration.load();

        EntityTickManager.sync();
        DynamicMobcapManager.sync();
        DynamicDistanceManager.VIEW.sync();
        DynamicDistanceManager.SIMULATION.sync();
    }
}
