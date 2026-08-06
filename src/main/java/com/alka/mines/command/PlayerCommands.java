package com.alka.mines.command;

import com.alka.mines.gui.MineListMenu;
import com.alka.mines.manager.MineManager;
import com.alka.mines.manager.PlayerDataManager;
import com.alka.mines.model.Mine;
import com.alka.mines.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class PlayerCommands implements CommandExecutor, TabCompleter {

    private final MineManager mineManager;
    private final PlayerDataManager playerDataManager;
    private final MineListMenu listMenu;

    public PlayerCommands(MineManager mineManager, PlayerDataManager playerDataManager) {
        this.mineManager = mineManager;
        this.playerDataManager = playerDataManager;
        this.listMenu = new MineListMenu(mineManager, playerDataManager);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            ChatUtil.send(sender, "<red>Apenas jogadores podem usar esse comando.");
            return true;
        }

        if (args.length == 0) {
            // /minas (alias) sempre abre a lista - /mina (nome principal) teleporta
            // direto se so tiver 1 mina disponivel. Os dois compartilham o mesmo
            // registro de comando no plugin.yml, entao "label" e o unico jeito de
            // diferenciar qual das duas palavras o jogador digitou.
            if (label.equalsIgnoreCase("minas")) {
                listMenu.open(player);
            } else {
                handleDefault(player);
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "ir" -> handleIr(player, args);
            case "sair" -> handleSair(player);
            case "lista" -> listMenu.open(player);
            default -> ChatUtil.send(player, "<red>Uso: /mina [ir <id>|sair|lista]");
        }
        return true;
    }

    private void handleDefault(Player player) {
        Collection<Mine> mines = mineManager.getMines();
        if (mines.size() == 1) {
            teleportTo(player, mines.iterator().next());
            return;
        }
        listMenu.open(player);
    }

    private void handleIr(Player player, String[] args) {
        if (!player.hasPermission("alkaminas.ir")) {
            ChatUtil.send(player, "<red>Voce nao tem permissao.");
            return;
        }

        Mine target;
        if (args.length >= 2) {
            target = mineManager.getMine(args[1].toLowerCase()).orElse(null);
            if (target == null) {
                ChatUtil.send(player, "<red>Mina nao encontrada: " + args[1]);
                return;
            }
        } else {
            target = bestMineFor(player).orElse(null);
            if (target == null) {
                ChatUtil.send(player, "<red>Nenhuma mina disponivel para voce no momento.");
                return;
            }
        }

        teleportTo(player, target);
    }

    private void handleSair(Player player) {
        Location exit = playerDataManager.getExitLocation();
        if (exit == null) {
            World world = Bukkit.getWorlds().get(0);
            exit = world.getSpawnLocation();
        }
        player.teleport(exit);
        playerDataManager.get(player.getUniqueId()).setCurrentMineId(null);
        ChatUtil.send(player, "<green>Voce saiu da area de minas.");
    }

    private void teleportTo(Player player, Mine mine) {
        if (!canAccess(player, mine)) {
            ChatUtil.send(player, "<red>Voce precisa de nivel de picareta " + mine.getSettings().getMinPickaxeLevel()
                    + " para entrar em '" + mine.getId() + "'.");
            return;
        }
        player.teleport(mine.getSpawn());
        // forca o placeholder/tracker a reconhecer a mina mesmo que o spawn configurado
        // fique fora da regiao exata do WorldEdit (ex: uma plataforma de entrada) - senao
        // so o proximo PlayerMoveEvent detectaria isso, com um atraso perceptivel.
        playerDataManager.get(player.getUniqueId()).setCurrentMineId(mine.getId());
        ChatUtil.send(player, "<green>Teleportado para a mina '" + mine.getId() + "'.");
    }

    private Optional<Mine> bestMineFor(Player player) {
        return mineManager.getMines().stream()
                .filter(mine -> canAccess(player, mine))
                .min(Comparator.comparingInt(mine -> mine.getSettings().getMinPickaxeLevel()));
    }

    private boolean canAccess(Player player, Mine mine) {
        int required = mine.getSettings().getMinPickaxeLevel();
        if (required <= 0) {
            return true;
        }
        return playerDataManager.get(player.getUniqueId()).getPickaxeLevel() >= required;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("ir", "sair", "lista").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("ir")) {
            return mineManager.getMines().stream().map(Mine::getId).collect(Collectors.toList());
        }
        return List.of();
    }
}
