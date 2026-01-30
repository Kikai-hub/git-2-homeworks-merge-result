package com.killer;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class PlayerDeathListener implements Listener {

    private final EventManager eventManager;

    public PlayerDeathListener(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player deadPlayer = event.getEntity();
        Player killer = event.getEntity().getKiller();

        // Проверить если умерший игрок - это цель события
        if (eventManager.isEventActive() && eventManager.getTargetPlayer() != null &&
                eventManager.getTargetPlayer().equals(deadPlayer) && killer != null) {

            // Игрок-цель был убит
            eventManager.onTargetPlayerKilled(killer);
        }
    }
}
