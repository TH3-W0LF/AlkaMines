package com.alka.mines.model;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

public class MineBlock {

    private Material material;
    private double weight;
    /** Recompensas extras por moeda (ex: "escarion" -> 2.0), somadas ao MineSettings.rewardPerBlock da mina. */
    private final Map<String, Double> rewards = new HashMap<>();

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

    public boolean hasReward(String currency) {
        return getReward(currency) > 0;
    }

    public double getReward(String currency) {
        return rewards.getOrDefault(currency.toLowerCase(), 0.0);
    }

    public void setReward(String currency, double amount) {
        if (amount <= 0) {
            rewards.remove(currency.toLowerCase());
        } else {
            rewards.put(currency.toLowerCase(), amount);
        }
    }

    public Map<String, Double> getRewards() {
        return rewards;
    }
}
