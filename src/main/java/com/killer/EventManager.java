package com.killer;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class EventManager {

    private final KillerEventPlugin plugin;
    private Player targetPlayer;
    private long eventEndTime;
    private long eventStartTime;
    private BukkitTask eventTask;
    private BukkitTask coordinatesBroadcastTask;
    private BukkitTask bossBarUpdateTask;
    private BossBar bossBar;
    private boolean eventActive = false;

    public EventManager(KillerEventPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        // Запустить цикл проверки для начала события каждые 2 суток
        long intervalTicks = plugin.getConfig().getLong("event-interval-hours") * 20 * 60 * 60; // в тиках
        Bukkit.getScheduler().runTaskTimer(plugin, this::startEvent, intervalTicks, intervalTicks);
    }

    public void startEvent() {
        // Проверить есть ли игроки онлайн
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());

        // Получить минимальное количество игроков из конфига
        int minimumPlayers = plugin.getConfig().getInt("minimum-players", 3);
        
        if (onlinePlayers.size() < minimumPlayers) {
            plugin.getLogger().warning("Not enough players to start the event! Required: " + minimumPlayers + ", Online: " + onlinePlayers.size());
            return;
        }

        // Случайно выбрать игрока
        targetPlayer = onlinePlayers.get(new Random().nextInt(onlinePlayers.size()));
        if (targetPlayer == null) {
            plugin.getLogger().warning("Failed to select target player!");
            return;
        }
        eventActive = true;

        // Установить время окончания события
        long durationMinutes = plugin.getConfig().getLong("event-duration-minutes");
        eventStartTime = System.currentTimeMillis();
        eventEndTime = eventStartTime + (durationMinutes * 60 * 1000);

        // Отправить сообщение о начале события
        String startMessage = plugin.getConfig().getString("messages.event-started");
        if (startMessage != null) {
            startMessage = startMessage
                    .replace("{target}", targetPlayer.getName())
                    .replace("{duration}", String.valueOf(durationMinutes));
            Bukkit.broadcastMessage(startMessage);
        }
        plugin.getLogger().info("Event started with target: " + targetPlayer.getName());

        // Создать босс-бар для игрока-цели
        createBossBar();

        // Запустить трансляцию координат каждые 10 минут
        if (targetPlayer != null) {
            long broadcastIntervalMinutes = plugin.getConfig().getLong("coordinates-broadcast-interval-minutes");
            coordinatesBroadcastTask = Bukkit.getScheduler().runTaskTimer(plugin, this::broadcastTargetCoordinates,
                    broadcastIntervalMinutes * 20 * 60, broadcastIntervalMinutes * 20 * 60);

            // Запустить проверку окончания события
            eventTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkEventEnd, 20L, 20L);
            
            // Запустить обновление босс-бара каждую секунду
            bossBarUpdateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateBossBar, 20L, 20L);
        }
    }

    private void broadcastTargetCoordinates() {
        if (!eventActive || targetPlayer == null || !targetPlayer.isOnline()) {
            return;
        }

        String coordMessage = plugin.getConfig().getString("messages.coordinates-broadcast");
        if (coordMessage != null && targetPlayer.getLocation() != null) {
            coordMessage = coordMessage
                    .replace("{x}", String.valueOf(targetPlayer.getLocation().getBlockX()))
                    .replace("{y}", String.valueOf(targetPlayer.getLocation().getBlockY()))
                    .replace("{z}", String.valueOf(targetPlayer.getLocation().getBlockZ()))
                    .replace("{world}", targetPlayer.getWorld().getName());
            Bukkit.broadcastMessage(coordMessage);
        }
    }

    private void checkEventEnd() {
        if (!eventActive || targetPlayer == null) {
            return;
        }

        // Проверить окончился ли таймер события
        if (System.currentTimeMillis() >= eventEndTime) {
            // Игрок выжил
            endEventSurvived();
            return;
        }

        // Проверить онлайн ли игрок (не награждать если вышел)
        if (!targetPlayer.isOnline()) {
            // Событие обрабатывается в PlayerQuitListener
            // Здесь просто останавливаем проверку
            return;
        }
    }

    public void onTargetPlayerKilled(Player killer) {
        if (!eventActive || targetPlayer == null) {
            return;
        }

        // Игрок-цель был убит
        endEventKilled(killer);
    }

    public void onTargetPlayerQuit() {
        if (!eventActive || targetPlayer == null) {
            return;
        }

        // Игрок-цель вышел из игры
        endEventQuit();
    }

    private void endEventSurvived() {
        if (!eventActive || targetPlayer == null) {
            return;
        }

        eventActive = false;

        String endMessage = plugin.getConfig().getString("messages.event-ended-survived");
        if (endMessage != null) {
            endMessage = endMessage.replace("{target}", targetPlayer.getName());
            Bukkit.broadcastMessage(endMessage);
        }

        // Дать награду выжившему игроку
        giveReward(targetPlayer, "survivor-reward");

        stopEvent();
    }

    private void endEventKilled(Player killer) {
        if (!eventActive || targetPlayer == null || killer == null) {
            return;
        }

        eventActive = false;

        String endMessage = plugin.getConfig().getString("messages.event-ended-killed");
        if (endMessage != null) {
            endMessage = endMessage
                    .replace("{killer}", killer.getName())
                    .replace("{target}", targetPlayer.getName());
            Bukkit.broadcastMessage(endMessage);
        }

        // Дать награду убийце
        giveReward(killer, "killer-reward");

        stopEvent();
    }

    private void endEventQuit() {
        if (!eventActive || targetPlayer == null) {
            return;
        }

        eventActive = false;

        String targetName = targetPlayer.getName();

        // Отправить сообщение о выходе цели
        String endMessage = plugin.getConfig().getString("messages.event-ended-quit");
        if (endMessage != null) {
            endMessage = endMessage.replace("{target}", targetName);
            Bukkit.broadcastMessage(endMessage);
        }

        // Применить наказания
        applyPenalties(targetPlayer);

        stopEvent();
    }

    private void applyPenalties(Player player) {
        if (player == null) {
            return;
        }

        // Получить список команд наказания из конфига
        var penaltyCommands = plugin.getConfig().getStringList("penalties.quit-penalty.commands");
        
        if (penaltyCommands != null && !penaltyCommands.isEmpty()) {
            for (String command : penaltyCommands) {
                if (command != null && !command.isEmpty()) {
                    command = command.replace("{player}", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }
            }
        }

        // Отправить сообщение игроку о наказании (если он онлайн)
        if (player.isOnline()) {
            String message = plugin.getConfig().getString("penalties.quit-penalty.message");
            if (message != null) {
                message = message.replace("{player}", player.getName());
                player.sendMessage(message);
            }
        }
    }

    private void giveReward(Player player, String rewardType) {
        if (player == null) {
            return;
        }

        String command = plugin.getConfig().getString("rewards." + rewardType + ".command");
        String message = plugin.getConfig().getString("rewards." + rewardType + ".message");

        if (command != null) {
            command = command.replace("{player}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }

        if (message != null) {
            message = message.replace("{player}", player.getName());
            player.sendMessage(message);
        }
    }

    public void stopEvent() {
        eventActive = false;

        if (coordinatesBroadcastTask != null) {
            coordinatesBroadcastTask.cancel();
            coordinatesBroadcastTask = null;
        }

        if (eventTask != null) {
            eventTask.cancel();
            eventTask = null;
        }

        if (bossBarUpdateTask != null) {
            bossBarUpdateTask.cancel();
            bossBarUpdateTask = null;
        }

        // Удалить босс-бар
        removeBossBar();

        targetPlayer = null;
    }

    private void createBossBar() {
        if (targetPlayer == null) {
            return;
        }

        // Создать босс-бар с красным цветом
        bossBar = BossBar.bossBar(
                Component.text("ВЫ ЦЕЛЬ! ПОСТАРАЙТЕСЬ ВЫЖИТЬ", NamedTextColor.RED),
                0.0f,
                BossBar.Color.RED,
                BossBar.Overlay.PROGRESS
        );

        // Показать босс-бар игроку-цели
        targetPlayer.showBossBar(bossBar);
    }

    private void updateBossBar() {
        if (bossBar == null || targetPlayer == null || !targetPlayer.isOnline()) {
            return;
        }

        // Вычислить прогресс (от 0.0 до 1.0)
        long currentTime = System.currentTimeMillis();
        long totalDuration = eventEndTime - eventStartTime;
        long elapsed = currentTime - eventStartTime;
        
        float progress = (float) elapsed / totalDuration;
        
        // Ограничить прогресс от 0 до 1
        progress = Math.max(0.0f, Math.min(1.0f, progress));

        // Обновить прогресс босс-бара
        bossBar.progress(progress);
    }

    private void removeBossBar() {
        if (bossBar != null && targetPlayer != null && targetPlayer.isOnline()) {
            targetPlayer.hideBossBar(bossBar);
        }
        bossBar = null;
    }

    public boolean isEventActive() {
        return eventActive;
    }

    public Player getTargetPlayer() {
        return targetPlayer;
    }
}
