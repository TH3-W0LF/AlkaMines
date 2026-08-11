package com.alka.mines.hook;

import com.alka.mines.manager.MineManager;
import com.alka.mines.manager.PickaxeLevelManager;
import com.alka.mines.manager.PlayerDataManager;
import com.alka.mines.manager.PlayerMineData;
import com.alka.mines.manager.PrivateMineManager;
import com.alka.mines.model.Mine;
import com.alka.mines.model.PrivateMine;
import com.alka.mines.util.ChatUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;

/**
 * Placeholders do AlkaMines (identificador %alkaminas_...%).
 *
 * Do jogador: %alkaminas_mina%, %alkaminas_blocos%, %alkaminas_blocos_raw%,
 * %alkaminas_nivel%, %alkaminas_bonus%, %alkaminas_proximo_nivel%, %alkaminas_blocos_proximo%.
 *
 * Da mina atual do jogador (publica OU particular): %alkaminas_minas% (total),
 * %alkaminas_blocos_restantes%, %alkaminas_blocos_restantes_raw%,
 * %alkaminas_porcentagem_restante%, %alkaminas_progresso%, %alkaminas_reset_tempo% (mm:ss).
 */
public class PlaceholderHook extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final MineManager mineManager;
    private final PlayerDataManager playerDataManager;
    private final PickaxeLevelManager levelManager;
    private final PrivateMineManager privateMineManager;

    public PlaceholderHook(JavaPlugin plugin, MineManager mineManager, PlayerDataManager playerDataManager,
                            PickaxeLevelManager levelManager, PrivateMineManager privateMineManager) {
        this.plugin = plugin;
        this.mineManager = mineManager;
        this.playerDataManager = playerDataManager;
        this.levelManager = levelManager;
        this.privateMineManager = privateMineManager;
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
                if ("privado".equals(data.getCurrentMineId())) {
                    yield "Mina Particular";
                }
                Optional<Mine> mine = currentMine(data);
                // displayName e MiniMessage (editavel via /alkamines renomear) - scoreboard/TAB
                // normalmente so entendem codigos & legados, entao converte antes de expor.
                yield mine.map(m -> ChatUtil.toLegacy(m.getDisplayName())).orElse("Nenhuma");
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
            case "minas" -> String.valueOf(mineManager.getMines().size());
            case "blocos_restantes", "blocos_restantes_raw", "porcentagem_restante", "progresso", "reset_tempo" -> {
                if ("privado".equals(data.getCurrentMineId())) {
                    yield privateMineValue(params, player);
                }
                Optional<Mine> mine = currentMine(data);
                if (mine.isEmpty()) {
                    yield "";
                }
                yield switch (params.toLowerCase()) {
                    case "blocos_restantes" -> String.format(Locale.US, "%,d", mine.get().getBlocksRemaining());
                    case "blocos_restantes_raw" -> String.valueOf(mine.get().getBlocksRemaining());
                    case "porcentagem_restante" -> String.valueOf(remainingPercentage(mine.get()));
                    case "progresso" -> String.valueOf(100.0 - remainingPercentage(mine.get()));
                    default -> resetTime(mine.get());
                };
            }
            default -> "";
        };
    }

    private String privateMineValue(String params, Player player) {
        Optional<PrivateMine> mine = privateMineManager.getMineAt(player.getLocation());
        if (mine.isEmpty()) {
            return "";
        }
        return switch (params.toLowerCase()) {
            case "blocos_restantes" -> String.format(Locale.US, "%,d", mine.get().getBlocksRemaining());
            case "blocos_restantes_raw" -> String.valueOf(mine.get().getBlocksRemaining());
            case "porcentagem_restante" -> {
                long volume = mine.get().volume();
                yield volume > 0 ? String.valueOf(Math.round((mine.get().getBlocksRemaining() / (double) volume) * 1000.0) / 10.0) : "0.0";
            }
            case "progresso" -> {
                long volume = mine.get().volume();
                yield volume > 0 ? String.valueOf(100.0 - Math.round((mine.get().getBlocksRemaining() / (double) volume) * 1000.0) / 10.0) : "100.0";
            }
            default -> privateResetTime(mine.get());
        };
    }

    private String privateResetTime(PrivateMine mine) {
        int intervalMinutes = privateMineManager.getTemplate(mine.getTemplateId())
                .map(t -> t.getResetIntervalMinutes())
                .orElse(0);
        if (intervalMinutes <= 0) {
            return "Manual";
        }
        long remainingMs = Math.max(0, intervalMinutes * 60_000L - (System.currentTimeMillis() - mine.getLastReset()));
        long remainingSec = remainingMs / 1000;
        return String.format("%02d:%02d", remainingSec / 60, remainingSec % 60);
    }

    private Optional<Mine> currentMine(PlayerMineData data) {
        String currentId = data.getCurrentMineId();
        if (currentId == null) {
            return Optional.empty();
        }
        return mineManager.getMine(currentId);
    }

    private double remainingPercentage(Mine mine) {
        long volume = mine.getRegion().getVolume();
        if (volume <= 0) {
            return 0.0;
        }
        return Math.round((mine.getBlocksRemaining() / (double) volume) * 1000.0) / 10.0;
    }

    private String resetTime(Mine mine) {
        int intervalMinutes = mine.getSettings().getResetIntervalMinutes();
        if (intervalMinutes <= 0) {
            return "Manual";
        }
        long elapsedMs = System.currentTimeMillis() - mine.getLastReset();
        long remainingMs = Math.max(0, intervalMinutes * 60_000L - elapsedMs);
        long remainingSec = remainingMs / 1000;
        return String.format("%02d:%02d", remainingSec / 60, remainingSec % 60);
    }
}
