package com.alka.mines.manager;

import java.util.Map;

/** Estado por jogador que nao pertence a nenhuma mina especifica - persistido via
 * {@link PlayerDataManager} (players.yml). */
public class PlayerMineData {

    private String currentMineId;
    private long blocksBroken;
    private int pickaxeLevel = 0;
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

    /**
     * Recalcula o nivel de picareta a partir de blocksBroken e dos thresholds
     * configurados (PickaxeLevelManager#getThresholds) - nivel = a maior chave cujo
     * valor (blocos exigidos) blocksBroken ja alcancou. Os thresholds precisam estar
     * em ordem crescente tanto de nivel quanto de blocos exigidos (ver config.yml
     * pickaxe-levels) - a iteracao para no primeiro nivel nao alcancado.
     *
     * @return true se o nivel mudou (sempre pra cima, ja que blocksBroken so cresce).
     */
    public boolean recalculateLevel(Map<Integer, Long> thresholds) {
        int oldLevel = this.pickaxeLevel;
        int newLevel = 0;

        for (Map.Entry<Integer, Long> entry : thresholds.entrySet()) {
            if (this.blocksBroken >= entry.getValue()) {
                newLevel = entry.getKey();
            } else {
                break;
            }
        }

        this.pickaxeLevel = newLevel;
        return oldLevel != newLevel;
    }
}
