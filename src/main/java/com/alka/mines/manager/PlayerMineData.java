package com.alka.mines.manager;

/** Estado por jogador que nao pertence a nenhuma mina especifica - so em memoria (nao persiste entre restarts). */
public class PlayerMineData {

    private String currentMineId;
    private long blocksBroken;
    private int pickaxeLevel = 1;
    private double coinBonus = 0.0;

    public String getCurrentMineId() {
        return currentMineId;
    }

    public void setCurrentMineId(String currentMineId) {
        this.currentMineId = currentMineId;
    }

    public long getBlocksBroken() {
        return blocksBroken;
    }

    public void setBlocksBroken(long blocksBroken) {
        this.blocksBroken = blocksBroken;
    }

    public void incrementBlocksBroken() {
        this.blocksBroken++;
    }

    public int getPickaxeLevel() {
        return pickaxeLevel;
    }

    public void setPickaxeLevel(int pickaxeLevel) {
        this.pickaxeLevel = pickaxeLevel;
    }

    public double getCoinBonus() {
        return coinBonus;
    }

    public void setCoinBonus(double coinBonus) {
        this.coinBonus = coinBonus;
    }
}
