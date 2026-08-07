package com.alka.mines.gui;

import com.alka.mines.hook.ItemsAdderHook;
import com.alka.mines.manager.MineManager;
import com.alka.mines.manager.PlayerDataManager;
import com.alka.mines.model.Mine;
import com.alka.mines.util.ChatUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Menu de minas do jogador, em duas camadas: categorias primeiro (se houver mais de
 * uma), depois as minas daquela categoria. Categoria e um campo livre por mina
 * (Mine#getCategory, default "geral") - se so existir uma categoria no total, pula
 * direto pra lista de minas (sem forcar o jogador a clicar numa categoria unica).
 */
public class MineListMenu {

    private static final int SIZE = 27;

    private final MineManager mineManager;
    private final PlayerDataManager playerDataManager;

    public MineListMenu(MineManager mineManager, PlayerDataManager playerDataManager) {
        this.mineManager = mineManager;
        this.playerDataManager = playerDataManager;
    }

    public void open(Player player) {
        Set<String> categories = new LinkedHashSet<>();
        for (Mine mine : mineManager.getMines()) {
            categories.add(mine.getCategory());
        }

        if (categories.size() <= 1) {
            openMines(player, categories.isEmpty() ? null : categories.iterator().next());
            return;
        }

        openCategories(player, categories);
    }

    private void openCategories(Player player, Set<String> categories) {
        MenuBuilder builder = new MenuBuilder(SIZE, ChatUtil.parse("<dark_gray>Categorias de Minas"))
                .fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        int slot = 10;
        for (String category : categories) {
            if (slot >= 17) {
                break;
            }

            long count = mineManager.getMines().stream().filter(m -> m.getCategory().equals(category)).count();

            builder.item(slot++, Material.CHEST, ChatUtil.parse("<gold><bold>" + capitalize(category)),
                    List.of(
                            ChatUtil.parse("<gray>Minas disponiveis: <white>" + count),
                            ChatUtil.parse(""),
                            ChatUtil.parse("<yellow>Clique para ver")
                    ),
                    event -> {
                        player.closeInventory();
                        openMines(player, category);
                    });
        }

        player.openInventory(builder.build());
    }

    public void openMines(Player player, String category) {
        String title = category != null ? "<dark_gray>Minas: " + capitalize(category) : "<dark_gray>Minas Disponiveis";
        MenuBuilder builder = new MenuBuilder(SIZE, ChatUtil.parse(title))
                .fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        int slot = 10;
        for (Mine mine : mineManager.getMines()) {
            if (category != null && !mine.getCategory().equals(category)) {
                continue;
            }
            if (slot >= 17) {
                break;
            }

            boolean access = canAccess(player, mine);
            Component name = access
                    ? ChatUtil.parse("<green><bold>" + mine.getDisplayName())
                    : ChatUtil.parse("<red><bold>" + mine.getDisplayName());

            List<Component> lore = List.of(
                    ChatUtil.parse("<gray>Nivel necessario: <white>" + mine.getSettings().getMinPickaxeLevel()),
                    ChatUtil.parse("<gray>Blocos compostos: <white>" + mine.getComposition().size() + " tipo(s)"),
                    ChatUtil.parse("<gray>Restantes: <white>" + String.format(Locale.US, "%,d", mine.getBlocksRemaining())),
                    ChatUtil.parse(""),
                    access ? ChatUtil.parse("<green>Clique para entrar") : ChatUtil.parse(denyReason(player, mine))
            );

            ItemStack icon = resolveIcon(mine, access);
            ItemMeta iconMeta = icon.getItemMeta();
            iconMeta.displayName(name);
            iconMeta.lore(lore);
            icon.setItemMeta(iconMeta);

            Mine target = mine;
            builder.item(slot++, icon, event -> {
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

        if (category != null) {
            builder.backButton(SIZE - 5, event -> {
                player.closeInventory();
                open(player);
            });
        }

        player.openInventory(builder.build());
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

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
