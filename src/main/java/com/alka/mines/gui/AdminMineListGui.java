package com.alka.mines.gui;

import com.alka.mines.config.MenuConfig;
import com.alka.mines.hook.ItemsAdderHook;
import com.alka.mines.manager.MineManager;
import com.alka.mines.model.Mine;
import com.alkacode.core.gui.BaseGui;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Lista de minas pra escolher qual editar (/alkamines editar sem id) - clique abre o AdminMainMenu daquela mina. */
public class AdminMineListGui extends BaseGui {

    private static final int[] CONTENT_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private final MineManager mineManager;
    private final AdminMainMenu adminMainMenu;
    private int page;

    public AdminMineListGui(JavaPlugin plugin, Player admin, MineManager mineManager, AdminMainMenu adminMainMenu) {
        super(plugin, admin, MenuConfig.getInstance().title("titles.admin-list", Map.of()), 4, "alkamines-admin-list");
        this.mineManager = mineManager;
        this.adminMainMenu = adminMainMenu;
    }

    @Override
    public void render() {
        fillBorder(new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE));

        List<Mine> mines = new ArrayList<>(mineManager.getMines());
        MenuConfig cfg = MenuConfig.getInstance();
        int perPage = CONTENT_SLOTS.length;

        for (int i = 0; i < perPage; i++) {
            int index = page * perPage + i;
            if (index >= mines.size()) {
                break;
            }
            Mine mine = mines.get(index);
            ItemStack icon = resolveIcon(mine);
            var meta = icon.getItemMeta();
            Map<String, String> ph = Map.of(
                    "mine", mine.getDisplayName(),
                    "category", mine.getCategory(),
                    "blocks", String.valueOf(mine.getBlocksRemaining()));
            meta.displayName(com.alka.mines.util.ChatUtil.parse(cfg.name("items.admin-list.mine", ph)));
            meta.lore(cfg.lore("items.admin-list.mine", ph));
            icon.setItemMeta(meta);

            String mineId = mine.getId();
            setItem(CONTENT_SLOTS[i], icon, event -> adminMainMenu.open(player, mineId));
        }

        boolean hasPrevious = page > 0;
        boolean hasNext = mines.size() > (page + 1) * perPage;
        setItem(28, cfg.item("items.admin-list.nav-previous", Map.of("page", String.valueOf(page))),
                event -> {
                    if (hasPrevious) {
                        page--;
                        refresh();
                    }
                });
        setItem(34, cfg.item("items.admin-list.nav-next", Map.of("page", String.valueOf(page + 2))),
                event -> {
                    if (hasNext) {
                        page++;
                        refresh();
                    }
                });
    }

    /** Icone custom do ItemsAdder tem prioridade; cai pro Material (icon manual ou pedra) senao. */
    private ItemStack resolveIcon(Mine mine) {
        if (mine.getIconItemsAdder() != null && ItemsAdderHook.isEnabled()) {
            ItemStack custom = ItemsAdderHook.getCustomItem(mine.getIconItemsAdder());
            if (custom != null) {
                return custom;
            }
        }
        Material fallback = mine.getIcon() != null ? mine.getIcon() : Material.STONE;
        return new ItemStack(fallback);
    }
}
