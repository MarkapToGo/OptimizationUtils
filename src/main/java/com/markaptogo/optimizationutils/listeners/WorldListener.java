package com.markaptogo.optimizationutils.listeners;

import com.markaptogo.optimizationutils.manager.DynamicRandomTickManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

public class WorldListener implements Listener {

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        DynamicRandomTickManager.onWorldLoad(event.getWorld());
    }
}
