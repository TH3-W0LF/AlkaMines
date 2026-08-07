package com.alka.mines.model;

import org.bukkit.Material;

public class MineBlock {

    private Material material;
    private String customBlockId; // namespace do ItemsAdder, ex: "myitems:ruby_ore" - null se for Material vanilla
    private double weight;
    private double normalXp; // XP vanilla dado ao quebrar - 0 = nenhum (comportamento atual)
    private double mcmmoXp; // XP de mcMMO (Mineracao) - 0 = usa a tabela global mcmmo-xp do config.yml

    public MineBlock(Material material, double weight) {
        this.material = material;
        this.weight = weight;
    }

    public MineBlock(String customBlockId, double weight) {
        this.customBlockId = customBlockId;
        this.material = Material.STONE; // fallback caso o ItemsAdder seja removido do servidor
        this.weight = weight;
    }

    public boolean isCustomBlock() {
        return customBlockId != null && !customBlockId.isEmpty();
    }

    public Material getMaterial() {
        return material;
    }

    public void setMaterial(Material material) {
        this.material = material;
    }

    public String getCustomBlockId() {
        return customBlockId;
    }

    public void setCustomBlockId(String customBlockId) {
        this.customBlockId = customBlockId;
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

    /** Identidade estavel na composicao - Material sozinho nao basta porque blocos
     * custom sem ItemsAdder instalado caem todos no mesmo fallback (STONE). Usado pra
     * achar/editar/remover a entrada certa no BlockCompositionMenu. */
    public String getCompositionKey() {
        return isCustomBlock() ? "IA:" + customBlockId : "MAT:" + material.name();
    }
}
