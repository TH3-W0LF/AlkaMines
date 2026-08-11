package com.alka.mines.model;

import java.util.ArrayList;
import java.util.List;

public class MineSettings {

    private int resetIntervalMinutes;
    private double resetPercentage;
    private boolean invisiblePlayers;
    private int minPickaxeLevel;
    private String permission;
    private boolean actionbarEnabled;
    private int actionbarRange = 10;
    private int broadcastMode;
    private List<String> resetCommands = new ArrayList<>();

    public MineSettings() {
        this(0, 40.0, false, 0, "");
    }

    /** Mantido pra compatibilidade com chamadores antigos - sem permissao customizada. */
    public MineSettings(int resetIntervalMinutes, double resetPercentage, boolean invisiblePlayers, int minPickaxeLevel) {
        this(resetIntervalMinutes, resetPercentage, invisiblePlayers, minPickaxeLevel, "");
    }

    public MineSettings(int resetIntervalMinutes, double resetPercentage, boolean invisiblePlayers,
                         int minPickaxeLevel, String permission) {
        this.resetIntervalMinutes = resetIntervalMinutes;
        this.resetPercentage = resetPercentage;
        this.invisiblePlayers = invisiblePlayers;
        this.minPickaxeLevel = minPickaxeLevel;
        this.permission = permission != null ? permission : "";
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

    /** Permissao extra (alem do nivel de picareta) exigida pra entrar na mina - vazio = publica. */
    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission != null ? permission : "";
    }

    public boolean hasPermission() {
        return !permission.isBlank();
    }

    /** ActionBar de status (restantes/%) enviado a jogadores perto da mina. */
    public boolean isActionbarEnabled() {
        return actionbarEnabled;
    }

    public void setActionbarEnabled(boolean actionbarEnabled) {
        this.actionbarEnabled = actionbarEnabled;
    }

    /** Raio em blocos (ao cuboide) em que o actionbar aparece. */
    public int getActionbarRange() {
        return actionbarRange;
    }

    public void setActionbarRange(int actionbarRange) {
        this.actionbarRange = actionbarRange;
    }

    /** 0 = mundo inteiro, -1 = todos os mundos, -2 = silencioso, >=1 = raio em blocos. */
    public int getBroadcastMode() {
        return broadcastMode;
    }

    public void setBroadcastMode(int broadcastMode) {
        this.broadcastMode = broadcastMode;
    }

    /** Comandos (console) rodados quando a mina resetar - %mine% = id, %display% = nome. */
    public List<String> getResetCommands() {
        return resetCommands;
    }

    public void setResetCommands(List<String> resetCommands) {
        this.resetCommands = resetCommands != null ? resetCommands : new ArrayList<>();
    }
}
