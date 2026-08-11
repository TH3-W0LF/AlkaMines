package com.alka.mines.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Recompensa aleatoria ao quebrar um bloco de uma mina (estilo AxMines, reimplementado).
 * Uma recompensa tem uma chance (%), pode ser filtrada por blocos (vazio = qualquer
 * bloco), entrega itens e/ou roda comandos (placeholder <player>), e pode suprimir o
 * drop nativo do bloco (preventDrops). Vivia em mines.yml por mina.
 */
public class MineReward {

    private double chance = 1.0;
    private List<Material> blocks = new ArrayList<>();
    private List<ItemStack> items = new ArrayList<>();
    private List<String> commands = new ArrayList<>();
    private boolean preventDrops;

    public double getChance() {
        return chance;
    }

    public void setChance(double chance) {
        this.chance = chance;
    }

    /** Blocos que disparam esta recompensa - vazio = qualquer bloco da mina. */
    public List<Material> getBlocks() {
        return blocks;
    }

    public void setBlocks(List<Material> blocks) {
        this.blocks = blocks != null ? blocks : new ArrayList<>();
    }

    public List<ItemStack> getItems() {
        return items;
    }

    public void setItems(List<ItemStack> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    public List<String> getCommands() {
        return commands;
    }

    public void setCommands(List<String> commands) {
        this.commands = commands != null ? commands : new ArrayList<>();
    }

    /** true = nao entrega o drop normal do bloco quando esta recompensa dispara. */
    public boolean isPreventDrops() {
        return preventDrops;
    }

    public void setPreventDrops(boolean preventDrops) {
        this.preventDrops = preventDrops;
    }

    public boolean matches(Material type) {
        return blocks.isEmpty() || blocks.contains(type);
    }
}
