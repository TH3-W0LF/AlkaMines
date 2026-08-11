package com.alka.mines.command;

import com.alka.mines.gui.AdminMainMenu;
import com.alka.mines.hologram.HologramManager;
import com.alka.mines.hook.WorldEditHook;
import com.alka.mines.manager.MineManager;
import com.alka.mines.manager.PlayerDataManager;
import com.alka.mines.manager.PrivateMineManager;
import com.alka.mines.model.Mine;
import com.alka.mines.model.MineRegion;
import com.alka.mines.model.MineTemplate;
import com.alka.mines.service.MineResetService;
import com.alka.mines.util.ChatUtil;
import com.alka.mines.util.DebugLogger;
import org.bukkit.Bukkit;
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
 * /alkamines - CommandExecutor classico (fallback documentado no prompt em vez de
 * Brigadier: mesmo padrao ja usado por todo comando administrativo neste workspace,
 * sem o overhead/risco de versao do registro via LifecycleEvents.COMMANDS do Paper).
 */
public class AdminCommands implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "criar", "deletar", "editar", "resetar", "setspawn", "setlobby", "removelobby",
            "setsaida", "lista", "reload", "renomear", "debug", "givegerador", "esquematicos", "registrarmina");

    private final MineManager mineManager;
    private final WorldEditHook worldEditHook;
    private final AdminMainMenu adminMainMenu;
    private final MineResetService resetService;
    private final PlayerDataManager playerDataManager;
    private final HologramManager hologramManager;
    private final PrivateMineManager privateMineManager;

    public AdminCommands(MineManager mineManager, WorldEditHook worldEditHook, AdminMainMenu adminMainMenu,
                          MineResetService resetService, PlayerDataManager playerDataManager,
                          HologramManager hologramManager, PrivateMineManager privateMineManager) {
        this.mineManager = mineManager;
        this.worldEditHook = worldEditHook;
        this.adminMainMenu = adminMainMenu;
        this.resetService = resetService;
        this.playerDataManager = playerDataManager;
        this.hologramManager = hologramManager;
        this.privateMineManager = privateMineManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            ChatUtil.send(sender, "<red>Uso: /alkamines <criar|deletar|editar|resetar|setspawn|setlobby|removelobby|setsaida|lista|reload|renomear|debug|givegerador|esquematicos|registrarmina>");
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
            case "debug" -> handleDebug(sender);
            case "givegerador" -> handleGiveGerador(sender, args);
            case "esquematicos" -> handleEsquematicos(sender);
            case "registrarmina" -> handleRegistrarMina(sender, args);
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
            ChatUtil.send(sender, "<red>Uso: /alkamines criar <id>");
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

        int maxMineSize = mineManager.getMaxMineSize();
        if (maxMineSize > 0 && !mineManager.isWithinSizeLimit(selection.get())) {
            long area = (long) (selection.get().getX2() - selection.get().getX1() + 1)
                    * (selection.get().getZ2() - selection.get().getZ1() + 1);
            ChatUtil.send(sender, "<red>Mina muito grande: <white>" + area + "</white><red> blocos de area (X*Z), "
                    + "limite configurado de <white>" + maxMineSize + "</white><red>. "
                    + "Ajuste <white>max-mine-size</white> no config.yml se precisar.");
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
            ChatUtil.send(sender, "<red>Uso: /alkamines deletar <id>");
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
            ChatUtil.send(sender, "<red>Uso: /alkamines editar <id>");
            return;
        }

        adminMainMenu.open(player, sanitizeId(args[1]));
    }

    private void handleResetar(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "resetar")) {
            return;
        }
        if (args.length < 2) {
            ChatUtil.send(sender, "<red>Uso: /alkamines resetar <id>");
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
            ChatUtil.send(sender, "<red>Uso: /alkamines setspawn <id>");
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
            ChatUtil.send(sender, "<red>Uso: /alkamines setlobby <id>");
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
        mineManager.reindexMine(mine);
        mineManager.save();
        ChatUtil.send(sender, "<green>Area da mina '" + id + "' (lobby/dungeon) definida.");
        ChatUtil.send(sender, "<gray>A regiao de mineracao continua sendo a original - o lobby so vale pra tracking/placeholder e protecao de comando.");
    }

    private void handleRemoveLobby(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "removelobby")) {
            return;
        }
        if (args.length < 2) {
            ChatUtil.send(sender, "<red>Uso: /alkamines removelobby <id>");
            return;
        }

        String id = sanitizeId(args[1]);
        Mine mine = mineManager.getMine(id).orElse(null);
        if (mine == null) {
            ChatUtil.send(sender, "<red>Mina nao encontrada: " + id);
            return;
        }

        mine.setLobbyRegion(null);
        mineManager.reindexMine(mine);
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
        privateMineManager.reload();
        ChatUtil.send(sender, "<green>Configuracao do AlkaMines (mines.yml + private-mine-templates.yml) recarregada.");
    }

    private void handleRenomear(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "renomear")) {
            return;
        }
        if (args.length < 3) {
            ChatUtil.send(sender, "<red>Uso: /alkamines renomear <id> <nome...>");
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

    /** Liga/desliga o log de debug no console ([AlkaMines-DEBUG]) - nao precisa de reload,
     * mas nao persiste entre restarts (pro boot persistente, use debug: true no config.yml). */
    private void handleDebug(CommandSender sender) {
        if (!checkPermission(sender, "debug")) {
            return;
        }
        boolean nowEnabled = !DebugLogger.isEnabled();
        DebugLogger.setEnabled(nowEnabled);
        ChatUtil.send(sender, nowEnabled
                ? "<green>Debug do AlkaMines <white>LIGADO</white><green> - logs no console ([AlkaMines-DEBUG])."
                : "<red>Debug do AlkaMines <white>DESLIGADO</white><red>.");
    }

    /** /alkamines givegerador <jogador> <template> - da o item gerador de mina particular. */
    private void handleGiveGerador(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "givegerador")) {
            return;
        }
        if (args.length < 3) {
            ChatUtil.send(sender, "<red>Uso: /alkamines givegerador <jogador> <template>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            ChatUtil.send(sender, "<red>Jogador offline: " + args[1]);
            return;
        }
        Optional<MineTemplate> template = privateMineManager.getTemplate(args[2]);
        if (template.isEmpty()) {
            ChatUtil.send(sender, "<red>Template de mina particular nao encontrado: " + args[2]);
            return;
        }
        target.getInventory().addItem(privateMineManager.createGeneratorItem(template.get()));
        ChatUtil.send(sender, "<green>Gerador de '" + template.get().getId() + "' entregue a " + target.getName() + ".");
        ChatUtil.send(target, "<green>Voce recebeu um gerador de mina particular! Use-o com clique direito na sua plot.");
    }

    /** /alkamines esquematicos - mostra os .schem que o plugin enxerga e quais templates usam cada um. */
    private void handleEsquematicos(CommandSender sender) {
        if (!checkPermission(sender, "esquematicos")) {
            return;
        }
        List<java.io.File> files = privateMineManager.listSchematicFiles();
        ChatUtil.send(sender, "<aqua><b>Schematics encontrados (" + files.size() + "):</b>");
        if (files.isEmpty()) {
            ChatUtil.send(sender, "<yellow>Nenhum .schem. Fluxo: //wand, selecione, //copy, //schem save <nome>.");
            return;
        }
        for (java.io.File file : files) {
            String template = privateMineManager.getTemplates().stream()
                    .filter(t -> t.getSchematic() != null
                            && (t.getSchematic().endsWith(".schem")
                            ? t.getSchematic().equalsIgnoreCase(file.getName())
                            : t.getSchematic().equalsIgnoreCase(file.getName().replace(".schem", ""))))
                    .map(com.alka.mines.model.MineTemplate::getId)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("(sem template)");
            ChatUtil.send(sender, "<gray>- <white>" + file.getName() + " <dark_gray>(" + file.getParentFile().getName()
                    + ") <green>-> " + template);
        }
        ChatUtil.send(sender, "<gray>Dica: defina 'schematic: <nome>' no template e rode /alkamines reload.");
    }

    /** /alkamines registrarmina [<template>] <nome> - salva a selecao do WorldEdit como schematic
     *  e amarra no template. Sem //copy: o plugin salva direto da selecao. Com 1 argumento,
     *  cria um template novo com o mesmo nome do schematic. */
    private void handleRegistrarMina(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "registrarmina")) {
            return;
        }
        if (!(sender instanceof Player player)) {
            ChatUtil.send(sender, "<red>Apenas jogadores podem usar esse comando.");
            return;
        }
        if (args.length < 2) {
            ChatUtil.send(sender, "<red>Uso: /alkamines registrarmina <nome>   (cria template novo)");
            ChatUtil.send(sender, "<red>      /alkamines registrarmina <template> <nome>   (usa template existente)");
            return;
        }
        String templateId;
        String name;
        if (args.length == 2) {
            templateId = args[1];
            name = args[1];
        } else {
            templateId = args[1];
            name = args[2];
        }
        String error = privateMineManager.registerMineSchematic(player, name, templateId);
        if (error != null) {
            ChatUtil.send(sender, error);
            return;
        }
        ChatUtil.send(sender, "<green>Mina '" + name + "' registrada no template '" + templateId
                + "'. Da o gerador com /alkamines givegerador <jogador> " + templateId + " e ative na plot.");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && List.of("deletar", "editar", "resetar", "setspawn", "setlobby", "removelobby", "renomear").contains(args[0].toLowerCase())) {
            return mineManager.getMines().stream().map(Mine::getId).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("givegerador")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("givegerador")) {
            return privateMineManager.getTemplates().stream().map(MineTemplate::getId).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("registrarmina")) {
            return privateMineManager.getTemplates().stream().map(MineTemplate::getId).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
