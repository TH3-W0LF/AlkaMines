package com.alka.mines.command;

import com.alka.mines.gui.MineListMenu;
import com.alka.mines.gui.PrivateMineGui;
import com.alka.mines.gui.RankingGui;
import com.alka.mines.manager.MineManager;
import com.alka.mines.manager.PlayerDataManager;
import com.alka.mines.manager.PrivateMineManager;
import com.alka.mines.model.Mine;
import com.alka.mines.model.MineTemplate;
import com.alka.mines.model.PrivateMine;
import com.alka.mines.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class PlayerCommands implements CommandExecutor, TabCompleter {

    private final MineManager mineManager;
    private final PlayerDataManager playerDataManager;
    private final PrivateMineManager privateMineManager;
    private final JavaPlugin plugin;
    private final MineListMenu listMenu;
    private final Map<UUID, Long> deleteConfirm = new HashMap<>();

    public PlayerCommands(JavaPlugin plugin, MineManager mineManager, PlayerDataManager playerDataManager,
                          PrivateMineManager privateMineManager) {
        this.plugin = plugin;
        this.mineManager = mineManager;
        this.playerDataManager = playerDataManager;
        this.privateMineManager = privateMineManager;
        this.listMenu = new MineListMenu(plugin, mineManager, playerDataManager);
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
            case "ranking", "top" -> sendRanking(player);
            case "particular" -> handleParticular(player, args);
            default -> ChatUtil.send(player, "<red>Uso: /mina [ir <id>|sair|lista|ranking|particular info|particular deletar]");
        }
        return true;
    }

    private void sendRanking(Player player) {
        if (playerDataManager.getTopBlocksBroken(1).isEmpty()) {
            ChatUtil.send(player, "<yellow>Ainda nao ha dados suficientes para o ranking.");
            return;
        }
        new RankingGui(plugin, player, playerDataManager).open();
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
            if (mine.getSettings().hasPermission() && !player.hasPermission(mine.getSettings().getPermission())) {
                ChatUtil.send(player, "<red>Voce precisa da permissao <white>" + mine.getSettings().getPermission()
                        + "</white><red> para entrar em '" + mine.getId() + "'.");
                return;
            }
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
        if (mine.getSettings().hasPermission() && !player.hasPermission(mine.getSettings().getPermission())) {
            return false;
        }
        int required = mine.getSettings().getMinPickaxeLevel();
        if (required <= 0) {
            return true;
        }
        return playerDataManager.get(player.getUniqueId()).getPickaxeLevel() >= required;
    }

    /** /mina particular info|deletar - gerencia a mina particular na plot atual. */
    private void handleParticular(Player player, String[] args) {
        if (args.length < 2) {
            ChatUtil.send(player, "<red>Uso: /mina particular <info|deletar>");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "menu" -> new PrivateMineGui(plugin, player, privateMineManager).open();
            case "home" -> {
                PrivateMine mine = privateMineManager.getMineProtectingAt(player.getLocation())
                        .filter(m -> m.getOwner().equals(player.getUniqueId()))
                        .orElseGet(() -> privateMineManager.getForPlayer(player.getUniqueId()).stream()
                                .findFirst().orElse(null));
                if (mine == null) {
                    ChatUtil.send(player, "<red>Voce nao tem uma mina particular.");
                    return;
                }
                player.teleport(privateMineManager.getHomeLocation(mine));
                ChatUtil.send(player, "<green>Voce foi levado ao topo da sua mina.");
            }
            case "compartilhar" -> {
                if (args.length < 3) {
                    ChatUtil.send(player, "<red>Uso: /mina particular compartilhar <jogador>");
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null || !target.isOnline()) {
                    ChatUtil.send(player, "<red>Jogador offline: " + args[2]);
                    return;
                }
                String error = privateMineManager.addMember(player, target);
                if (error != null) {
                    ChatUtil.send(player, error);
                } else {
                    ChatUtil.send(player, "<green>" + target.getName() + " agora e membro da sua mina.");
                    ChatUtil.send(target, "<green>Voce agora pode usar a mina de " + player.getName() + ".");
                }
            }
            case "info" -> {
                PrivateMine mine = privateMineManager.getMineProtectingAt(player.getLocation())
                        .orElseGet(() -> privateMineManager.getForPlayer(player.getUniqueId()).stream()
                                .findFirst().orElse(null));
                if (mine == null) {
                    ChatUtil.send(player, "<yellow>Voce nao tem uma mina particular.");
                    return;
                }
                Optional<MineTemplate> template = privateMineManager.getTemplate(mine.getTemplateId());
                int interval = mine.getResetIntervalMinutes() > 0 ? mine.getResetIntervalMinutes()
                        : template.map(MineTemplate::getResetIntervalMinutes).orElse(0);
                long remainingMs = Math.max(0, interval * 60_000L - (System.currentTimeMillis() - mine.getLastReset()));
                long remainingSec = remainingMs / 1000;
                String timeReal = String.format("%02d:%02d", remainingSec / 60, remainingSec % 60);
                String ownerName = Bukkit.getOfflinePlayer(mine.getOwner()).getName();
                long volume = mine.volume();
                double pct = volume > 0
                        ? Math.round((mine.getBlocksRemaining() / (double) volume) * 1000.0) / 10.0 : 0.0;
                String founded = new SimpleDateFormat("dd/MM/yyyy").format(new Date(mine.getCreatedAt()));
                String expires = template.isPresent() && template.get().getExpiresInDays() > 0
                        ? new SimpleDateFormat("dd/MM/yyyy").format(new Date(mine.getCreatedAt()
                        + template.get().getExpiresInDays() * 86_400_000L))
                        : "<green>Eterna";
                ChatUtil.send(player, "<gold><bold>◈ Mina Particular ◈</bold>");
                ChatUtil.send(player, "<gray>Tipo: <white>" + (template.isPresent()
                        ? template.get().getDisplayName() : mine.getTemplateId()));
                ChatUtil.send(player, "<gray>Dono: <white>" + (ownerName != null ? ownerName : mine.getOwner()));
                ChatUtil.send(player, "<gray>Blocos: <white>" + String.format(Locale.US, "%,d", mine.getBlocksRemaining())
                        + " <dark_gray>(" + pct + "%)");
                ChatUtil.send(player, "<gray>Prox. Reset: <white>" + interval + "Min <dark_gray>/ " + timeReal);
                ChatUtil.send(player, "<gray>Raridade: <yellow>" + template.map(MineTemplate::getRarity).orElse("★"));
                ChatUtil.send(player, "<gray>Fundada: <white>" + founded);
                ChatUtil.send(player, "<gray>Expira: <white>" + expires);
            }
            case "expandir" -> {
                if (args.length < 3) {
                    ChatUtil.send(player, "<red>Uso: /mina particular expandir <quantidade>");
                    return;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    ChatUtil.send(player, "<red>Quantidade invalida.");
                    return;
                }
                String error = privateMineManager.expand(player, amount);
                if (error != null) {
                    ChatUtil.send(player, error);
                } else {
                    ChatUtil.send(player, "<green>Mina expandida em " + amount + " bloco(s) pra cada lado! "
                            + "Os novos blocos foram preenchidos com a composicao do template.");
                }
            }
            case "deletar" -> {
                Optional<PrivateMine> mine = privateMineManager.getMineProtectingAt(player.getLocation());
                if (mine.isEmpty() || !mine.get().getOwner().equals(player.getUniqueId())) {
                    ChatUtil.send(player, "<red>Nao existe uma mina particular sua aqui.");
                    return;
                }
                Long expiry = deleteConfirm.get(player.getUniqueId());
                if (expiry == null || expiry < System.currentTimeMillis()) {
                    deleteConfirm.put(player.getUniqueId(), System.currentTimeMillis() + 5000);
                    ChatUtil.send(player, "<yellow>Digite <red>/mina particular deletar</red><yellow> de novo em ate 5s pra confirmar.");
                    return;
                }
                deleteConfirm.remove(player.getUniqueId());
                if (privateMineManager.deleteAt(player, player.getLocation())) {
                    ChatUtil.send(player, "<green>Mina particular removida.");
                } else {
                    ChatUtil.send(player, "<red>Nao foi possivel remover.");
                }
            }
            default -> ChatUtil.send(player, "<red>Uso: /mina particular <menu|info|deletar|expandir <quantidade>|home|compartilhar <jogador>>");
        }
    }

    private String format(long seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("ir", "sair", "lista", "ranking").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("ir")) {
            return mineManager.getMines().stream().map(Mine::getId).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("particular")) {
            return List.of("menu", "info", "deletar", "expandir", "home", "compartilhar").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
