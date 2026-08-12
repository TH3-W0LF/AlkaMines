package com.alka.mines.gui;

import com.alka.mines.config.MenuConfig;
import com.alka.mines.hook.ItemsAdderHook;
import com.alka.mines.manager.MineManager;
import com.alka.mines.manager.PlayerDataManager;
import com.alka.mines.model.Mine;
import com.alka.mines.util.ChatUtil;
import com.alkacode.core.gui.BaseGui;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Menu de minas do jogador (BaseGui do AlkaCore), em duas camadas: categorias primeiro
 * (se houver mais de uma), depois as minas daquela categoria. Categoria e um campo
 * livre por mina (Mine#getCategory, default "geral").
 */
public class MineListGui extends BaseGui {

    private final MineManager mineManager;
    private final PlayerDataManager playerDataManager;
    private final String category; // null = tela de categorias

    public MineListGui(JavaPlugin plugin, Player player, MineManager mineManager,
                       PlayerDataManager playerDataManager, String category) {
        super(plugin, player, title(category), 3, "alkamines-list");
        this.mineManager = mineManager;
        this.playerDataManager = playerDataManager;
        this.category = category;
    }

    /** Abre a lista completa: categorias se houver mais de uma, senao direto as minas. */
    public static void open(JavaPlugin plugin, Player player, MineManager mineManager,
                            PlayerDataManager playerDataManager) {
        Set<String> categories = categoriesOf(mineManager);
        if (categories.size() <= 1) {
            new MineListGui(plugin, player, mineManager, playerDataManager,
                    categories.isEmpty() ? null : categories.iterator().next()).open();
        } else {
            new MineListGui(plugin, player, mineManager, playerDataManager, null).open();
        }
    }

    private static Set<String> categoriesOf(MineManager mineManager) {
        Set<String> categories = new LinkedHashSet<>();
        for (Mine mine : mineManager.getMines()) {
            categories.add(mine.getCategory());
        }
        return categories;
    }

    private static String title(String category) {
        MenuConfig cfg = MenuConfig.getInstance();
        return category != null
                ? cfg.title("titles.mine-list", Map.of("category", capitalize(category)))
                : cfg.title("titles.mine-list-categories", Map.of());
    }

    @Override
    public void render() {
        fillBorder(new ItemStack(Material.BLACK_STAINED_GLASS_PANE));
        if (category != null) {
            renderMines(category);
        } else {
            renderCategories();
        }
    }

    private void renderCategories() {
        Set<String> categories = categoriesOf(mineManager);
        if (categories.size() <= 1) {
            renderMines(categories.isEmpty() ? null : categories.iterator().next());
            return;
        }

        MenuConfig cfg = MenuConfig.getInstance();
        int slot = 10;
        for (String cat : categories) {
            if (slot >= 17) {
                break;
            }
            long count = mineManager.getMines().stream().filter(m -> m.getCategory().equals(cat)).count();
            String target = cat;
            setItem(slot++, cfg.item("items.mine-list.category",
                    Map.of("category", capitalize(cat), "count", String.valueOf(count))),
                    event -> new MineListGui(plugin, player, mineManager, playerDataManager, target).open());
        }
    }

    private void renderMines(String targetCategory) {
        MenuConfig cfg = MenuConfig.getInstance();
        int slot = 10;
        for (Mine mine : mineManager.getMines()) {
            if (targetCategory != null && !mine.getCategory().equals(targetCategory)) {
                continue;
            }
            if (slot >= 17) {
                break;
            }

            boolean access = canAccess(player, mine);
            ItemStack icon = resolveIcon(mine, access);
            ItemMeta iconMeta = icon.getItemMeta();
            Map<String, String> ph = Map.of(
                    "mine", mine.getDisplayName(),
                    "level", String.valueOf(mine.getSettings().getMinPickaxeLevel()),
                    "composition", String.valueOf(mine.getComposition().size()),
                    "blocks", String.format(Locale.US, "%,d", mine.getBlocksRemaining()),
                    "deny", access ? "" : denyReason(player, mine));
            iconMeta.displayName(ChatUtil.parse(cfg.name(access ? "items.mine-list.mine" : "items.mine-list.mine-denied", ph)));
            iconMeta.lore(cfg.lore(access ? "items.mine-list.mine" : "items.mine-list.mine-denied", ph));
            icon.setItemMeta(iconMeta);

            Mine target = mine;
            setItem(slot++, icon, event -> {
                if (!access) {
                    ChatUtil.send(player, denyMessage(player, target));
                    return;
                }
                player.closeInventory();
                player.teleport(target.getSpawn());
                playerDataManager.get(player.getUniqueId()).setCurrentMineId(target.getId());
                ChatUtil.send(player, "<green>Teleportado para a mina '" + target.getId() + "'.");
            });
        }

        if (targetCategory != null) {
            setItem(22, cfg.item("items.mine-list.back", Map.of()),
                    event -> open(plugin, player, mineManager, playerDataManager));
        }
    }

    /** Icone custom do ItemsAdder tem prioridade; cai pro Material (icon manual ou
     * la verde/vermelha padrao) se nao houver icone custom ou ele sumir do registro. */
    private ItemStack resolveIcon(Mine mine, boolean access) {
        if (mine.getIconItemsAdder() != null && ItemsAdderHook.isEnabled()) {
            ItemStack custom = ItemsAdderHook.getCustomItem(mine.getIconItemsAdder());
            if (custom != null) {
                return custom;
            }
        }
        Material fallback = mine.getIcon() != null ? mine.getIcon() : (access ? Material.LIME_WOOL : Material.RED_WOOL);
        return new ItemStack(fallback);
    }

    private boolean canAccess(Player player, Mine mine) {
        if (mine.getSettings().hasPermission() && !player.hasPermission(mine.getSettings().getPermission())) {
            return false;
        }
        int required = mine.getSettings().getMinPickaxeLevel();
        if (required <= 0) {
            return true;
        }
        return playerDataManager.get(player.getUniqueId()).getPickaxeLevel() >= required;
    }

    private String denyReason(Player player, Mine mine) {
        if (mine.getSettings().hasPermission() && !player.hasPermission(mine.getSettings().getPermission())) {
            return "<red>Requer: <white>" + mine.getSettings().getPermission();
        }
        return "<red>Nivel insuficiente";
    }

    private String denyMessage(Player player, Mine mine) {
        if (mine.getSettings().hasPermission() && !player.hasPermission(mine.getSettings().getPermission())) {
            return "<red>Voce precisa da permissao <white>" + mine.getSettings().getPermission()
                    + "</white><red> para entrar em '" + mine.getId() + "'.";
        }
        return "<red>Voce precisa de nivel de picareta " + mine.getSettings().getMinPickaxeLevel()
                + " para entrar em '" + mine.getId() + "'.";
    }

    private static ItemStack buildIcon(Material material, String name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ChatUtil.parse(name));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
