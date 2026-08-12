package com.alka.mines.model;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/** Template de mina particular (private-mine-templates.yml) - composicao e intervalo de
 * reset pre-configurados pelo admin; cada template vira um item gerador vendivel. */
public class MineTemplate {

    private String id;
    private String displayName;
    private Material icon;
    private int resetIntervalMinutes = 30;
    private List<MineBlock> composition = new ArrayList<>();
    /** Schematic .schem (construcao custom da mina) - o volume mineravel e o bounding box
     * dos blocos de minerio (qualquer Material terminado em "_ORE") detectados dentro dele;
     * tudo que nao e minerio (pedra/decor/paredes) fica intacto pra sempre, nunca reseta.
     * null = preenche a parte superior da plot com {@link #getHeight()} camadas. */
    private String schematic;
    /** Altura (camadas do topo pra baixo) preenchida quando nao ha schematic. */
    private int height = 40;
    /** Deslocamento vertical do schematic em relacao ao fundo da plot - registrado no
     * /alkamines registrarmina pra o paste colar na MESMA altura em que a mina foi construida.
     * Integer.MIN_VALUE = nunca registrado (cai pro getPlotSurfaceY() no paste). NAO usar 0
     * como sentinela: 0 e um offset legitimo (schematic construido rente ao fundo da plot). */
    private int pasteYOffset = Integer.MIN_VALUE;
    /** Raridade exibida no /mina particular info (ex: ★★★) - configuravel por template. */
    private String rarity = "★";
    /** Expira a mina depois de X dias (0 = eterna). Cash = 0; VIP = duracao do grupo. */
    private int expiresInDays;

    public MineTemplate(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName != null ? displayName : id;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public void setIcon(Material icon) {
        this.icon = icon;
    }

    public int getResetIntervalMinutes() {
        return resetIntervalMinutes;
    }

    public void setResetIntervalMinutes(int resetIntervalMinutes) {
        this.resetIntervalMinutes = resetIntervalMinutes;
    }

    public List<MineBlock> getComposition() {
        return composition;
    }

    public void setComposition(List<MineBlock> composition) {
        this.composition = composition != null ? composition : new ArrayList<>();
    }

    public MineBlock getCompositionBlock(Material material) {
        for (MineBlock block : composition) {
            if (block.getMaterial() == material) {
                return block;
            }
        }
        return null;
    }

    public String getSchematic() {
        return schematic;
    }

    public void setSchematic(String schematic) {
        this.schematic = schematic;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getPasteYOffset() {
        return pasteYOffset;
    }

    public void setPasteYOffset(int pasteYOffset) {
        this.pasteYOffset = pasteYOffset;
    }

    public String getRarity() {
        return rarity;
    }

    public void setRarity(String rarity) {
        this.rarity = rarity;
    }

    public int getExpiresInDays() {
        return expiresInDays;
    }

    public void setExpiresInDays(int expiresInDays) {
        this.expiresInDays = expiresInDays;
    }
}
