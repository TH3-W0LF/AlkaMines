package com.alka.mines.hook;

import com.alka.mines.manager.MineManager;
import com.alka.mines.manager.PlayerDataManager;
import com.alka.mines.manager.PlayerMineData;
import com.alka.mines.model.Mine;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * %alkaminas_mina%, %alkaminas_blocos%, %alkaminas_blocos_raw%, %alkaminas_nivel%, %alkaminas_bonus%.
 */
public class PlaceholderHook extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final MineManager mineManager;
    private final PlayerDataManager playerDataManager;

    public PlaceholderHook(JavaPlugin plugin, MineManager mineManager, PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.mineManager = mineManager;
        this.playerDataManager = playerDataManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "alkaminas";
    }

    @Override
    public @NotNull String getAuthor() {
        return "MestreDEV";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        PlayerMineData data = playerDataManager.get(player.getUniqueId());

        return switch (params.toLowerCase()) {
            case "mina" -> mineManager.getMineAt(player.getLocation()).map(Mine::getDisplayName).orElse("Nenhuma");
            case "blocos" -> String.format(Locale.US, "%,d", data.getBlocksBroken());
            case "blocos_raw" -> String.valueOf(data.getBlocksBroken());
            case "nivel" -> String.valueOf(data.getPickaxeLevel());
            case "bonus" -> String.valueOf(data.getCoinBonus());
            default -> "";
        };
    }
}
