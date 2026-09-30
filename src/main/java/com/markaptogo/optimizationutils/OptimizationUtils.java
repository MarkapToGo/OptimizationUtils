package com.markaptogo.optimizationutils;

import com.markaptogo.optimizationutils.commands.OptimizationUtilsCommand;
import com.markaptogo.optimizationutils.config.AsyncFileWriter;
import com.markaptogo.optimizationutils.config.ConfigurationFactory;
import com.markaptogo.optimizationutils.config.DataConfiguration;
import com.markaptogo.optimizationutils.config.PluginConfiguration;
import com.markaptogo.optimizationutils.listeners.EntityListener;
import com.markaptogo.optimizationutils.listeners.PlayerListener;
import com.markaptogo.optimizationutils.listeners.ServerTickListener;
import com.markaptogo.optimizationutils.listeners.WorldListener;
import com.markaptogo.optimizationutils.manager.DynamicDistanceManager;
import com.markaptogo.optimizationutils.manager.DynamicMobcapManager;
import com.markaptogo.optimizationutils.manager.DynamicRandomTickManager;
import com.markaptogo.optimizationutils.manager.EntityTickManager;
import com.markaptogo.optimizationutils.metrics.Metrics;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class OptimizationUtils extends JavaPlugin {

    private PluginConfiguration pluginConfiguration;
    private DataConfiguration dataConfiguration;
    private AsyncFileWriter dataWriter;

    private static OptimizationUtils instance;

    @Override
    public void onEnable() {
        instance = this;
        this.dataWriter = new AsyncFileWriter("OptimizationUtils Data Writer", getLogger());

        setupMetrics();

        this.pluginConfiguration = ConfigurationFactory.createPluginConfiguration(new File(this.getDataFolder(), "config.yml"));
        this.dataConfiguration = ConfigurationFactory.createDataConfiguration(new File(this.getDataFolder(), "data.yml"));

        // The server loaded the worlds with the distances from server.properties, not the ones set by our commands
        for (World world : Bukkit.getWorlds()) {
            DynamicDistanceManager.VIEW.applyStoredDistance(world);
            DynamicDistanceManager.SIMULATION.applyStoredDistance(world);
        }

        EntityTickManager.sync();
        DynamicMobcapManager.sync();
        DynamicDistanceManager.VIEW.sync();
        DynamicDistanceManager.SIMULATION.sync();
        // Also gives worlds their random tick speed back if the server stopped while it was lowered
        DynamicRandomTickManager.sync();

        Bukkit.getPluginManager().registerEvents(new EntityListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(), this);
        Bukkit.getPluginManager().registerEvents(new ServerTickListener(), this);
        Bukkit.getPluginManager().registerEvents(new WorldListener(), this);

        // Plugin startup logic
        registerCommands();
    }

    @Override
    public void onDisable() {
        // Give unticked mobs back to the tick list
        EntityTickManager.disable();

        // Give worlds their normal mobcap, view and simulation distance and random tick speed back
        DynamicMobcapManager.disable();
        DynamicDistanceManager.VIEW.disable();
        DynamicDistanceManager.SIMULATION.disable();
        DynamicRandomTickManager.disable();

        // Wait for queued writes, then save the final data on the main thread
        this.dataWriter.close();
        this.dataConfiguration.save();
    }

    private void registerCommands() {
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> OptimizationUtilsCommand.register(event.registrar()));
    }

    private void setupMetrics() {
        // bStats: OptimizationUtils-Markap
        int pluginId = 34344;
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

    /**
     * Writes files on a background thread, like data.yml and exports.
     */
    public AsyncFileWriter fileWriter() {
        return dataWriter;
    }

    /**
     * Saves data.yml. The data is copied on the main thread (as YAML) and written to disk on a background thread.
     */
    public void saveDataConfiguration() {
        this.dataWriter.write(this.dataConfiguration.getBindFile(), this.dataConfiguration.saveToString());
    }

    public void reloadConfiguration() {
        ConfigurationFactory.loadPluginConfiguration(this.pluginConfiguration);
        // The file is behind the data until the queued writes are done
        this.dataWriter.flush();
        this.dataConfiguration.load();

        EntityTickManager.sync();
        DynamicMobcapManager.sync();
        DynamicDistanceManager.VIEW.sync();
        DynamicDistanceManager.SIMULATION.sync();
        DynamicRandomTickManager.sync();
    }
}
