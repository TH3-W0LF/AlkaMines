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
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private final String templateId;
    private long createdAt;
    private long lastReset;
    private int blocksRemaining;

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

    public World getWorld() {
        return org.bukkit.Bukkit.getWorld(worldName);
    }
}
