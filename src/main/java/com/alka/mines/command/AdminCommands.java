package com.alka.mines.command;

import com.alka.mines.gui.AdminMainMenu;
import com.alka.mines.hologram.HologramManager;
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
            "criar", "deletar", "editar", "resetar", "setspawn", "setlobby", "removelobby",
            "setsaida", "lista", "reload", "renomear");

    private final MineManager mineManager;
    private final WorldEditHook worldEditHook;
    private final AdminMainMenu adminMainMenu;
    private final MineResetService resetService;
    private final PlayerDataManager playerDataManager;
    private final HologramManager hologramManager;

    public AdminCommands(MineManager mineManager, WorldEditHook worldEditHook, AdminMainMenu adminMainMenu,
                          MineResetService resetService, PlayerDataManager playerDataManager,
                          HologramManager hologramManager) {
        this.mineManager = mineManager;
        this.worldEditHook = worldEditHook;
        this.adminMainMenu = adminMainMenu;
        this.resetService = resetService;
        this.playerDataManager = playerDataManager;
        this.hologramManager = hologramManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            ChatUtil.send(sender, "<red>Uso: /minaadmin <criar|deletar|editar|resetar|setspawn|setlobby|removelobby|setsaida|lista|reload|renomear>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "criar" -> handleCriar(sender, args);
            case "deletar" -> handleDeletar(sender, args);
            case "editar" -> handleEditar(sender, args);
            case "resetar" -> handleResetar(sender, args);
            case "setspawn" -> handleSetSpawn(sender, args);
            case "setlobby" -> handleSetLobby(sender, args);
            case "removelobby" -> handleRemoveLobby(sender, args);
            case "setsaida" -> handleSetSaida(sender);
            case "lista" -> handleLista(sender);
            case "reload" -> handleReload(sender);
            case "renomear" -> handleRenomear(sender, args);
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

    /** ID de mina vira chave de secao no mines.yml e parte do id do holograma (DHAPI) -
     * restringe a [a-z0-9_-] pra nunca gerar YAML/holograma invalido a partir de texto
     * colado com espacos, acentos ou codigos de cor.
     *
     * Remove o codigo de cor INTEIRO (simbolo + caractere, ex: "&5", "&l", "§a")
     * antes do filtro alfanumerico - se so o simbolo fosse removido, o caractere do
     * codigo (que costuma ser uma letra ou digito valido) vazaria pro ID: "&5&5&lmina"
     * viraria "55lmina" em vez de "mina". */
    private String sanitizeId(String raw) {
        if (raw == null) {
            return "";
        }
        String noColorCodes = raw.replaceAll("[&§][0-9a-fk-or]", "");
        return noColorCodes.toLowerCase().replaceAll("[^a-z0-9_-]", "");
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

        String id = sanitizeId(args[1]);
        if (id.isEmpty()) {
            ChatUtil.send(sender, "<red>ID invalido. Use apenas letras, numeros, underscore e hifen.");
            return;
        }
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

        String id = sanitizeId(args[1]);
        if (mineManager.deleteMine(id)) {
            hologramManager.delete(id);
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

        adminMainMenu.open(player, sanitizeId(args[1]));
    }

    private void handleResetar(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "resetar")) {
            return;
        }
        if (args.length < 2) {
            ChatUtil.send(sender, "<red>Uso: /minaadmin resetar <id>");
            return;
        }

        String id = sanitizeId(args[1]);
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

        String id = sanitizeId(args[1]);
        Mine mine = mineManager.getMine(id).orElse(null);
        if (mine == null) {
            ChatUtil.send(sender, "<red>Mina nao encontrada: " + id);
            return;
        }

        mine.setSpawn(player.getLocation());
        mineManager.save();
        ChatUtil.send(sender, "<green>Spawn da mina '" + id + "' atualizado para sua posicao atual.");
    }

    private void handleSetLobby(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "setlobby")) {
            return;
        }
        if (!(sender instanceof Player player)) {
            ChatUtil.send(sender, "<red>Apenas jogadores podem usar esse comando.");
            return;
        }
        if (args.length < 2) {
            ChatUtil.send(sender, "<red>Uso: /minaadmin setlobby <id>");
            return;
        }

        String id = sanitizeId(args[1]);
        Mine mine = mineManager.getMine(id).orElse(null);
        if (mine == null) {
            ChatUtil.send(sender, "<red>Mina nao encontrada: " + id);
            return;
        }

        Optional<MineRegion> selection = worldEditHook.getSelection(player);
        if (selection.isEmpty()) {
            ChatUtil.send(sender, "<red>Voce precisa de uma selecao valida do WorldEdit (//pos1, //pos2 ou //wand).");
            return;
        }

        mine.setLobbyRegion(selection.get());
        mineManager.save();
        ChatUtil.send(sender, "<green>Area da mina '" + id + "' (lobby/dungeon) definida.");
        ChatUtil.send(sender, "<gray>A regiao de mineracao continua sendo a original - o lobby so vale pra tracking/placeholder e protecao de comando.");
    }

    private void handleRemoveLobby(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "removelobby")) {
            return;
        }
        if (args.length < 2) {
            ChatUtil.send(sender, "<red>Uso: /minaadmin removelobby <id>");
            return;
        }

        String id = sanitizeId(args[1]);
        Mine mine = mineManager.getMine(id).orElse(null);
        if (mine == null) {
            ChatUtil.send(sender, "<red>Mina nao encontrada: " + id);
            return;
        }

        mine.setLobbyRegion(null);
        mineManager.save();
        ChatUtil.send(sender, "<green>Area da mina '" + id + "' removida - agora so a regiao de mineracao conta.");
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
            String lobbyTag = mine.getLobbyRegion() != null ? " <light_purple>[lobby]" : "";
            ChatUtil.send(sender, "<gray>- <white>" + mine.getId() + " <gray>(" + mine.getBlocksRemaining() + " blocos restantes)" + lobbyTag);
        }
    }

    /**
     * save() antes do load() e proposital: persiste blocksRemaining/lastReset (que so
     * vivem em memoria entre resets) antes de recarregar mines.yml do disco, entao um
     * admin editando o arquivo a mao (composicao, settings) tem as
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

    private void handleRenomear(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "renomear")) {
            return;
        }
        if (args.length < 3) {
            ChatUtil.send(sender, "<red>Uso: /minaadmin renomear <id> <nome...>");
            return;
        }

        String id = sanitizeId(args[1]);
        Mine mine = mineManager.getMine(id).orElse(null);
        if (mine == null) {
            ChatUtil.send(sender, "<red>Mina nao encontrada: " + id);
            return;
        }

        String name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        mine.setDisplayName(name);
        mineManager.save();
        hologramManager.updateHologram(mine);
        ChatUtil.send(sender, "<green>Mina '" + id + "' renomeada para '" + name + "'.");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && List.of("deletar", "editar", "resetar", "setspawn", "setlobby", "removelobby", "renomear").contains(args[0].toLowerCase())) {
            return mineManager.getMines().stream().map(Mine::getId).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
