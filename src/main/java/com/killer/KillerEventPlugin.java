package com.killer;

import org.bukkit.plugin.java.JavaPlugin;

public class KillerEventPlugin extends JavaPlugin {

    private EventManager eventManager;

    @Override
    public void onEnable() {
        // Сохранить конфиг по умолчанию
        saveDefaultConfig();

        // Инициализировать менеджер событий
        eventManager = new EventManager(this);
        eventManager.initialize();

        // Зарегистрировать слушателей
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(eventManager), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(eventManager), this);

        // Зарегистрировать команду
        getCommand("killev").setExecutor(new KillerEventCommand(this));

        getLogger().info("KillerEvent plugin enabled!");
    }

    @Override
    public void onDisable() {
        if (eventManager != null) {
            eventManager.stopEvent();
        }
        getLogger().info("KillerEvent plugin disabled!");
    }

    public EventManager getEventManager() {
        return eventManager;
    }
}
