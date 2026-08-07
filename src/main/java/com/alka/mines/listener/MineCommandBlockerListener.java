package com.alka.mines.listener;

import com.alka.mines.manager.MineManager;
import com.alka.mines.model.Mine;
import com.alka.mines.util.ChatUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Bloqueia comandos configurados (config.yml: mine-protection.blocked-commands) dentro
 * da area de uma mina (Mine#containsLobby - lobbyRegion se definido, senao a propria
 * regiao de mineracao) pra quem nao tem alkaminas.admin.bypass.commands. Pensado pra
 * prison: /tp, /home, /warp etc podem ser usados pra pular fila ou escapar de
 * restricoes da mina.
 *
 * O handler de teleporte SO cobre ENDER_PEARL/CHORUS_FRUIT - os dois vetores de
 * teleporte que nao passam por PlayerCommandPreprocessEvent. Ele checa que o jogador
 * esta ENTRANDO na mina vindo de fora (getFrom fora + getTo dentro) - checar so o
 * destino cancelaria ate um pearl jogado por um jogador que ja esta dentro da propria
 * mina se movendo, o que nao tem nada a ver com "entrar de fora".
 */
public class MineCommandBlockerListener implements Listener {

    private final MineManager mineManager;
    private final List<String> blockedCommands;

    public MineCommandBlockerListener(MineManager mineManager, List<String> blockedCommands) {
        this.mineManager = mineManager;
        this.blockedCommands = blockedCommands.stream().map(c -> c.toLowerCase(Locale.ROOT)).collect(Collectors.toList());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("alkaminas.admin.bypass.commands")) {
            return;
        }

        Optional<Mine> mine = mineManager.getMineLobbyAt(player.getLocation());
        if (mine.isEmpty()) {
            return;
        }

        String raw = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        int namespaceSplit = raw.indexOf(':');
        String commandName = namespaceSplit >= 0 ? raw.substring(namespaceSplit + 1) : raw;

        if (blockedCommands.contains(commandName)) {
            event.setCancelled(true);
            ChatUtil.send(player, "<red>Voce nao pode usar esse comando dentro da mina <white>"
                    + mine.get().getDisplayName() + "</white><red>.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                && event.getCause() != PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            return;
        }

        Player player = event.getPlayer();
        if (player.hasPermission("alkaminas.admin.bypass.commands")) {
            return;
        }

        boolean enteringFromOutside = mineManager.getMineLobbyAt(event.getFrom()).isEmpty();
        Optional<Mine> target = mineManager.getMineLobbyAt(event.getTo());
        if (enteringFromOutside && target.isPresent()) {
            event.setCancelled(true);
            ChatUtil.send(player, "<red>Voce nao pode entrar na mina <white>" + target.get().getDisplayName()
                    + "</white><red> assim.");
        }
    }
}
