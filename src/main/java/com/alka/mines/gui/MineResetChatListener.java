package com.alka.mines.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/** Captura o valor (minutos ou %) digitado no chat pelo admin - ver MineResetMenu. */
public class MineResetChatListener implements Listener {

    private final JavaPlugin plugin;
    private final MineResetMenu menu;

    public MineResetChatListener(JavaPlugin plugin, MineResetMenu menu) {
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
