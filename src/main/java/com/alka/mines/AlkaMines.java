package com.alka.mines;

import com.alka.mines.command.AdminCommands;
import com.alka.mines.command.PlayerCommands;
import com.alka.mines.gui.AdminMainMenu;
import com.alka.mines.gui.BlockCompositionChatListener;
import com.alka.mines.gui.BlockCompositionMenu;
import com.alka.mines.gui.BlockCompositionMenuListener;
import com.alka.mines.gui.MineResetChatListener;
import com.alka.mines.gui.MineResetMenu;
import com.alka.mines.gui.MineRewardsChatListener;
import com.alka.mines.gui.MineRewardsListener;
import com.alka.mines.gui.MineRewardsMenu;
import com.alka.mines.hologram.HologramManager;
import com.alka.mines.hook.AdvancedEnchantmentsHook;
import com.alka.mines.hook.AlkaDropHook;
import com.alka.mines.hook.AlkaShopHook;
import com.alka.mines.hook.BossesProHook;
import com.alka.mines.hook.ItemsAdderHook;
import com.alka.mines.hook.McMMOHook;
import com.alka.mines.hook.PlaceholderHook;
import com.alka.mines.hook.PlotSquaredHook;
import com.alka.mines.hook.WorldEditHook;
import com.alka.mines.listener.MineBreakListener;
import com.alka.mines.listener.MineCommandBlockerListener;
import com.alka.mines.listener.MineProtectionListener;
import com.alka.mines.listener.PlayerMineTrackerListener;
import com.alka.mines.listener.PrivateMineListener;
import com.alka.mines.manager.MineManager;
import com.alka.mines.manager.PickaxeLevelManager;
import com.alka.mines.manager.PlayerDataManager;
import com.alka.mines.manager.PrivateMineManager;
import com.alka.mines.service.MineResetService;
import com.alka.mines.task.MineResetTask;
import com.alka.mines.util.DebugLogger;
import com.alkacode.core.plugin.AlkaPlugin;
import org.bukkit.Bukkit;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Plugin de minas do AlkaStudio. Migrado pro AlkaCore (ver ALKANETWORKING.md): estende
 * {@link AlkaPlugin} (herda depend/ordem e o acesso ao {@code AlkaAPI}), mensagens via
 * MessageProvider (ChatUtil), GUIs de menu via BaseGui do AlkaCore (GuiListener ja
 * registrado pelo proprio AlkaCore) e dados de jogador no banco do AlkaCore
 * (PlayerMineRepository/AbstractRepository).
 *
 * O editor de composicao (BlockCompositionMenu) fica no proprio listener - precisa
 * acessar o inventario inferior do jogador pra "arrastar" itens, e o GuiListener do
 * AlkaCore cancela qualquer clique fora do inventario da GUI (bottom), o que quebraria
 * isso.
 */
public final class AlkaMines extends AlkaPlugin {

    private MineManager mineManager;
    private PlayerDataManager playerDataManager;
    private HologramManager hologramManager;
    private PickaxeLevelManager levelManager;

    @Override
    protected void onPluginEnable() {
        DebugLogger.setEnabled(getConfig().getBoolean("debug", false));

        mineManager = new MineManager(this);
        mineManager.load();

        playerDataManager = new PlayerDataManager(this, getAlkaAPI().getDatabase());
        levelManager = new PickaxeLevelManager(this);
        hologramManager = new HologramManager(this);
        hologramManager.loadAll(mineManager);
        PrivateMineManager privateMineManager = new PrivateMineManager(this);
        PlotSquaredHook.tryHook();
        WorldEditHook worldEditHook = new WorldEditHook();
        MineResetService resetService = new MineResetService(this);

        AdminMainMenu adminMainMenu = new AdminMainMenu(this, mineManager, hologramManager);
        BlockCompositionMenu compositionMenu = new BlockCompositionMenu(this, mineManager);
        MineResetMenu resetMenu = new MineResetMenu(this, mineManager, hologramManager);
        MineRewardsMenu rewardsMenu = new MineRewardsMenu(this, mineManager);
        adminMainMenu.setBlockCompositionMenu(compositionMenu);
        adminMainMenu.setMineResetMenu(resetMenu);
        adminMainMenu.setMineRewardsMenu(rewardsMenu);
        compositionMenu.setAdminMainMenu(adminMainMenu);
        resetMenu.setAdminMainMenu(adminMainMenu);
        rewardsMenu.setAdminMainMenu(adminMainMenu);

        getServer().getPluginManager().registerEvents(new BlockCompositionMenuListener(compositionMenu), this);
        getServer().getPluginManager().registerEvents(new BlockCompositionChatListener(this, compositionMenu), this);
        getServer().getPluginManager().registerEvents(new MineResetChatListener(this, resetMenu), this);
        getServer().getPluginManager().registerEvents(new MineRewardsListener(rewardsMenu), this);
        getServer().getPluginManager().registerEvents(new MineRewardsChatListener(this, rewardsMenu), this);
        getServer().getPluginManager().registerEvents(
                new PlayerMineTrackerListener(mineManager, playerDataManager, privateMineManager), this);
        getServer().getPluginManager().registerEvents(new MineProtectionListener(mineManager), this);
        getServer().getPluginManager().registerEvents(
                new MineCommandBlockerListener(mineManager, getConfig().getStringList("mine-protection.blocked-commands")),
                this);
        getServer().getPluginManager().registerEvents(new PrivateMineListener(privateMineManager), this);

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
                new MineBreakListener(mineManager, playerDataManager, levelManager, privateMineManager,
                        shopHook, mcmmoHook, aeHook, dropHookRef::get), this);

        AdminCommands adminCommands = new AdminCommands(mineManager, worldEditHook, adminMainMenu, resetService,
                playerDataManager, hologramManager, privateMineManager);
        getCommand("alkamines").setExecutor(adminCommands);
        getCommand("alkamines").setTabCompleter(adminCommands);

        PlayerCommands playerCommands = new PlayerCommands(this, mineManager, playerDataManager, privateMineManager);
        getCommand("mina").setExecutor(playerCommands);
        getCommand("mina").setTabCompleter(playerCommands);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaceholderHook(this, mineManager, playerDataManager, levelManager, privateMineManager).register();
            getLogger().info("Hook do PlaceholderAPI registrado.");
        }

        MineResetTask resetTask = new MineResetTask(mineManager, resetService);
        getServer().getScheduler().runTaskTimer(this, resetTask, 20L, 20L);
        // reset automatico das minas particulares (por intervalo do template)
        getServer().getScheduler().runTaskTimer(this, privateMineManager::tickResets, 20L, 20L);
        // update a cada 5s em vez de 1s - recriar as linhas de todos os hologramas a cada
        // segundo so queimava CPU/tick em servidor cheio sem ganho perceptivel pra ninguem.
        getServer().getScheduler().runTaskTimer(this, () -> hologramManager.updateAll(mineManager), 100L, 100L);
        // Auto-save periodico do progresso do jogador (blocksBroken/pickaxeLevel) na main
        // thread - o banco do AlkaCore (SQLite) usa pool de 1 conexao, escritas async
        // concorrentes dariam "database is locked". Upserts individuais sao baratos.
        getServer().getScheduler().runTaskTimer(this, playerDataManager::save, 6000L, 6000L);
        // Auto-save periodico das minas dirty (blocksRemaining/lastReset) na main thread -
        // barato (so as minas alteradas, arquivo pequeno) e evita a corrida de duas escritas
        // concorrentes no mines.yml que um save async teria com os save() explicito do admin.
        getServer().getScheduler().runTaskTimer(this, mineManager::saveDirty, 1200L, 1200L);

        getLogger().info("AlkaMines habilitado com " + mineManager.getMines().size() + " mina(s).");
        DebugLogger.log("Boot concluido: %d mina(s), %d chunk(s) indexados.",
                mineManager.getMines().size(), mineManager.getIndexedChunkCount());
    }

    @Override
    protected void onPluginDisable() {
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
