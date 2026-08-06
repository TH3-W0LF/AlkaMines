package com.alka.mines.model;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

public class Mine {

    private String id;
    private String displayName;
    private MineRegion region;
    private Location spawn;
    private Location exit;
    private Location hologramLocation;
    private List<MineBlock> composition;
    private MineSettings settings;
    private long lastReset;
    private int blocksRemaining;
    private String category;
    private Material icon;

    public Mine(String id, String displayName, MineRegion region) {
        this.id = id;
        this.displayName = displayName;
        this.region = region;
        this.spawn = region.getCenter();
        this.exit = null;
        this.hologramLocation = null;
        this.composition = new ArrayList<>();
        this.settings = new MineSettings();
        this.lastReset = System.currentTimeMillis();
        this.blocksRemaining = (int) Math.min(region.getVolume(), Integer.MAX_VALUE);
        this.category = "geral";
        this.icon = null;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public MineRegion getRegion() {
        return region;
    }

    public void setRegion(MineRegion region) {
        this.region = region;
    }

    public Location getSpawn() {
        return spawn;
    }

    public void setSpawn(Location spawn) {
        this.spawn = spawn;
    }

    public Location getExit() {
        return exit;
    }

    public void setExit(Location exit) {
        this.exit = exit;
    }

    public Location getHologramLocation() {
        return hologramLocation;
    }

    public void setHologramLocation(Location hologramLocation) {
        this.hologramLocation = hologramLocation;
    }

    public List<MineBlock> getComposition() {
        return composition;
    }

    /** Primeiro MineBlock da composicao com esse material, ou null se o material nao estiver configurado. */
    public MineBlock getCompositionBlock(Material material) {
        for (MineBlock block : composition) {
            if (block.getMaterial() == material) {
                return block;
            }
        }
        return null;
    }

    public void setComposition(List<MineBlock> composition) {
        this.composition = composition;
    }

    public MineSettings getSettings() {
        return settings;
    }

    public void setSettings(MineSettings settings) {
        this.settings = settings;
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

    public boolean contains(Location location) {
        return region.contains(location);
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = (category == null || category.isBlank()) ? "geral" : category;
    }

    /** Null = sem icone customizado - quem exibe a mina deve escolher um fallback (ex: la verde/vermelha). */
    public Material getIcon() {
        return icon;
    }

    public void setIcon(Material icon) {
        this.icon = icon;
    }
}
