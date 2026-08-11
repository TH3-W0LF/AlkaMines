package com.alka.mines.gui;

import com.alka.mines.manager.MineManager;
import com.alka.mines.manager.PlayerDataManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Facade do menu de minas do jogador - a renderizacao vive em {@link MineListGui} (BaseGui). */
public class MineListMenu {

    private final JavaPlugin plugin;
    private final MineManager mineManager;
    private final PlayerDataManager playerDataManager;

    public MineListMenu(JavaPlugin plugin, MineManager mineManager, PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.mineManager = mineManager;
        this.playerDataManager = playerDataManager;
    }

    public void open(Player player) {
        MineListGui.open(plugin, player, mineManager, playerDataManager);
    }

    public void openMines(Player player, String category) {
        new MineListGui(plugin, player, mineManager, playerDataManager, category).open();
    }
}
