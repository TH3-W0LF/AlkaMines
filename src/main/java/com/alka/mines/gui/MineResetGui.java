package com.alka.mines.gui;

import com.alka.mines.config.MenuConfig;
import com.alka.mines.hologram.HologramManager;
import com.alka.mines.manager.MineManager;
import com.alka.mines.model.Mine;
import com.alka.mines.model.MineSettings;
import com.alka.mines.util.ChatUtil;
import com.alkacode.core.gui.BaseGui;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

import java.util.ArrayList;
import java.util.List;

/** Tela de configuracao de reset/actionbar/broadcast/comandos de uma mina (BaseGui do
 * AlkaCore) - o prompt de chat do valor numerico continua no servico {@link MineResetMenu}. */
public class MineResetGui extends BaseGui {

    private final MineManager mineManager;
    private final MineResetMenu menu;
    private final AdminMainMenu adminMainMenu;
    private final String mineId;

    public MineResetGui(JavaPlugin plugin, Player admin, MineManager mineManager, HologramManager hologramManager,
                        MineResetMenu menu, AdminMainMenu adminMainMenu, String mineId) {
        super(plugin, admin, title(plugin, mineManager, mineId), 3, "alkamines-reset");
        this.mineManager = mineManager;
        this.menu = menu;
        this.adminMainMenu = adminMainMenu;
        this.mineId = mineId;
    }

    private static String title(JavaPlugin plugin, MineManager mineManager, String mineId) {
        Mine mine = mineManager.getMine(mineId).orElse(null);
        return MenuConfig.getInstance().title("titles.reset",
                Map.of("mine", mine != null ? mine.getDisplayName() : mineId));
    }

    @Override
    public void render() {
        Mine mine = mineManager.getMine(mineId).orElse(null);
        if (mine == null) {
            ChatUtil.send(player, "<red>Mina nao encontrada: " + mineId);
            player.closeInventory();
            return;
        }
        MineSettings settings = mine.getSettings();
        MenuConfig cfg = MenuConfig.getInstance();

        fillBorder(new ItemStack(Material.BLACK_STAINED_GLASS_PANE));

        setItem(10, cfg.item("items.reset.interval", Map.of("minutes",
                String.valueOf(settings.getResetIntervalMinutes()))),
                event -> menu.promptInterval(player, mineId));
        setItem(11, cfg.item("items.reset.percentage", Map.of("percentage",
                trim(settings.getResetPercentage()))),
                event -> menu.promptPercentage(player, mineId));
        setItem(12, cfg.item("items.reset.permission", Map.of("permission",
                settings.hasPermission() ? settings.getPermission() : "Nenhuma (publica)")),
                event -> menu.promptPermission(player, mineId));
        setItem(13, cfg.item("items.reset.actionbar", Map.of("state",
                settings.isActionbarEnabled() ? "LIGADO" : "desligado")),
                event -> {
                    settings.setActionbarEnabled(!settings.isActionbarEnabled());
                    mineManager.save();
                    refresh();
                });
        setItem(14, cfg.item("items.reset.actionbar-range", Map.of("range",
                String.valueOf(settings.getActionbarRange()))),
                event -> menu.promptActionbarRange(player, mineId));
        setItem(15, cfg.item("items.reset.broadcast", Map.of("mode",
                settings.getBroadcastMode() + " (" + broadcastLabel(settings.getBroadcastMode()) + ")")),
                event -> menu.promptBroadcast(player, mineId));
        setItem(16, cfg.item("items.reset.commands", Map.of("count",
                String.valueOf(settings.getResetCommands().size()))),
                event -> menu.promptResetCommand(player, mineId));
        setItem(22, cfg.item("items.back", Map.of()),
                event -> {
                    player.closeInventory();
                    if (adminMainMenu != null) {
                        adminMainMenu.open(player, mineId);
                    }
                });
    }

    private static String broadcastLabel(int mode) {
        return switch (mode) {
            case 0 -> "mundo";
            case -1 -> "todos os mundos";
            case -2 -> "silencioso";
            default -> "raio " + mode;
        };
    }

    private static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format("%.1f", value);
    }
}
