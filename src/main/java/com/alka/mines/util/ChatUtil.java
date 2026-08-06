package com.alka.mines.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

public final class ChatUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private ChatUtil() {
    }

    public static Component parse(String message) {
        return MM.deserialize(message);
    }

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(parse(message));
    }
}
