package com.alka.mines.gui;

import com.alka.mines.hologram.HologramManager;
import com.alka.mines.manager.MineManager;
import com.alka.mines.model.Mine;
import com.alka.mines.model.MineSettings;
import com.alka.mines.util.ChatUtil;
import com.alkacode.core.gui.BaseGui;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

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
        return "<dark_gray>Reset: " + (mine != null ? mine.getDisplayName() : mineId);
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

        fillBorder(new ItemStack(Material.BLACK_STAINED_GLASS_PANE));

        setItem(10, icon(Material.CLOCK, "<gold><bold>Tempo de Reset",
                "<gray>Atual: <white>" + settings.getResetIntervalMinutes() + " min",
                "", "<yellow>Clique para alterar"),
                event -> menu.promptInterval(player, mineId));
        setItem(11, icon(Material.COMPARATOR, "<aqua><bold>Porcentagem Restante",
                "<gray>Atual: <white>" + trim(settings.getResetPercentage()) + "%",
                "", "<yellow>Clique para alterar"),
                event -> menu.promptPercentage(player, mineId));
        setItem(12, icon(Material.TRIPWIRE_HOOK, "<light_purple><bold>Permissao de Entrada",
                "<gray>Atual: <white>" + (settings.hasPermission() ? settings.getPermission() : "Nenhuma (publica)"),
                "", "<yellow>Clique para alterar"),
                event -> menu.promptPermission(player, mineId));
        setItem(13, icon(settings.isActionbarEnabled() ? Material.STONE_BUTTON : Material.STICK,
                "<aqua><bold>ActionBar: " + (settings.isActionbarEnabled() ? "LIGADO" : "desligado"),
                "<gray>Mostra restantes/% pra quem esta perto.", "", "<yellow>Clique para alternar"),
                event -> {
                    settings.setActionbarEnabled(!settings.isActionbarEnabled());
                    mineManager.save();
                    refresh();
                });
        setItem(14, icon(Material.LEAD, "<aqua><bold>Raio do ActionBar",
                "<gray>Atual: <white>" + settings.getActionbarRange() + " blocos",
                "", "<yellow>Clique para alterar"),
                event -> menu.promptActionbarRange(player, mineId));
        setItem(15, icon(Material.OAK_BUTTON, "<gold><bold>Broadcast do Reset",
                "<gray>Atual: <white>" + settings.getBroadcastMode() + " "
                        + "(" + broadcastLabel(settings.getBroadcastMode()) + ")",
                "", "<yellow>Clique para alterar"),
                event -> menu.promptBroadcast(player, mineId));
        setItem(16, icon(Material.COMMAND_BLOCK, "<green><bold>Comandos de Reset",
                "<gray>Atual: <white>" + settings.getResetCommands().size() + " comando(s)",
                "<gray>Placeholders: <white>%mine% %display%", "", "<yellow>Clique para gerenciar"),
                event -> menu.promptResetCommand(player, mineId));
        setItem(22, icon(Material.ARROW, "<red><bold>Voltar", "<gray>Clique para voltar"),
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

    private static ItemStack icon(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(ChatUtil.parse(name));
        List<Component> loreList = new ArrayList<>();
        for (String line : lore) {
            loreList.add(ChatUtil.parse(line));
        }
        meta.lore(loreList);
        item.setItemMeta(meta);
        return item;
    }

    private static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format("%.1f", value);
    }
}
