package com.alka.mines;

import com.alka.mines.command.AdminCommands;
import com.alka.mines.command.PlayerCommands;
import com.alka.mines.gui.AdminMainMenu;
import com.alka.mines.gui.BlockCompositionChatListener;
import com.alka.mines.gui.BlockCompositionMenu;
import com.alka.mines.gui.BlockCompositionMenuListener;
import com.alka.mines.gui.MenuBuilder;
import com.alka.mines.gui.MineResetChatListener;
import com.alka.mines.gui.MineResetMenu;
import com.alka.mines.hologram.HologramManager;
import com.alka.mines.hook.AlkaEconomyHook;
import com.alka.mines.hook.AlkaShopHook;
import com.alka.mines.hook.PlaceholderHook;
import com.alka.mines.hook.WorldEditHook;
import com.alka.mines.listener.MineBreakListener;
import com.alka.mines.listener.PlayerMineTrackerListener;
import com.alka.mines.manager.MineManager;
import com.alka.mines.manager.PlayerDataManager;
import com.alka.mines.service.MineResetService;
import com.alka.mines.task.MineResetTask;
import org.bukkit.plugin.java.JavaPlugin;

public final class AlkaMines extends JavaPlugin {

    private MineManager mineManager;

    @Override
    public void onEnable() {
        mineManager = new MineManager(this);
        mineManager.load();

        PlayerDataManager playerDataManager = new PlayerDataManager();
        HologramManager hologramManager = new HologramManager(this);
        WorldEditHook worldEditHook = new WorldEditHook();
        MineResetService resetService = new MineResetService(this);

        AdminMainMenu adminMainMenu = new AdminMainMenu(mineManager, hologramManager);
        BlockCompositionMenu compositionMenu = new BlockCompositionMenu(this, mineManager);
        MineResetMenu resetMenu = new MineResetMenu(this, mineManager);
        adminMainMenu.setBlockCompositionMenu(compositionMenu);
        adminMainMenu.setMineResetMenu(resetMenu);
        compositionMenu.setAdminMainMenu(adminMainMenu);
        resetMenu.setAdminMainMenu(adminMainMenu);

        getServer().getPluginManager().registerEvents(new MenuBuilder.MenuListener(), this);
        getServer().getPluginManager().registerEvents(new BlockCompositionMenuListener(compositionMenu), this);
        getServer().getPluginManager().registerEvents(new BlockCompositionChatListener(this, compositionMenu), this);
        getServer().getPluginManager().registerEvents(new MineResetChatListener(this, resetMenu), this);
        getServer().getPluginManager().registerEvents(new PlayerMineTrackerListener(mineManager, playerDataManager), this);

        var economyHook = AlkaEconomyHook.tryHook(this);
        var shopHook = AlkaShopHook.tryHook(this);
        getServer().getPluginManager().registerEvents(new MineBreakListener(mineManager, playerDataManager, economyHook, shopHook), this);

        AdminCommands adminCommands = new AdminCommands(mineManager, worldEditHook, adminMainMenu, resetService, playerDataManager);
        getCommand("minaadmin").setExecutor(adminCommands);
        getCommand("minaadmin").setTabCompleter(adminCommands);

        PlayerCommands playerCommands = new PlayerCommands(mineManager, playerDataManager);
        getCommand("mina").setExecutor(playerCommands);
        getCommand("mina").setTabCompleter(playerCommands);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaceholderHook(this, mineManager, playerDataManager).register();
            getLogger().info("Hook do PlaceholderAPI registrado.");
        }

        MineResetTask resetTask = new MineResetTask(mineManager, resetService);
        getServer().getScheduler().runTaskTimer(this, resetTask, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, () -> hologramManager.updateAll(mineManager), 20L, 20L);

        getLogger().info("AlkaMines habilitado com " + mineManager.getMines().size() + " mina(s).");
    }

    @Override
    public void onDisable() {
        if (mineManager != null) {
            mineManager.save();
        }
    }

    /**
     * Exposto para integracoes soft de outros plugins Alka* (ex: AlkaDrop, que
     * detecta se um bloco esta dentro de uma mina registrada via
     * {@link MineManager#getMineAt}) - AlkaMines nao depende de ninguem pra isso,
     * so publica sua propria API pro consumidor puxar via mavenLocal.
     */
    public MineManager getMineManager() {
        return mineManager;
    }
}
