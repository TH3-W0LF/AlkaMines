package com.alka.mines.gui;

import com.alka.mines.manager.MineManager;
import com.alka.mines.manager.PlayerDataManager;
import com.alka.mines.model.Mine;
import com.alka.mines.util.ChatUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.List;
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
        MenuBuilder builder = new MenuBuilder(SIZE, ChatUtil.parse("<dark_gray>Categorias de Minas"));

        int slot = 0;
        for (String category : categories) {
            if (slot >= SIZE) {
                break;
            }

            long count = mineManager.getMines().stream().filter(m -> m.getCategory().equals(category)).count();

            builder.item(slot++, Material.CHEST, ChatUtil.parse("<gold>" + capitalize(category)),
                    List.of(
                            ChatUtil.parse("<gray>" + count + " mina(s)"),
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
        MenuBuilder builder = new MenuBuilder(SIZE, ChatUtil.parse(title));

        int slot = 0;
        for (Mine mine : mineManager.getMines()) {
            if (category != null && !mine.getCategory().equals(category)) {
                continue;
            }
            if (slot >= SIZE) {
                break;
            }

            boolean access = canAccess(player, mine);
            Material icon = mine.getIcon() != null ? mine.getIcon() : (access ? Material.LIME_WOOL : Material.RED_WOOL);
            Component name = access
                    ? ChatUtil.parse("<green>" + mine.getDisplayName())
                    : ChatUtil.parse("<red>" + mine.getDisplayName());

            List<Component> lore = List.of(
                    ChatUtil.parse("<gray>Nivel necessario: <white>" + mine.getSettings().getMinPickaxeLevel()),
                    ChatUtil.parse("<gray>Blocos compostos: <white>" + mine.getComposition().size() + " tipo(s)"),
                    ChatUtil.parse(access ? "<green>Clique para entrar" : "<red>Nivel insuficiente")
            );

            Mine target = mine;
            builder.item(slot++, icon, name, lore, event -> {
                if (!access) {
                    ChatUtil.send(player, "<red>Voce precisa de nivel de picareta " + target.getSettings().getMinPickaxeLevel()
                            + " para entrar em '" + target.getId() + "'.");
                    return;
                }
                player.closeInventory();
                player.teleport(target.getSpawn());
                ChatUtil.send(player, "<green>Teleportado para a mina '" + target.getId() + "'.");
            });
        }

        player.openInventory(builder.build());
    }

    private boolean canAccess(Player player, Mine mine) {
        int required = mine.getSettings().getMinPickaxeLevel();
        if (required <= 0) {
            return true;
        }
        return playerDataManager.get(player.getUniqueId()).getPickaxeLevel() >= required;
    }

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
