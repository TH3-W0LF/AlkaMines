package com.alka.mines.gui;

import com.alka.mines.hologram.HologramManager;
import com.alka.mines.manager.MineManager;
import com.alka.mines.model.Mine;
import com.alka.mines.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sub-menu do AdminMainMenu pra configurar tempo/porcentagem de reset - GUI + prompt
 * de chat pro valor numerico, mesmo padrao do BlockCompositionMenu (nunca comando).
 * Tambem serve de infraestrutura compartilhada pro prompt de categoria e de renomear,
 * chamados direto pelo AdminMainMenu (sem passar pela tela de reset) - mesmo mapa de
 * pendencia, mesmo listener de chat, sem duplicar codigo.
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
        Mine mine = mineManager.getMine(mineId).orElse(null);
        if (mine == null) {
            ChatUtil.send(admin, "<red>Mina nao encontrada: " + mineId);
            return;
        }

        Inventory inv = new MenuBuilder(27, ChatUtil.parse("<dark_gray>Reset: " + mine.getDisplayName()))
                .item(11, Material.CLOCK, ChatUtil.parse("<gold>Tempo de Reset"),
                        List.of(
                                ChatUtil.parse("<gray>Atual: <white>" + mine.getSettings().getResetIntervalMinutes() + " min"),
                                ChatUtil.parse("<yellow>Clique para alterar")
                        ),
                        event -> promptInterval(admin, mineId))
                .item(15, Material.COMPARATOR, ChatUtil.parse("<aqua>Porcentagem Restante"),
                        List.of(
                                ChatUtil.parse("<gray>Atual: <white>" + trim(mine.getSettings().getResetPercentage()) + "%"),
                                ChatUtil.parse("<yellow>Clique para alterar")
                        ),
                        event -> promptPercentage(admin, mineId))
                .item(22, Material.ARROW, ChatUtil.parse("<white>Voltar"), List.of(),
                        event -> {
                            admin.closeInventory();
                            if (adminMainMenu != null) {
                                adminMainMenu.open(admin, mineId);
                            }
                        })
                .build();

        admin.openInventory(inv);
    }

    private void promptInterval(Player admin, String mineId) {
        pending.put(admin.getUniqueId(), new PendingResetInput(mineId, Field.INTERVAL));
        admin.closeInventory();
        ChatUtil.send(admin, "<yellow>Digite o intervalo de reset em minutos (ex: 30). Digite <red>cancelar</red><yellow> para voltar.");
    }

    private void promptPercentage(Player admin, String mineId) {
        pending.put(admin.getUniqueId(), new PendingResetInput(mineId, Field.PERCENTAGE));
        admin.closeInventory();
        ChatUtil.send(admin, "<yellow>Digite a porcentagem restante para resetar (0 a 100). Digite <red>cancelar</red><yellow> para voltar.");
    }

    /** Chamado direto pelo AdminMainMenu - pula esta tela, vai direto pro chat. */
    public void promptCategory(Player admin, String mineId) {
        pending.put(admin.getUniqueId(), new PendingResetInput(mineId, Field.CATEGORY));
        admin.closeInventory();
        ChatUtil.send(admin, "<yellow>Digite o nome da categoria desta mina (ex: vip, pvp, ranking). Digite <red>cancelar</red><yellow> para voltar.");
    }

    /** Chamado direto pelo AdminMainMenu - pula esta tela, vai direto pro chat. */
    public void promptRename(Player admin, String mineId) {
        pending.put(admin.getUniqueId(), new PendingResetInput(mineId, Field.RENAME));
        admin.closeInventory();
        ChatUtil.send(admin, "<yellow>Digite o novo nome de exibicao desta mina. Digite <red>cancelar</red><yellow> para voltar.");
    }

    /** Chamado pelo MineResetChatListener, ja na main thread, com o texto digitado no chat. */
    public void handleChatInput(Player admin, String input) {
        PendingResetInput request = pending.remove(admin.getUniqueId());
        if (request == null) {
            return;
        }

        if (input.equalsIgnoreCase("cancelar")) {
            ChatUtil.send(admin, "<yellow>Operacao cancelada.");
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
        }
    }

    private void handleIntervalInput(Player admin, Mine mine, PendingResetInput request, String input) {
        int minutes;
        try {
            minutes = Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            ChatUtil.send(admin, "<red>Valor invalido. Digite um numero inteiro maior que 0, ou 'cancelar'.");
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
        ChatUtil.send(admin, "<green>Intervalo de reset definido para " + minutes + " minutos.");
        reopenAfter(Field.INTERVAL, admin, request.mineId());
    }

    private void handlePercentageInput(Player admin, Mine mine, PendingResetInput request, String input) {
        double percentage;
        try {
            percentage = Double.parseDouble(input.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            ChatUtil.send(admin, "<red>Valor invalido. Digite um numero de 0 a 100, ou 'cancelar'.");
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
        ChatUtil.send(admin, "<green>Porcentagem de reset definida para " + trim(percentage) + "%.");
        reopenAfter(Field.PERCENTAGE, admin, request.mineId());
    }

    private void handleCategoryInput(Player admin, Mine mine, PendingResetInput request, String input) {
        mine.setCategory(input.trim().toLowerCase());
        mineManager.save();
        ChatUtil.send(admin, "<green>Categoria da mina '" + mine.getId() + "' definida como '" + mine.getCategory() + "'.");
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
        ChatUtil.send(admin, "<green>Mina '" + mine.getId() + "' renomeada para '" + name + "'.");
        reopenAfter(Field.RENAME, admin, request.mineId());
    }

    /** INTERVAL/PERCENTAGE voltam pra esta tela (de onde vieram); CATEGORY/RENAME foram
     *  chamados direto do AdminMainMenu, entao voltam pra la. */
    private void reopenAfter(Field field, Player admin, String mineId) {
        if (field == Field.INTERVAL || field == Field.PERCENTAGE) {
            Bukkit.getScheduler().runTask(plugin, () -> open(admin, mineId));
        } else if (adminMainMenu != null) {
            Bukkit.getScheduler().runTask(plugin, () -> adminMainMenu.open(admin, mineId));
        }
    }

    private String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format("%.1f", value);
    }

    private enum Field {
        INTERVAL, PERCENTAGE, CATEGORY, RENAME
    }

    public record PendingResetInput(String mineId, Field field) {
    }
}
