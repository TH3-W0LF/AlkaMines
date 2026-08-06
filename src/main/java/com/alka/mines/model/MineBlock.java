package com.alka.mines.model;

import org.bukkit.Material;

public class MineBlock {

    private Material material;
    private double weight;

    public MineBlock(Material material, double weight) {
        this.material = material;
        this.weight = weight;
    }

    public Material getMaterial() {
        return material;
    }

    public void setMaterial(Material material) {
        this.material = material;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }
}
