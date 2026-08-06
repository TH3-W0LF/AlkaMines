package com.alka.mines.model;

public class MineSettings {

    private int resetIntervalMinutes;
    private double resetPercentage;
    private boolean invisiblePlayers;
    private int minPickaxeLevel;
    private double rewardPerBlock;

    public MineSettings() {
        this(0, 40.0, false, 0, 0.0);
    }

    public MineSettings(int resetIntervalMinutes, double resetPercentage, boolean invisiblePlayers, int minPickaxeLevel) {
        this(resetIntervalMinutes, resetPercentage, invisiblePlayers, minPickaxeLevel, 0.0);
    }

    public MineSettings(int resetIntervalMinutes, double resetPercentage, boolean invisiblePlayers,
                         int minPickaxeLevel, double rewardPerBlock) {
        this.resetIntervalMinutes = resetIntervalMinutes;
        this.resetPercentage = resetPercentage;
        this.invisiblePlayers = invisiblePlayers;
        this.minPickaxeLevel = minPickaxeLevel;
        this.rewardPerBlock = rewardPerBlock;
    }

    public int getResetIntervalMinutes() {
        return resetIntervalMinutes;
    }

    public void setResetIntervalMinutes(int resetIntervalMinutes) {
        this.resetIntervalMinutes = resetIntervalMinutes;
    }

    public double getResetPercentage() {
        return resetPercentage;
    }

    public void setResetPercentage(double resetPercentage) {
        this.resetPercentage = resetPercentage;
    }

    public boolean isInvisiblePlayers() {
        return invisiblePlayers;
    }

    public void setInvisiblePlayers(boolean invisiblePlayers) {
        this.invisiblePlayers = invisiblePlayers;
    }

    public int getMinPickaxeLevel() {
        return minPickaxeLevel;
    }

    public void setMinPickaxeLevel(int minPickaxeLevel) {
        this.minPickaxeLevel = minPickaxeLevel;
    }

    /** Quanto de ESCARION (AlkaEconomy) o jogador recebe por bloco quebrado nesta mina. 0 = sem recompensa. */
    public double getRewardPerBlock() {
        return rewardPerBlock;
    }

    public void setRewardPerBlock(double rewardPerBlock) {
        this.rewardPerBlock = rewardPerBlock;
    }
}
