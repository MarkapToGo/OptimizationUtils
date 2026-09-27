package com.markaptogo.optimizationutils.listeners;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import com.markaptogo.optimizationutils.manager.ThrottleUtils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ServerTickListener implements Listener {

    @EventHandler
    public void onServerTickEnd(ServerTickEndEvent event) {
        ThrottleUtils.recordTickDuration(event.getTickDuration());
    }
}
