package com.alka.mines.model;

import org.bukkit.Material;

public class MineBlock {

    private Material material;
    private double weight;
    private double normalXp; // XP vanilla dado ao quebrar - 0 = nenhum
    private double mcmmoXp; // XP de mcMMO (Mineracao) - so vale quando mcmmoXpConfigured=true
    private boolean mcmmoXpConfigured; // true = valor da mina e autoritativo (0 = sem XP);
                                       // false = cai pra tabela global mcmmo-xp do config.yml

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

    public double getNormalXp() {
        return normalXp;
    }

    public void setNormalXp(double normalXp) {
        this.normalXp = normalXp;
    }

    public double getMcmmoXp() {
        return mcmmoXp;
    }

    public void setMcmmoXp(double mcmmoXp) {
        this.mcmmoXp = mcmmoXp;
    }

    /** true = o valor de {@link #getMcmmoXp()} da mina vale exatamente (0 = sem XP);
     * false = usa a tabela global mcmmo-xp do config.yml como fallback. */
    public boolean isMcmmoXpConfigured() {
        return mcmmoXpConfigured;
    }

    public void setMcmmoXpConfigured(boolean mcmmoXpConfigured) {
        this.mcmmoXpConfigured = mcmmoXpConfigured;
    }
}
