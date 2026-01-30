package com.killer;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class KillerEventCommand implements CommandExecutor {

    private final KillerEventPlugin plugin;

    public KillerEventCommand(KillerEventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Проверить есть ли аргументы
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "start":
                return handleStart(sender);
            case "stop":
                return handleStop(sender);
            case "status":
                return handleStatus(sender);
            case "help":
                sendHelp(sender);
                return true;
            default:
                sender.sendMessage("§c§lОшибка: неизвестная команда!");
                sendHelp(sender);
                return true;
        }
    }

    private boolean handleStart(CommandSender sender) {
        // Проверить прав доступа
        if (!sender.hasPermission("killerevent.start")) {
            sender.sendMessage("§c§lОшибка: у вас нет прав для этой команды!");
            return true;
        }

        EventManager eventManager = plugin.getEventManager();

        // Проверить если событие уже идёт
        if (eventManager.isEventActive()) {
            sender.sendMessage("§c§lОшибка: событие уже активно!");
            return true;
        }

        // Запустить событие вручную
        eventManager.startEvent();
        sender.sendMessage("§6§lСобытие KillerEvent успешно запущено!");
        return true;
    }

    private boolean handleStop(CommandSender sender) {
        // Проверить прав доступа
        if (!sender.hasPermission("killerevent.stop")) {
            sender.sendMessage("§c§lОшибка: у вас нет прав для этой команды!");
            return true;
        }

        EventManager eventManager = plugin.getEventManager();

        // Проверить если событие не идёт
        if (!eventManager.isEventActive()) {
            sender.sendMessage("§c§lОшибка: событие не активно!");
            return true;
        }

        // Остановить событие
        eventManager.stopEvent();
        sender.sendMessage("§6§lСобытие KillerEvent успешно остановлено!");
        return true;
    }

    private boolean handleStatus(CommandSender sender) {
        EventManager eventManager = plugin.getEventManager();

        if (eventManager.isEventActive()) {
            Player targetPlayer = eventManager.getTargetPlayer();
            if (targetPlayer != null) {
                sender.sendMessage("§6§l=== КИлер Событие ===");
                sender.sendMessage("§eСтатус: §a§lАКТИВНО");
                sender.sendMessage("§eЦель: §c" + targetPlayer.getName());
                sender.sendMessage("§eКоординаты: §c" + targetPlayer.getLocation().getBlockX() + ", " +
                        targetPlayer.getLocation().getBlockY() + ", " +
                        targetPlayer.getLocation().getBlockZ());
                sender.sendMessage("§eМир: §c" + targetPlayer.getWorld().getName());
            }
        } else {
            sender.sendMessage("§6§l=== КИлер Событие ===");
            sender.sendMessage("§eСтатус: §c§lНЕАКТИВНО");
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l=== KillerEvent Команды ===");
        sender.sendMessage("§e/killev start §6- Запустить событие вручную");
        sender.sendMessage("§e/killev stop §6- Остановить текущее событие");
        sender.sendMessage("§e/killev status §6- Показать статус события");
        sender.sendMessage("§e/killev help §6- Показать эту справку");
    }
}
