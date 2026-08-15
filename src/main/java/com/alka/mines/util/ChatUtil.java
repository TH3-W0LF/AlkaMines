package com.alka.mines.util;

import com.alka.mines.config.MessagesConfig;
import com.alkacode.core.api.AlkaAPI;
import com.alkacode.core.api.MessageProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.Map;

/**
 * Mensagens do AlkaMines. parse/send delegam pro {@link MessageProvider} do AlkaCore
 * (AlkaAPI#getMessages) - padroniza prefixo/formatacao com o resto do studio.
 * sendKey resolve a mensagem via {@link MessagesConfig} (messages.yml) - forma
 * preferida pra mensagens de chat editaveis sem recompilar; cai pro provider do
 * AlkaCore se o MessagesConfig ainda nao foi inicializado.
 * toLegacy fica local porque e conversao pra consumidores que so entendem codigos
 * legacy (holograma do DHAPI, scoreboard/TAB via placeholder).
 */
public final class ChatUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    // character(SECTION_CHAR) (nao legacyAmpersand()) + useUnusualXRepeatedCharacterHexFormat():
    // consumidores (scoreboard/TAB/holograma) so entendem codigo real "§", nunca texto "&" cru -
    // mesmo bug ja corrigido no AlkaVips (v1.0.14 -> v1.0.15, ver project-alkavips na memoria).
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private ChatUtil() {
    }

    public static Component parse(String message) {
        return provider().parse(message);
    }

    public static void send(CommandSender sender, String message) {
        provider().send(sender, message);
    }

    public static void sendKey(CommandSender sender, String key) {
        sendKey(sender, key, null);
    }

    public static void sendKey(CommandSender sender, String key, Map<String, String> placeholders) {
        MessagesConfig cfg = MessagesConfig.getInstance();
        if (cfg != null) {
            sender.sendMessage(cfg.getComponent(key, placeholders));
        } else {
            send(sender, "<red>[messages.yml nao carregado] " + key);
        }
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
