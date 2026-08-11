package com.alka.mines.util;

import com.alkacode.core.api.AlkaAPI;
import com.alkacode.core.api.MessageProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

/**
 * Mensagens do AlkaMines. parse/send delegam pro {@link MessageProvider} do AlkaCore
 * (AlkaAPI#getMessages) - padroniza prefixo/formatacao com o resto do studio.
 * toLegacy fica local porque e conversao pra consumidores que so entendem codigos
 * legacy (holograma do DHAPI, scoreboard/TAB via placeholder).
 */
public final class ChatUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private ChatUtil() {
    }

    public static Component parse(String message) {
        return provider().parse(message);
    }

    public static void send(CommandSender sender, String message) {
        provider().send(sender, message);
    }

    private static MessageProvider provider() {
        return AlkaAPI.get().getMessages();
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
