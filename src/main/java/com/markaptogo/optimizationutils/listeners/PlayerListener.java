package com.markaptogo.optimizationutils.listeners;

import com.markaptogo.optimizationutils.OptimizationUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Map;
import java.util.UUID;

public class PlayerListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Map<UUID, Integer> viewDistanceOverrides = OptimizationUtils.instance().dataConfiguration().viewDistanceOverrides;
        Integer viewDistance = viewDistanceOverrides.get(player.getUniqueId());
        if (viewDistance != null) {
            // Right away, while the client still shows the loading screen. Applied later, the client would
            // re-render all chunks it just showed.
            player.setViewDistance(viewDistance);
        }
    }
}
