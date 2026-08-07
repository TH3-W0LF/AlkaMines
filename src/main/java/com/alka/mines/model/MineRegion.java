package com.alka.mines.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class MineRegion {

    private String world;
    private int x1, y1, z1;
    private int x2, y2, z2;

    public MineRegion(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.world = world;
        this.x1 = Math.min(x1, x2);
        this.x2 = Math.max(x1, x2);
        this.y1 = Math.min(y1, y2);
        this.y2 = Math.max(y1, y2);
        this.z1 = Math.min(z1, z2);
        this.z2 = Math.max(z1, z2);
    }

    public long getVolume() {
        return (long) (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
    }

    public Location getCenter() {
        World w = Bukkit.getWorld(world);
        double x = x1 + (x2 - x1) / 2.0 + 0.5;
        double y = y1 + (y2 - y1) / 2.0 + 1.0;
        double z = z1 + (z2 - z1) / 2.0 + 0.5;
        return new Location(w, x, y, z);
    }

    /** Regiao exata (X, Y e Z) - usado pra mineracao, onde a altura importa de verdade. */
    public boolean contains(Location location) {
        World w = location.getWorld();
        if (w == null || !w.getName().equals(world)) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
    }

    /** So X e Z - Y e infinito. Usado pra lobby/dungeon: a plataforma de entrada de uma
     * mina raramente fica exatamente dentro do intervalo de Y da selecao de mineracao. */
    public boolean containsIgnoreY(Location location) {
        World w = location.getWorld();
        if (w == null || !w.getName().equals(world)) {
            return false;
        }
        int x = location.getBlockX();
        int z = location.getBlockZ();
        return x >= x1 && x <= x2 && z >= z1 && z <= z2;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public int getX1() {
        return x1;
    }

    public int getY1() {
        return y1;
    }

    public int getZ1() {
        return z1;
    }

    public int getX2() {
        return x2;
    }

    public int getY2() {
        return y2;
    }

    public int getZ2() {
        return z2;
    }
}
