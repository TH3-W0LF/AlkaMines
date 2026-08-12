package com.alka.mines.gui;

import com.alka.mines.config.MenuConfig;
import com.alka.mines.manager.PlayerDataManager;
import com.alka.mines.manager.PlayerMineData;
import com.alka.mines.util.ChatUtil;
import com.alkacode.core.gui.BaseGui;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Leaderboard de blocos minerados (BaseGui do AlkaCore), paginado - 7 posicoes por
 * pagina, cabecas dos jogadores, navegacao Anterior/Proxima + Fechar.
 */
public class RankingGui extends BaseGui {

    private static final int PER_PAGE = 7;

    private final PlayerDataManager playerDataManager;
    private int page;

    public RankingGui(JavaPlugin plugin, Player player, PlayerDataManager playerDataManager) {
        super(plugin, player, "<dark_gray>Ranking - Blocos Minerados", 6, "alkamines-ranking");
        this.playerDataManager = playerDataManager;
    }

    @Override
    public void render() {
        fillBorder(new ItemStack(Material.GRAY_STAINED_GLASS_PANE));

        List<Map.Entry<UUID, PlayerMineData>> top = playerDataManager.getTopBlocksBroken((page + 1) * PER_PAGE);

        int slot = 10;
        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i;
            if (index >= top.size()) {
                break;
            }
            Map.Entry<UUID, PlayerMineData> entry = top.get(index);
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            PlayerMineData data = entry.getValue();
            setItem(slot++, head(name != null ? name : "?", positionName(index + 1),
                    "Blocos minerados: <yellow>" + String.format(Locale.US, "%,d", data.getBlocksBroken()),
                    "Nivel de picareta: <white>" + data.getPickaxeLevel()));
        }

        boolean hasPrevious = page > 0;
        boolean hasNext = top.size() > (page + 1) * PER_PAGE;

        setItem(45, MenuConfig.getInstance().item("items.ranking.nav-previous", Map.of("page", String.valueOf(page))),
                event -> {
                    if (hasPrevious) {
                        page--;
                        refresh();
                    }
                });
        setItem(53, MenuConfig.getInstance().item("items.ranking.nav-next", Map.of("page", String.valueOf(page + 2))),
                event -> {
                    if (hasNext) {
                        page++;
                        refresh();
                    }
                });
        setItem(49, MenuConfig.getInstance().item("items.ranking.close", Map.of()),
                event -> player.closeInventory());
    }

    private static String positionName(int position) {
        return switch (position) {
            case 1 -> "<gold><bold>#1";
            case 2 -> "<gray><bold>#2";
            case 3 -> "<color:#cd7f32><bold>#3";
            default -> "<white>#" + position;
        };
    }

    private static ItemStack navItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(ChatUtil.parse(name));
        meta.lore(List.of(ChatUtil.parse(lore)));
        item.setItemMeta(meta);
        return item;
    }
}
