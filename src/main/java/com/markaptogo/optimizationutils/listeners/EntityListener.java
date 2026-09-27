package com.markaptogo.optimizationutils.listeners;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.PreSpawnerSpawnEvent;
import com.markaptogo.optimizationutils.manager.DynamicMobcapManager;
import com.markaptogo.optimizationutils.manager.EntityTickManager;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class EntityListener implements Listener {

    @EventHandler
    public void onEntityAddToWorld(EntityAddToWorldEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }

        EntityTickManager.onMobAddedToWorld(mob);
    }

    @EventHandler
    public void onSpawnerSpawn(PreSpawnerSpawnEvent event) {
        if (!DynamicMobcapManager.shouldThrottleSpawners()) {
            return;
        }

        // Aborting a cancelled spawn makes the spawner wait its regular delay before trying again
        event.setCancelled(true);
        event.setShouldAbortSpawn(true);
    }
}
