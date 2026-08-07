package com.alka.mines.model;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

public class Mine {

    private String id;
    private String displayName;
    private MineRegion region;
    private MineRegion lobbyRegion;
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
        this.lobbyRegion = null;
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

    /** Area maior opcional (lobby/dungeon) usada pra tracking/placeholder/protecao de
     * comando - null = usa a propria regiao de mineracao pra tudo (ver containsLobby). */
    public MineRegion getLobbyRegion() {
        return lobbyRegion;
    }

    public void setLobbyRegion(MineRegion lobbyRegion) {
        this.lobbyRegion = lobbyRegion;
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

    /** Area de mineracao (blocos quebraveis) - a regiao original do WorldEdit. */
    public boolean containsMining(Location location) {
        return region.contains(location);
    }

    /** Area da mina como um todo (lobby/dungeon) - cai pra regiao de mineracao se
     * nenhum lobbyRegion foi configurado via /minaadmin setlobby. Ignora Y nos dois
     * casos: a plataforma de entrada raramente fica exatamente dentro do intervalo de
     * Y da selecao original, entao qualquer altura na coluna X/Z conta como "na mina"
     * pra tracking/placeholder/bloqueio de comando (NUNCA pra mineracao - isso e
     * sempre containsMining, regiao exata). */
    public boolean containsLobby(Location location) {
        return lobbyRegion != null ? lobbyRegion.containsIgnoreY(location) : region.containsIgnoreY(location);
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
