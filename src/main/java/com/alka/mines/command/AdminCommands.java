package com.alka.mines.command;

import com.alka.mines.gui.AdminMainMenu;
import com.alka.mines.hook.WorldEditHook;
import com.alka.mines.manager.MineManager;
import com.alka.mines.manager.PlayerDataManager;
import com.alka.mines.model.Mine;
import com.alka.mines.model.MineRegion;
import com.alka.mines.service.MineResetService;
import com.alka.mines.util.ChatUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * /minaadmin - CommandExecutor classico (fallback documentado no prompt em vez de
 * Brigadier: mesmo padrao ja usado por todo comando administrativo neste workspace,
 * sem o overhead/risco de versao do registro via LifecycleEvents.COMMANDS do Paper).
 */
public class AdminCommands implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "criar", "deletar", "editar", "resetar", "setspawn", "setsaida", "lista", "reload");

    private final MineManager mineManager;
    private final WorldEditHook worldEditHook;
    private final AdminMainMenu adminMainMenu;
    private final MineResetService resetService;
    private final PlayerDataManager playerDataManager;

    public AdminCommands(MineManager mineManager, WorldEditHook worldEditHook, AdminMainMenu adminMainMenu,
                          MineResetService resetService, PlayerDataManager playerDataManager) {
        this.mineManager = mineManager;
        this.worldEditHook = worldEditHook;
        this.adminMainMenu = adminMainMenu;
        this.resetService = resetService;
        this.playerDataManager = playerDataManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            ChatUtil.send(sender, "<red>Uso: /minaadmin <criar|deletar|editar|resetar|setspawn|setsaida|lista|reload>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "criar" -> handleCriar(sender, args);
            case "deletar" -> handleDeletar(sender, args);
            case "editar" -> handleEditar(sender, args);
            case "resetar" -> handleResetar(sender, args);
            case "setspawn" -> handleSetSpawn(sender, args);
            case "setsaida" -> handleSetSaida(sender);
            case "lista" -> handleLista(sender);
            case "reload" -> handleReload(sender);
            default -> ChatUtil.send(sender, "<red>Subcomando desconhecido.");
        }
        return true;
    }

    private boolean checkPermission(CommandSender sender, String sub) {
        if (!sender.hasPermission("alkaminas.admin." + sub)) {
            ChatUtil.send(sender, "<red>Voce nao tem permissao.");
            return false;
        }
        return true;
    }

    private void handleCriar(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "criar")) {
            return;
        }
        if (!(sender instanceof Player player)) {
            ChatUtil.send(sender, "<red>Apenas jogadores podem usar esse comando.");
            return;
        }
        if (args.length < 2) {
            ChatUtil.send(sender, "<red>Uso: /minaadmin criar <id>");
            return;
        }

        String id = args[1].toLowerCase();
        if (mineManager.getMine(id).isPresent()) {
            ChatUtil.send(sender, "<red>Ja existe uma mina com o id '" + id + "'.");
            return;
        }

        Optional<MineRegion> selection = worldEditHook.getSelection(player);
        if (selection.isEmpty()) {
            ChatUtil.send(sender, "<red>Voce precisa de uma selecao valida do WorldEdit (//pos1, //pos2 ou //wand).");
            return;
        }

        mineManager.createMine(id, selection.get());
        ChatUtil.send(sender, "<green>Mina '" + id + "' criada com sucesso.");
    }

    private void handleDeletar(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "deletar")) {
            return;
        }
        if (args.length < 2) {
            ChatUtil.send(sender, "<red>Uso: /minaadmin deletar <id>");
            return;
        }

        String id = args[1].toLowerCase();
        if (mineManager.deleteMine(id)) {
            ChatUtil.send(sender, "<green>Mina '" + id + "' deletada.");
        } else {
            ChatUtil.send(sender, "<red>Mina nao encontrada: " + id);
        }
    }

    private void handleEditar(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "editar")) {
            return;
        }
        if (!(sender instanceof Player player)) {
            ChatUtil.send(sender, "<red>Apenas jogadores podem usar esse comando.");
            return;
        }
        if (args.length < 2) {
            ChatUtil.send(sender, "<red>Uso: /minaadmin editar <id>");
            return;
        }

        adminMainMenu.open(player, args[1].toLowerCase());
    }

    private void handleResetar(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "resetar")) {
            return;
        }
        if (args.length < 2) {
            ChatUtil.send(sender, "<red>Uso: /minaadmin resetar <id>");
            return;
        }

        String id = args[1].toLowerCase();
        Mine mine = mineManager.getMine(id).orElse(null);
        if (mine == null) {
            ChatUtil.send(sender, "<red>Mina nao encontrada: " + id);
            return;
        }

        resetService.reset(mine);
        ChatUtil.send(sender, "<green>Reset da mina '" + id + "' iniciado.");
    }

    private void handleSetSpawn(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "setspawn")) {
            return;
        }
        if (!(sender instanceof Player player)) {
            ChatUtil.send(sender, "<red>Apenas jogadores podem usar esse comando.");
            return;
        }
        if (args.length < 2) {
            ChatUtil.send(sender, "<red>Uso: /minaadmin setspawn <id>");
            return;
        }

        String id = args[1].toLowerCase();
        Mine mine = mineManager.getMine(id).orElse(null);
        if (mine == null) {
            ChatUtil.send(sender, "<red>Mina nao encontrada: " + id);
            return;
        }

        mine.setSpawn(player.getLocation());
        mineManager.save();
        ChatUtil.send(sender, "<green>Spawn da mina '" + id + "' atualizado para sua posicao atual.");
    }

    private void handleSetSaida(CommandSender sender) {
        if (!checkPermission(sender, "setsaida")) {
            return;
        }
        if (!(sender instanceof Player player)) {
            ChatUtil.send(sender, "<red>Apenas jogadores podem usar esse comando.");
            return;
        }

        playerDataManager.setExitLocation(player.getLocation());
        ChatUtil.send(sender, "<green>Saida global atualizada para sua posicao atual.");
    }

    private void handleLista(CommandSender sender) {
        if (!checkPermission(sender, "lista")) {
            return;
        }

        Collection<Mine> mines = mineManager.getMines();
        if (mines.isEmpty()) {
            ChatUtil.send(sender, "<yellow>Nenhuma mina criada ainda.");
            return;
        }

        ChatUtil.send(sender, "<gold>Minas (" + mines.size() + "):");
        for (Mine mine : mines) {
            ChatUtil.send(sender, "<gray>- <white>" + mine.getId() + " <gray>(" + mine.getBlocksRemaining() + " blocos restantes)");
        }
    }

    /**
     * save() antes do load() e proposital: persiste blocksRemaining/lastReset (que so
     * vivem em memoria entre resets) antes de recarregar mines.yml do disco, entao um
     * admin editando o arquivo a mao (composicao, recompensas, settings) tem as
     * mudancas aplicadas sem perder o progresso de nenhuma mina em andamento. Nenhum
     * manager guarda referencia de Mine alem de uma chamada de metodo - todo mundo
     * busca de novo via MineManager#getMine/getMineAt, entao a troca de instancias e
     * segura mesmo com jogadores online dentro de uma mina.
     */
    private void handleReload(CommandSender sender) {
        if (!checkPermission(sender, "reload")) {
            return;
        }
        mineManager.save();
        mineManager.load();
        ChatUtil.send(sender, "<green>Configuracao do AlkaMines (mines.yml) recarregada.");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && List.of("deletar", "editar", "resetar", "setspawn").contains(args[0].toLowerCase())) {
            return mineManager.getMines().stream().map(Mine::getId).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
