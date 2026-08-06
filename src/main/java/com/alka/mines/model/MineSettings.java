package com.alka.mines.model;

public class MineSettings {

    private int resetIntervalMinutes;
    private double resetPercentage;
    private boolean invisiblePlayers;
    private int minPickaxeLevel;

    public MineSettings() {
        this(0, 40.0, false, 0);
    }

    public MineSettings(int resetIntervalMinutes, double resetPercentage, boolean invisiblePlayers, int minPickaxeLevel) {
        this.resetIntervalMinutes = resetIntervalMinutes;
        this.resetPercentage = resetPercentage;
        this.invisiblePlayers = invisiblePlayers;
        this.minPickaxeLevel = minPickaxeLevel;
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
}
