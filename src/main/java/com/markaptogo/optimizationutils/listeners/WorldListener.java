package com.markaptogo.optimizationutils.listeners;

import com.markaptogo.optimizationutils.manager.DynamicDistanceManager;
import com.markaptogo.optimizationutils.manager.DynamicRandomTickManager;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

public class WorldListener implements Listener {

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        World world = event.getWorld();

        DynamicDistanceManager.VIEW.applyStoredDistance(world);
        DynamicDistanceManager.SIMULATION.applyStoredDistance(world);
        DynamicRandomTickManager.onWorldLoad(world);
    }
}
