package com.alka.mines.manager;

import com.alkacode.core.api.DatabaseProvider;
import com.alkacode.core.database.AbstractRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Persistencia do estado por jogador do AlkaMines no banco do AlkaCore (SQLite/MySQL
 * via {@link DatabaseProvider}). Substitui o players.yml - mesmo schema nos dois
 * bancos (AbstractRepository resolve o upsert MySQL vs SQLite). Todos os acessos sao
 * feitos na main thread (o SQLiteProvider usa pool de 1 conexao; escritas concorrentes
 * dariam "database is locked").
 */
public class PlayerMineRepository extends AbstractRepository {

    private static final Logger LOGGER = Logger.getLogger("AlkaMines");

    private static final String TABLE = "alkamines_player_mines";
    private static final String SETTINGS_TABLE = "alkamines_settings";

    public PlayerMineRepository(DatabaseProvider db) {
        super(db);
        createTables();
    }

    private void createTables() {
        try (Connection conn = db.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                    "player_uuid VARCHAR(36) NOT NULL PRIMARY KEY," +
                    "blocks_broken BIGINT NOT NULL DEFAULT 0," +
                    "pickaxe_level INTEGER NOT NULL DEFAULT 0," +
                    "coin_bonus DOUBLE NOT NULL DEFAULT 0)");
            stmt.execute("CREATE TABLE IF NOT EXISTS " + SETTINGS_TABLE + " (" +
                    "setting_key VARCHAR(64) NOT NULL PRIMARY KEY," +
                    "setting_value TEXT)");
        } catch (SQLException e) {
            LOGGER.severe("Erro ao criar tabelas do AlkaMines: " + e.getMessage());
        }
    }

    /** Carrega do banco, ou null se o jogador nunca foi salvo. */
    public PlayerMineData load(UUID uuid) {
        String sql = "SELECT blocks_broken, pickaxe_level, coin_bonus FROM " + TABLE + " WHERE player_uuid = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                PlayerMineData data = new PlayerMineData();
                data.setBlocksBroken(rs.getLong("blocks_broken"));
                data.setPickaxeLevel(rs.getInt("pickaxe_level"));
                data.setCoinBonus(rs.getDouble("coin_bonus"));
                return data;
            }
        } catch (SQLException e) {
            LOGGER.severe("Erro ao ler dados de " + uuid + ": " + e.getMessage());
            return null;
        }
    }

    /** Insere ou atualiza a linha do jogador. */
    public void save(UUID uuid, PlayerMineData data) {
        String sql = upsert(TABLE,
                new String[]{"player_uuid", "blocks_broken", "pickaxe_level", "coin_bonus"},
                new String[]{"player_uuid"});
        try {
            execute(sql, ps -> {
                ps.setString(1, uuid.toString());
                ps.setLong(2, data.getBlocksBroken());
                ps.setInt(3, data.getPickaxeLevel());
                ps.setDouble(4, data.getCoinBonus());
            });
        } catch (SQLException e) {
            LOGGER.severe("Erro ao salvar dados de " + uuid + ": " + e.getMessage());
        }
    }

    /** Top N jogadores por blocos quebrados (ordem decrescente), direto do banco. */
    public List<Map.Entry<UUID, PlayerMineData>> getTop(int limit) {
        String sql = "SELECT player_uuid, blocks_broken, pickaxe_level, coin_bonus FROM " + TABLE
                + " ORDER BY blocks_broken DESC LIMIT ?";
        List<Map.Entry<UUID, PlayerMineData>> rows = new ArrayList<>();
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    PlayerMineData data = new PlayerMineData();
                    data.setBlocksBroken(rs.getLong("blocks_broken"));
                    data.setPickaxeLevel(rs.getInt("pickaxe_level"));
                    data.setCoinBonus(rs.getDouble("coin_bonus"));
                    rows.add(Map.entry(uuid, data));
                }
            }
        } catch (SQLException e) {
            LOGGER.severe("Erro ao buscar ranking: " + e.getMessage());
        }
        return rows;
    }

    public String getSetting(String key) {
        String sql = "SELECT setting_value FROM " + SETTINGS_TABLE + " WHERE setting_key = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("setting_value");
                }
            }
        } catch (SQLException e) {
            LOGGER.severe("Erro ao ler setting '" + key + "': " + e.getMessage());
        }
        return null;
    }

    public void setSetting(String key, String value) {
        String sql = upsert(SETTINGS_TABLE, new String[]{"setting_key", "setting_value"}, new String[]{"setting_key"});
        try {
            execute(sql, ps -> {
                ps.setString(1, key);
                ps.setString(2, value);
            });
        } catch (SQLException e) {
            LOGGER.severe("Erro ao salvar setting '" + key + "': " + e.getMessage());
        }
    }
}
