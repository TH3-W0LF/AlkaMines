package com.alka.mines.hook;

import com.alka.mines.util.DebugLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * Ponte soft com o PlotSquared v7 - 100% por reflexao (softdepend no plugin.yml):
 * um import direto de com.plotsquared.* aqui derrubaria o AlkaMines com
 * NoClassDefFoundError sem o PlotSquared instalado.
 *
 * API v7 usada (confirmada no source oficial 7.5.x):
 * - com.plotsquared.core.player.PlotPlayer (ATENCAO: NAO e core.plot.PlotPlayer)
 * - PlotPlayer.from(Object) - converte o org.bukkit.entity.Player sem depender de cache
 *   (wrapPlayer usa getPlayerIfExists e pode voltar null)
 * - PlotPlayer#getCurrentPlot() -> Plot (null na estrada/fora de plot area)
 * - Plot#isOwner(UUID), Plot#getBottomAbs()/getTopAbs() -> core.location.Location
 */
public final class PlotSquaredHook {

    private static boolean available;
    private static Method plotPlayerFromMethod;
    private static Method getCurrentPlotMethod;
    private static Method isOwnerMethod;
    private static Method getBottomAbsMethod;
    private static Method getTopAbsMethod;
    private static Method psLocationGetX;
    private static Method psLocationGetY;
    private static Method psLocationGetZ;
    private static Method psLocationGetWorldName;

    private PlotSquaredHook() {
    }

    public static boolean isEnabled() {
        return available;
    }

    public static void tryHook() {
        if (Bukkit.getPluginManager().getPlugin("PlotSquared") == null) {
            available = false;
            return;
        }
        try {
            Class<?> plotPlayerClass = Class.forName("com.plotsquared.core.player.PlotPlayer");
            Class<?> plotClass = Class.forName("com.plotsquared.core.plot.Plot");
            Class<?> psLocationClass = Class.forName("com.plotsquared.core.location.Location");

            plotPlayerFromMethod = plotPlayerClass.getMethod("from", Object.class);
            getCurrentPlotMethod = plotPlayerClass.getMethod("getCurrentPlot");
            isOwnerMethod = plotClass.getMethod("isOwner", UUID.class);
            getBottomAbsMethod = plotClass.getMethod("getBottomAbs");
            getTopAbsMethod = plotClass.getMethod("getTopAbs");
            psLocationGetX = psLocationClass.getMethod("getX");
            psLocationGetY = psLocationClass.getMethod("getY");
            psLocationGetZ = psLocationClass.getMethod("getZ");
            psLocationGetWorldName = psLocationClass.getMethod("getWorldName");
            available = true;
            Bukkit.getLogger().info("[AlkaMines] Hook PlotSquared v7 ativado (minas particulares).");
        } catch (Throwable t) {
            available = false;
            Bukkit.getLogger().warning("[AlkaMines] PlotSquared presente mas a API v7 nao refletiu "
                    + "(versao diferente?). Mina particular desativada. Motivo: " + t);
        }
    }

    /** Bounds da plot em que o jogador ESTA, se ele for dono (bottom->top). */
    public static Optional<PlotBounds> getOwnedPlotAt(Player player) {
        if (!available) {
            return Optional.empty();
        }
        try {
            Object plotPlayer = plotPlayerFromMethod.invoke(null, player);
            if (plotPlayer == null) {
                DebugLogger.log("PlotSquared: PlotPlayer.from(%s) voltou null.", player.getName());
                return Optional.empty();
            }
            Object plot = getCurrentPlotMethod.invoke(plotPlayer);
            if (plot == null) {
                DebugLogger.log("PlotSquared: nenhuma plot na posicao do jogador %s "
                        + "(estrada ou fora de plot area).", player.getName());
                return Optional.empty();
            }
            if (!(Boolean) isOwnerMethod.invoke(plot, player.getUniqueId())) {
                DebugLogger.log("PlotSquared: plot encontrada mas %s NAO e dono.", player.getName());
                return Optional.empty();
            }
            return Optional.of(extractBounds(plot));
        } catch (Throwable t) {
            DebugLogger.log("PlotSquared: erro ao detectar plot pra %s: %s", player.getName(), t);
            return Optional.empty();
        }
    }

    private static PlotBounds extractBounds(Object plot) throws ReflectiveOperationException {
        Object bottom = getBottomAbsMethod.invoke(plot);
        Object top = getTopAbsMethod.invoke(plot);
        return new PlotBounds(
                (String) psLocationGetWorldName.invoke(bottom),
                (int) psLocationGetX.invoke(bottom), (int) psLocationGetY.invoke(bottom), (int) psLocationGetZ.invoke(bottom),
                (int) psLocationGetX.invoke(top), (int) psLocationGetY.invoke(top), (int) psLocationGetZ.invoke(top));
    }

    public record PlotBounds(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }
}
