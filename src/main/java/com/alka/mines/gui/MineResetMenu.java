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
        ChatUtil.send(admin, "<yellow>Digite o intervalo de reset em minutos (ex: 30). Digite <red>cancelar</red><yellow> para voltar.");
    }

    void promptPercentage(Player admin, String mineId) {
        pending.put(admin.getUniqueId(), new PendingResetInput(mineId, Field.PERCENTAGE));
        admin.closeInventory();
        ChatUtil.send(admin, "<yellow>Digite a porcentagem restante para resetar (0 a 100). Digite <red>cancelar</red><yellow> para voltar.");
    }

    void promptPermission(Player admin, String mineId) {
        pending.put(admin.getUniqueId(), new PendingResetInput(mineId, Field.PERMISSION));
        admin.closeInventory();
        ChatUtil.send(admin, "<yellow>Digite a permissao necessaria (ex: alkaminas.mina.vip). Digite <red>remover</red><yellow> para liberar ou <red>cancelar</red><yellow> para voltar.");
    }

    void promptActionbarRange(Player admin, String mineId) {
        pending.put(admin.getUniqueId(), new PendingResetInput(mineId, Field.ACTIONBAR_RANGE));
        admin.closeInventory();
        ChatUtil.send(admin, "<yellow>Digite o raio (em blocos) do ActionBar de status da mina. Digite <red>cancelar</red><yellow> para voltar.");
    }

    void promptBroadcast(Player admin, String mineId) {
        pending.put(admin.getUniqueId(), new PendingResetInput(mineId, Field.BROADCAST));
        admin.closeInventory();
        ChatUtil.send(admin, "<yellow>Digite o modo de broadcast do reset: <white>0</white> = mundo, <white>-1</white> = todos, "
                + "<white>-2</white> = silencioso, <white>N</white> = raio em blocos. Digite <red>cancelar</red><yellow> para voltar.");
    }

    void promptResetCommand(Player admin, String mineId) {
        pending.put(admin.getUniqueId(), new PendingResetInput(mineId, Field.RESET_COMMAND));
        admin.closeInventory();
        ChatUtil.send(admin, "<yellow>Digite um comando pra rodar no reset (sem /, use <white>%mine%</white> e <white>%display%</white>). "
                + "Digite <red>limpar</red><yellow> pra esvaziar a lista ou <red>cancelar</red><yellow> pra voltar.");
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

    private void handlePermissionInput(Player admin, Mine mine, PendingResetInput request, String input) {
        if (input.equalsIgnoreCase("remover")) {
            mine.getSettings().setPermission("");
            ChatUtil.send(admin, "<green>Permissao de entrada removida - mina '" + mine.getId() + "' agora e publica.");
        } else {
            mine.getSettings().setPermission(input.trim());
            ChatUtil.send(admin, "<green>Permissao de entrada da mina '" + mine.getId() + "' definida como '" + input.trim() + "'.");
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
            ChatUtil.send(admin, "<red>Valor invalido. Digite um numero inteiro >= 0, ou 'cancelar'.");
            pending.put(admin.getUniqueId(), request);
            return;
        }
        if (range < 0) {
            ChatUtil.send(admin, "<red>O raio nao pode ser negativo.");
            pending.put(admin.getUniqueId(), request);
            return;
        }
        mine.getSettings().setActionbarRange(range);
        mineManager.save();
        ChatUtil.send(admin, "<green>Raio do ActionBar definido para " + range + " blocos.");
        reopenAfter(Field.ACTIONBAR_RANGE, admin, request.mineId());
    }

    private void handleBroadcastInput(Player admin, Mine mine, PendingResetInput request, String input) {
        int mode;
        try {
            mode = Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            ChatUtil.send(admin, "<red>Valor invalido. Digite 0, -1, -2 ou um raio >= 1, ou 'cancelar'.");
            pending.put(admin.getUniqueId(), request);
            return;
        }
        mine.getSettings().setBroadcastMode(mode);
        mineManager.save();
        ChatUtil.send(admin, "<green>Broadcast do reset definido para o modo " + mode + ".");
        reopenAfter(Field.BROADCAST, admin, request.mineId());
    }

    private void handleResetCommandInput(Player admin, Mine mine, PendingResetInput request, String input) {
        if (input.equalsIgnoreCase("limpar")) {
            mine.getSettings().getResetCommands().clear();
            mineManager.save();
            ChatUtil.send(admin, "<green>Lista de comandos de reset esvaziada.");
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
        ChatUtil.send(admin, "<green>Comando adicionado: /" + command);
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
