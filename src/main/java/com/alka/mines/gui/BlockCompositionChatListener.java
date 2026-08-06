package com.alka.mines.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Captura a % digitada no chat pelo admin (ver BlockCompositionMenu). So age quando o
 * jogador tem um input pendente; caso contrario o chat segue normal. AsyncChatEvent
 * roda fora da main thread - o processamento em si (toca Mine/MineManager/GUI) e
 * despachado de volta pro main thread.
 */
public class BlockCompositionChatListener implements Listener {

    private final JavaPlugin plugin;
    private final BlockCompositionMenu menu;

    public BlockCompositionChatListener(JavaPlugin plugin, BlockCompositionMenu menu) {
        this.plugin = plugin;
        this.menu = menu;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!menu.isPending(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        Bukkit.getScheduler().runTask(plugin, () -> menu.handleChatInput(player, input));
    }
}
