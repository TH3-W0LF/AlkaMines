package com.alka.mines.gui;

import com.alka.mines.hologram.HologramManager;
import com.alka.mines.manager.MineManager;
import com.alka.mines.model.Mine;
import com.alka.mines.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sub-menu do AdminMainMenu pra configurar tempo/porcentagem/permissao de reset - GUI
 * (BaseGui do AlkaCore, ver {@link MineResetGui}) + prompt de chat pro valor numerico,
 * mesmo padrao do BlockCompositionMenu (nunca comando). Tambem serve de infraestrutura
 * compartilhada pro prompt de categoria e de renomear, chamados direto pelo
 * AdminMainMenuGui (sem passar pela tela de reset) - mesmo mapa de pendencia, mesmo
 * listener de chat, sem duplicar codigo.
 */
public class MineResetMenu {

    private final JavaPlugin plugin;
    private final MineManager mineManager;
    private final HologramManager hologramManager;
    private final Map<UUID, PendingResetInput> pending = new ConcurrentHashMap<>();
    private AdminMainMenu adminMainMenu;

    public MineResetMenu(JavaPlugin plugin, MineManager mineManager, HologramManager hologramManager) {
        this.plugin = plugin;
        this.mineManager = mineManager;
        this.hologramManager = hologramManager;
    }

    /** Setter em vez de construtor: MineResetMenu <-> AdminMainMenu se referenciam mutuamente. */
    public void setAdminMainMenu(AdminMainMenu adminMainMenu) {
        this.adminMainMenu = adminMainMenu;
    }

    public boolean isPending(UUID uuid) {
        return pending.containsKey(uuid);
    }

    public void open(Player admin, String mineId) {
        new MineResetGui(plugin, admin, mineManager, hologramManager, this, adminMainMenu, mineId).open();
    }

    void promptInterval(Player admin, String mineId) {
        pending.put(admin.getUniqueId(), new PendingResetInput(mineId, Field.INTERVAL));
        admin.closeInventory();
        ChatUtil.sendKey(admin, "admin.reset.prompt-interval");
    }

    void promptPercentage(Player admin, String mineId) {
        pending.put(admin.getUniqueId(), new PendingResetInput(mineId, Field.PERCENTAGE));
        admin.closeInventory();
        ChatUtil.sendKey(admin, "admin.reset.prompt-percentage");
    }

    void promptPermission(Player admin, String mineId) {
        pending.put(admin.getUniqueId(), new PendingResetInput(mineId, Field.PERMISSION));
        admin.closeInventory();
        ChatUtil.sendKey(admin, "admin.reset.prompt-permission");
    }

    void promptActionbarRange(Player admin, String mineId) {
        pending.put(admin.getUniqueId(), new PendingResetInput(mineId, Field.ACTIONBAR_RANGE));
        admin.closeInventory();
        ChatUtil.sendKey(admin, "admin.reset.prompt-actionbar");
    }

    void promptBroadcast(Player admin, String mineId) {
        pending.put(admin.getUniqueId(), new PendingResetInput(mineId, Field.BROADCAST));
        admin.closeInventory();
        ChatUtil.sendKey(admin, "admin.reset.prompt-broadcast");
    }

    void promptResetCommand(Player admin, String mineId) {
        pending.put(admin.getUniqueId(), new PendingResetInput(mineId, Field.RESET_COMMAND));
        admin.closeInventory();
        ChatUtil.sendKey(admin, "admin.reset.prompt-command");
    }

    /** Chamado direto pelo AdminMainMenu - pula esta tela, vai direto pro chat. */
    public void promptCategory(Player admin, String mineId) {
        pending.put(admin.getUniqueId(), new PendingResetInput(mineId, Field.CATEGORY));
        admin.closeInventory();
        ChatUtil.sendKey(admin, "admin.reset.prompt-category");
    }

    /** Chamado direto pelo AdminMainMenu - pula esta tela, vai direto pro chat. */
    public void promptRename(Player admin, String mineId) {
        pending.put(admin.getUniqueId(), new PendingResetInput(mineId, Field.RENAME));
        admin.closeInventory();
        ChatUtil.sendKey(admin, "admin.reset.prompt-rename");
    }

    /** Chamado pelo MineResetChatListener, ja na main thread, com o texto digitado no chat. */
    public void handleChatInput(Player admin, String input) {
        PendingResetInput request = pending.remove(admin.getUniqueId());
        if (request == null) {
            return;
        }

        if (input.equalsIgnoreCase("cancelar")) {
            ChatUtil.sendKey(admin, "generic.cancelled");
            reopenAfter(request.field(), admin, request.mineId());
            return;
        }

        Mine mine = mineManager.getMine(request.mineId()).orElse(null);
        if (mine == null) {
            return;
        }

        switch (request.field()) {
            case INTERVAL -> handleIntervalInput(admin, mine, request, input);
            case PERCENTAGE -> handlePercentageInput(admin, mine, request, input);
            case CATEGORY -> handleCategoryInput(admin, mine, request, input);
            case RENAME -> handleRenameInput(admin, mine, request, input);
            case PERMISSION -> handlePermissionInput(admin, mine, request, input);
            case ACTIONBAR_RANGE -> handleActionbarRangeInput(admin, mine, request, input);
            case BROADCAST -> handleBroadcastInput(admin, mine, request, input);
            case RESET_COMMAND -> handleResetCommandInput(admin, mine, request, input);
        }
    }

    private void handleIntervalInput(Player admin, Mine mine, PendingResetInput request, String input) {
        int minutes;
        try {
            minutes = Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            ChatUtil.sendKey(admin, "generic.invalid-number");
            pending.put(admin.getUniqueId(), request);
            return;
        }

        if (minutes <= 0) {
            ChatUtil.send(admin, "<red>O intervalo deve ser maior que 0.");
            pending.put(admin.getUniqueId(), request);
            return;
        }

        mine.getSettings().setResetIntervalMinutes(minutes);
        mineManager.save();
        ChatUtil.sendKey(admin, "admin.reset.interval-set", Map.of("minutes", String.valueOf(minutes)));
        reopenAfter(Field.INTERVAL, admin, request.mineId());
    }

    private void handlePercentageInput(Player admin, Mine mine, PendingResetInput request, String input) {
        double percentage;
        try {
            percentage = Double.parseDouble(input.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            ChatUtil.sendKey(admin, "generic.invalid-number");
            pending.put(admin.getUniqueId(), request);
            return;
        }

        if (percentage < 0 || percentage > 100) {
            ChatUtil.send(admin, "<red>A porcentagem deve estar entre 0 e 100.");
            pending.put(admin.getUniqueId(), request);
            return;
        }

        mine.getSettings().setResetPercentage(percentage);
        mineManager.save();
        ChatUtil.sendKey(admin, "admin.reset.percentage-set", Map.of("percentage", trim(percentage)));
        reopenAfter(Field.PERCENTAGE, admin, request.mineId());
    }

    private void handleCategoryInput(Player admin, Mine mine, PendingResetInput request, String input) {
        mine.setCategory(input.trim().toLowerCase());
        mineManager.save();
        ChatUtil.sendKey(admin, "admin.reset.category-set", Map.of("mine", mine.getId(), "category", mine.getCategory()));
        reopenAfter(Field.CATEGORY, admin, request.mineId());
    }

    private void handleRenameInput(Player admin, Mine mine, PendingResetInput request, String input) {
        String name = input.trim();
        if (name.isEmpty()) {
            ChatUtil.send(admin, "<red>Nome invalido.");
            pending.put(admin.getUniqueId(), request);
            return;
        }
        mine.setDisplayName(name);
        mineManager.save();
        hologramManager.updateHologram(mine);
        ChatUtil.sendKey(admin, "admin.renamed", Map.of("mine", mine.getId(), "name", name));
        reopenAfter(Field.RENAME, admin, request.mineId());
    }

    private void handlePermissionInput(Player admin, Mine mine, PendingResetInput request, String input) {
        if (input.equalsIgnoreCase("remover")) {
            mine.getSettings().setPermission("");
            ChatUtil.sendKey(admin, "admin.reset.permission-removed", Map.of("mine", mine.getId()));
        } else {
            mine.getSettings().setPermission(input.trim());
            ChatUtil.sendKey(admin, "admin.reset.permission-set", Map.of("mine", mine.getId(), "permission", input.trim()));
        }
        mineManager.save();
        reopenAfter(Field.PERMISSION, admin, request.mineId());
    }

    /** INTERVAL/PERCENTAGE/PERMISSION/ACTIONBAR_RANGE/BROADCAST/RESET_COMMAND voltam pra
     *  esta tela (de onde vieram); CATEGORY/RENAME foram chamados direto do
     *  AdminMainMenu, entao voltam pra la. */
    private void reopenAfter(Field field, Player admin, String mineId) {
        boolean fromHere = field == Field.INTERVAL || field == Field.PERCENTAGE || field == Field.PERMISSION
                || field == Field.ACTIONBAR_RANGE || field == Field.BROADCAST || field == Field.RESET_COMMAND;
        if (fromHere) {
            Bukkit.getScheduler().runTask(plugin, () -> open(admin, mineId));
        } else if (adminMainMenu != null) {
            Bukkit.getScheduler().runTask(plugin, () -> adminMainMenu.open(admin, mineId));
        }
    }

    private void handleActionbarRangeInput(Player admin, Mine mine, PendingResetInput request, String input) {
        int range;
        try {
            range = Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            ChatUtil.sendKey(admin, "generic.invalid-number");
            pending.put(admin.getUniqueId(), request);
            return;
        }
        if (range < 0) {
            ChatUtil.sendKey(admin, "generic.negative-value");
            pending.put(admin.getUniqueId(), request);
            return;
        }
        mine.getSettings().setActionbarRange(range);
        mineManager.save();
        ChatUtil.sendKey(admin, "admin.reset.actionbar-set", Map.of("range", String.valueOf(range)));
        reopenAfter(Field.ACTIONBAR_RANGE, admin, request.mineId());
    }

    private void handleBroadcastInput(Player admin, Mine mine, PendingResetInput request, String input) {
        int mode;
        try {
            mode = Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            ChatUtil.sendKey(admin, "generic.invalid-number");
            pending.put(admin.getUniqueId(), request);
            return;
        }
        mine.getSettings().setBroadcastMode(mode);
        mineManager.save();
        ChatUtil.sendKey(admin, "admin.reset.broadcast-set", Map.of("mode", String.valueOf(mode)));
        reopenAfter(Field.BROADCAST, admin, request.mineId());
    }

    private void handleResetCommandInput(Player admin, Mine mine, PendingResetInput request, String input) {
        if (input.equalsIgnoreCase("limpar")) {
            mine.getSettings().getResetCommands().clear();
            mineManager.save();
            ChatUtil.sendKey(admin, "admin.reset.commands-cleared");
            reopenAfter(Field.RESET_COMMAND, admin, request.mineId());
            return;
        }
        String command = input.trim();
        if (command.isEmpty()) {
            ChatUtil.send(admin, "<red>Comando vazio.");
            pending.put(admin.getUniqueId(), request);
            return;
        }
        mine.getSettings().getResetCommands().add(command);
        mineManager.save();
        ChatUtil.sendKey(admin, "admin.reset.command-added", Map.of("command", command));
        reopenAfter(Field.RESET_COMMAND, admin, request.mineId());
    }

    private String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format("%.1f", value);
    }

    private enum Field {
        INTERVAL, PERCENTAGE, CATEGORY, RENAME, PERMISSION, ACTIONBAR_RANGE, BROADCAST, RESET_COMMAND
    }

    public record PendingResetInput(String mineId, Field field) {
    }
}
