package com.alka.mines.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

public final class ChatUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private ChatUtil() {
    }

    public static Component parse(String message) {
        return MM.deserialize(message);
    }

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(parse(message));
    }

    /** Converte MiniMessage (<red>, <rainbow>, etc) para codigos & - usado quando o texto
     * (ex: Mine#getDisplayName(), editavel via /alkamines renomear) vai parar em algo que
     * so entende legacy: holograma (DHAPI) ou placeholder consumido por scoreboard/TAB. */
    public static String toLegacy(String text) {
        if (text == null) {
            return "";
        }
        return LEGACY.serialize(MM.deserialize(text));
    }
}
