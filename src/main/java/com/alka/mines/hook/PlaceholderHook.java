package com.alka.mines.hook;

import com.alka.mines.manager.MineManager;
import com.alka.mines.manager.PickaxeLevelManager;
import com.alka.mines.manager.PlayerDataManager;
import com.alka.mines.manager.PlayerMineData;
import com.alka.mines.model.Mine;
import com.alka.mines.util.ChatUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * %alkaminas_mina%, %alkaminas_blocos%, %alkaminas_blocos_raw%, %alkaminas_nivel%,
 * %alkaminas_bonus%, %alkaminas_proximo_nivel%, %alkaminas_blocos_proximo%.
 */
public class PlaceholderHook extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final MineManager mineManager;
    private final PlayerDataManager playerDataManager;
    private final PickaxeLevelManager levelManager;

    public PlaceholderHook(JavaPlugin plugin, MineManager mineManager, PlayerDataManager playerDataManager,
                            PickaxeLevelManager levelManager) {
        this.plugin = plugin;
        this.mineManager = mineManager;
        this.playerDataManager = playerDataManager;
        this.levelManager = levelManager;
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
            case "mina" -> {
                String currentId = data.getCurrentMineId();
                Mine mine = currentId == null ? null : mineManager.getMine(currentId).orElse(null);
                // displayName e MiniMessage (editavel via /alkamines renomear) - scoreboard/TAB
                // normalmente so entendem codigos & legados, entao converte antes de expor.
                yield mine == null ? "Nenhuma" : ChatUtil.toLegacy(mine.getDisplayName());
            }
            case "blocos" -> String.format(Locale.US, "%,d", data.getBlocksBroken());
            case "blocos_raw" -> String.valueOf(data.getBlocksBroken());
            case "nivel" -> String.valueOf(data.getPickaxeLevel());
            case "bonus" -> String.valueOf(data.getCoinBonus());
            case "proximo_nivel" -> {
                long next = levelManager.getBlocksForNextLevel(data.getPickaxeLevel());
                yield next > 0 ? String.format(Locale.US, "%,d", next) : "MAX";
            }
            case "blocos_proximo" -> {
                long next = levelManager.getBlocksForNextLevel(data.getPickaxeLevel());
                yield next > 0 ? String.format(Locale.US, "%,d", Math.max(0, next - data.getBlocksBroken())) : "0";
            }
            default -> "";
        };
    }
}
