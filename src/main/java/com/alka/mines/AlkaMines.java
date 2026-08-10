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
import com.alka.mines.hook.AdvancedEnchantmentsHook;
import com.alka.mines.hook.AlkaDropHook;
import com.alka.mines.hook.AlkaShopHook;
import com.alka.mines.hook.BossesProHook;
import com.alka.mines.hook.ItemsAdderHook;
import com.alka.mines.hook.McMMOHook;
import com.alka.mines.hook.PlaceholderHook;
import com.alka.mines.hook.WorldEditHook;
import com.alka.mines.listener.MineBreakListener;
import com.alka.mines.listener.MineCommandBlockerListener;
import com.alka.mines.listener.MineProtectionListener;
import com.alka.mines.listener.PlayerMineTrackerListener;
import com.alka.mines.manager.MineManager;
import com.alka.mines.manager.PickaxeLevelManager;
import com.alka.mines.manager.PlayerDataManager;
import com.alka.mines.service.MineResetService;
import com.alka.mines.task.MineResetTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class AlkaMines extends JavaPlugin {

    private MineManager mineManager;
    private PlayerDataManager playerDataManager;
    private HologramManager hologramManager;
    private PickaxeLevelManager levelManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        mineManager = new MineManager(this);
        mineManager.load();

        playerDataManager = new PlayerDataManager(this);
        levelManager = new PickaxeLevelManager(this);
        hologramManager = new HologramManager(this);
        hologramManager.loadAll(mineManager);
        WorldEditHook worldEditHook = new WorldEditHook();
        MineResetService resetService = new MineResetService(this);

        AdminMainMenu adminMainMenu = new AdminMainMenu(mineManager, hologramManager);
        BlockCompositionMenu compositionMenu = new BlockCompositionMenu(this, mineManager);
        MineResetMenu resetMenu = new MineResetMenu(this, mineManager, hologramManager);
        adminMainMenu.setBlockCompositionMenu(compositionMenu);
        adminMainMenu.setMineResetMenu(resetMenu);
        compositionMenu.setAdminMainMenu(adminMainMenu);
        resetMenu.setAdminMainMenu(adminMainMenu);

        getServer().getPluginManager().registerEvents(new MenuBuilder.MenuListener(), this);
        getServer().getPluginManager().registerEvents(new BlockCompositionMenuListener(compositionMenu), this);
        getServer().getPluginManager().registerEvents(new BlockCompositionChatListener(this, compositionMenu), this);
        getServer().getPluginManager().registerEvents(new MineResetChatListener(this, resetMenu), this);
        getServer().getPluginManager().registerEvents(new PlayerMineTrackerListener(mineManager, playerDataManager), this);
        getServer().getPluginManager().registerEvents(new MineProtectionListener(mineManager), this);
        getServer().getPluginManager().registerEvents(
                new MineCommandBlockerListener(mineManager, getConfig().getStringList("mine-protection.blocked-commands")),
                this);

        ItemsAdderHook.tryHook(this);
        BossesProHook.tryHook(this);

        var shopHook = AlkaShopHook.tryHook(this);
        var mcmmoHook = McMMOHook.tryHook(this);
        var aeHook = AdvancedEnchantmentsHook.tryHook(this);

        // NAO resolve o AlkaDropHook aqui de forma sincrona - softdepend no
        // plugin.yml nao garante ordem estrita de enable com muitos plugins no
        // servidor (bug real 2026-08-09: AlkaMines habilitava ANTES do AlkaDrop
        // apesar do softdepend, entao a API dele no ServicesManager ainda nao
        // existia nesse ponto - o hook ficava permanentemente vazio pro resto da
        // sessao mesmo com os dois plugins presentes e saudaveis). Resolve 1 tick
        // depois em vez disso, quando TODOS os plugins ja terminaram o proprio
        // onEnable (o servidor so comeca a rodar tarefas agendadas depois disso).
        AtomicReference<Optional<AlkaDropHook>> dropHookRef = new AtomicReference<>(Optional.empty());
        Bukkit.getScheduler().runTask(this, () -> dropHookRef.set(AlkaDropHook.tryHook(this)));

        getServer().getPluginManager().registerEvents(
                new MineBreakListener(mineManager, playerDataManager, levelManager, shopHook, mcmmoHook, aeHook, dropHookRef::get), this);

        AdminCommands adminCommands = new AdminCommands(mineManager, worldEditHook, adminMainMenu, resetService,
                playerDataManager, hologramManager);
        getCommand("alkamines").setExecutor(adminCommands);
        getCommand("alkamines").setTabCompleter(adminCommands);

        PlayerCommands playerCommands = new PlayerCommands(mineManager, playerDataManager);
        getCommand("mina").setExecutor(playerCommands);
        getCommand("mina").setTabCompleter(playerCommands);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaceholderHook(this, mineManager, playerDataManager, levelManager).register();
            getLogger().info("Hook do PlaceholderAPI registrado.");
        }

        MineResetTask resetTask = new MineResetTask(mineManager, resetService);
        getServer().getScheduler().runTaskTimer(this, resetTask, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, () -> hologramManager.updateAll(mineManager), 20L, 20L);
        // Auto-save periodico do progresso do jogador (blocksBroken/pickaxeLevel) - o
        // onDisable ja salva, mas isso nao ajuda num crash/kill -9. blocksRemaining de
        // mina tolera perda (recalcula no proximo reset); progresso de jogador nao.
        getServer().getScheduler().runTaskTimerAsynchronously(this, playerDataManager::save, 6000L, 6000L);

        getLogger().info("AlkaMines habilitado com " + mineManager.getMines().size() + " mina(s).");
    }

    @Override
    public void onDisable() {
        if (mineManager != null) {
            mineManager.save();
        }
        if (playerDataManager != null) {
            playerDataManager.save();
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
