package com.alka.mines.util;

import org.bukkit.Bukkit;

import java.util.logging.Level;

/**
 * Log de debug do AlkaMines, ligado via debug: true no config.yml (ou
 * /alkamines debug em runtime, sem precisar de reload). So custa alguma coisa
 * quando habilitado - toda chamada faz short-circuit no flag estatico antes de
 * montar a string, entao o custo em producao e zero.
 */
public final class DebugLogger {

    private static final String PREFIX = "[AlkaMines-DEBUG]";
    private static boolean enabled = false;

    private DebugLogger() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void log(String message, Object... args) {
        if (!enabled) {
            return;
        }
        Bukkit.getLogger().info(PREFIX + " " + format(message, args));
    }

    public static void warn(String message, Object... args) {
        if (!enabled) {
            return;
        }
        Bukkit.getLogger().log(Level.WARNING, PREFIX + " " + format(message, args));
    }

    private static String format(String message, Object... args) {
        return args.length == 0 ? message : String.format(message, args);
    }
}
