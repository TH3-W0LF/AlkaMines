package com.alka.mines.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;

/**
 * Centraliza TODAS as mensagens de chat do AlkaMines no messages.yml (mesmo padrao do
 * MenuConfig pra GUIs) - tudo editavel sem recompilar. Formatacao MiniMessage (regra
 * [R5] do estudio: ZERO codigo & legado em mensagem de chat). Instancia unica
 * (MessagesConfig.getInstance()) setada no onEnable.
 */
public class MessagesConfig {

    private static MessagesConfig instance;
    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration config;
    private String prefix;

    public MessagesConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "messages.yml");
        reload();
    }

    public static void init(JavaPlugin plugin) {
        instance = new MessagesConfig(plugin);
    }

    public static MessagesConfig getInstance() {
        return instance;
    }

    public void reload() {
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        prefix = config.getString("prefix", "<dark_gray>[<gold>AlkaMines<dark_gray>] ");
    }

    public String getRaw(String key) {
        String value = config.getString(key);
        if (value == null) {
            plugin.getLogger().warning("Mensagem nao encontrada em messages.yml: " + key);
            return "<red>[Missing: " + key + "]";
        }
        return value;
    }

    public String get(String key, Map<String, String> placeholders) {
        String value = getRaw(key);
        value = value.replace("{prefix}", prefix);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                value = value.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return value;
    }

    public String get(String key) {
        return get(key, null);
    }

    public Component getComponent(String key, Map<String, String> placeholders) {
        return MiniMessage.miniMessage().deserialize(get(key, placeholders));
    }

    public Component getComponent(String key) {
        return getComponent(key, null);
    }
}
