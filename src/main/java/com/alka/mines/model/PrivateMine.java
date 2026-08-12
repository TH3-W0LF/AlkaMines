package com.alka.mines.model;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/**
 * Mina particular: uma plot do PlotSquared (cuboide bottom->top) de UM jogador, com a
 * composicao de um {@link MineTemplate} e reset automatico por intervalo. Persistida em
 * private-mines.yml. Reutiliza o pipeline de quebra (drops/XP/auto-venda) das minas
 * publicas; a protecao de build fica por conta do proprio PlotSquared.
 */
public class PrivateMine {

    private final UUID owner;
    private final String worldName;
    private int minX, minY, minZ, maxX, maxY, maxZ;
    private final String templateId;
    private long createdAt;
    private long lastReset;
    private int blocksRemaining;
    private int plotMinX, plotMinY, plotMinZ, plotMaxX, plotMaxY, plotMaxZ;
    /** Intervalo de reset (min) definido pelo DONO via menu particular - 0 = usa o do template. */
    private int resetIntervalMinutes;
    /** Nivel de upgrade (quantas vezes expandiu) - o custo de expandir escala com ele. */
    private int upgradeLevel;

    public PrivateMine(UUID owner, String worldName, int minX, int minY, int minZ,
                       int maxX, int maxY, int maxZ, String templateId) {
        this.owner = owner;
        this.worldName = worldName;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.templateId = templateId;
        this.createdAt = System.currentTimeMillis();
        this.lastReset = System.currentTimeMillis();
        this.blocksRemaining = (int) Math.min(volume(), Integer.MAX_VALUE);
    }

    public UUID getOwner() {
        return owner;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }

    public String getTemplateId() {
        return templateId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getLastReset() {
        return lastReset;
    }

    public void setLastReset(long lastReset) {
        this.lastReset = lastReset;
    }

    public int getBlocksRemaining() {
        return blocksRemaining;
    }

    public void setBlocksRemaining(int blocksRemaining) {
        this.blocksRemaining = blocksRemaining;
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    /** A coluna X/Z esta dentro desta mina particular (ignora Y - plot cheia). */
    public boolean contains(Location location) {
        return location.getWorld().getName().equalsIgnoreCase(worldName)
                && location.getBlockX() >= minX && location.getBlockX() <= maxX
                && location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ;
    }

    /** O ponto (X, Y e Z) esta DENTRO do volume mineravel - usado pra teleportar quem vai
     * ser soterrado no reset. */
    public boolean containsFull(Location location) {
        return contains(location)
                && location.getBlockY() >= minY && location.getBlockY() <= maxY;
    }

    /** O ponto esta dentro da PLOT inteira (nao so do volume mineravel) - usado pra
     * proteger a mina (so a composicao quebra) incluindo paredes/decoracao. */
    public boolean isInsidePlot(Location location) {
        return location.getWorld().getName().equalsIgnoreCase(worldName)
                && location.getBlockX() >= plotMinX && location.getBlockX() <= plotMaxX
                && location.getBlockZ() >= plotMinZ && location.getBlockZ() <= plotMaxZ;
    }

    /** Atualiza o volume mineravel (usado na expansao). */
    public void setMineRegion(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public World getWorld() {
        return org.bukkit.Bukkit.getWorld(worldName);
    }

    /** Bounds da PLOT inteira (nao do volume mineravel) - usados pra backup/restauracao
     * do estado original da plot no delete da mina. */
    public void setPlotBounds(int plotMinX, int plotMinY, int plotMinZ, int plotMaxX, int plotMaxY, int plotMaxZ) {
        this.plotMinX = plotMinX;
        this.plotMinY = plotMinY;
        this.plotMinZ = plotMinZ;
        this.plotMaxX = plotMaxX;
        this.plotMaxY = plotMaxY;
        this.plotMaxZ = plotMaxZ;
    }

    public int getPlotMinX() { return plotMinX; }
    public int getPlotMinY() { return plotMinY; }
    public int getPlotMinZ() { return plotMinZ; }
    public int getPlotMaxX() { return plotMaxX; }
    public int getPlotMaxY() { return plotMaxY; }
    public int getPlotMaxZ() { return plotMaxZ; }

    public int getResetIntervalMinutes() {
        return resetIntervalMinutes;
    }

    public void setResetIntervalMinutes(int resetIntervalMinutes) {
        this.resetIntervalMinutes = resetIntervalMinutes;
    }

    public int getUpgradeLevel() {
        return upgradeLevel;
    }

    public void setUpgradeLevel(int upgradeLevel) {
        this.upgradeLevel = upgradeLevel;
    }
}
