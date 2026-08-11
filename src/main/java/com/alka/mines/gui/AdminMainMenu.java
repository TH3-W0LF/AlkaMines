package com.alka.mines.gui;

import com.alka.mines.hologram.HologramManager;
import com.alka.mines.manager.MineManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Facade do menu administrativo de uma mina - a renderizacao vive em
 * {@link AdminMainMenuGui} (BaseGui do AlkaCore). Segue a mesma convencao dos outros
 * menus com chat (MineResetMenu/BlockCompositionMenu): servico de longa vida, GUI
 * por abertura.
 */
public class AdminMainMenu {

    private final JavaPlugin plugin;
    private final MineManager mineManager;
    private final HologramManager hologramManager;
    private BlockCompositionMenu blockCompositionMenu;
    private MineResetMenu mineResetMenu;
    private MineRewardsMenu mineRewardsMenu;

    public AdminMainMenu(JavaPlugin plugin, MineManager mineManager, HologramManager hologramManager) {
        this.plugin = plugin;
        this.mineManager = mineManager;
        this.hologramManager = hologramManager;
    }

    /** Setter em vez de construtor: os sub-menus se referenciam mutuamente com este menu. */
    public void setBlockCompositionMenu(BlockCompositionMenu blockCompositionMenu) {
        this.blockCompositionMenu = blockCompositionMenu;
    }

    public void setMineResetMenu(MineResetMenu mineResetMenu) {
        this.mineResetMenu = mineResetMenu;
    }

    public void setMineRewardsMenu(MineRewardsMenu mineRewardsMenu) {
        this.mineRewardsMenu = mineRewardsMenu;
    }

    public void open(Player admin, String mineId) {
        new AdminMainMenuGui(plugin, admin, mineManager, hologramManager,
                blockCompositionMenu, mineResetMenu, mineRewardsMenu, mineId).open();
    }
}
