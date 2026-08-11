package com.alka.mines.gui;

import com.alka.mines.manager.MineManager;
import com.alka.mines.model.Mine;
import com.alka.mines.model.MineReward;
import com.alka.mines.util.ChatUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Editor de Random Rewards de uma mina (estilo AxMines, reimplementado): chance por
 * clique, itens e blocos arrastados do inventario, comandos via prompt de chat, e
 * prevent-drops. NAO usa BaseGui porque os submenus de itens/blocos precisam acessar o
 * inventario inferior (drag) - Holder + listener proprio (ver MineRewardsListener).
 */
public class MineRewardsMenu {

    private final JavaPlugin plugin;
    private final MineManager mineManager;
    private final Map<UUID, PendingRewardInput> pending = new ConcurrentHashMap<>();
    private AdminMainMenu adminMainMenu;

    public MineRewardsMenu(JavaPlugin plugin, MineManager mineManager) {
        this.plugin = plugin;
        this.mineManager = mineManager;
    }

    public void setAdminMainMenu(AdminMainMenu adminMainMenu) {
        this.adminMainMenu = adminMainMenu;
    }

    public boolean isPending(UUID uuid) {
        return pending.containsKey(uuid);
    }

    public void open(Player admin, String mineId) {
        open(admin, mineId, Screen.LIST, 0, 0);
    }

    private void open(Player admin, String mineId, Screen screen, int rewardIndex, int page) {
        Mine mine = mineManager.getMine(mineId).orElse(null);
        if (mine == null) {
            ChatUtil.send(admin, "<red>Mina nao encontrada: " + mineId);
            return;
        }

        Holder holder = new Holder(mineId, screen, rewardIndex, page);
        Inventory inv = Bukkit.createInventory(holder, 45, ChatUtil.parse(title(screen, mine)));
        holder.inventory = inv;

        switch (screen) {
            case LIST -> renderList(admin, mine, inv, page);
            case EDITOR -> renderEditor(admin, mine, inv, rewardIndex);
            case ITEMS, BLOCKS -> renderMaterialList(admin, mine, inv, screen, rewardIndex, page);
            case COMMANDS -> renderCommands(admin, mine, inv, rewardIndex, page);
        }
        admin.openInventory(inv);
    }

    private String title(Screen screen, Mine mine) {
        return switch (screen) {
            case LIST -> "<dark_gray>Recompensas: " + mine.getDisplayName();
            case EDITOR -> "<dark_gray>Editar Recompensa";
            case ITEMS -> "<dark_gray>Itens da Recompensa";
            case BLOCKS -> "<dark_gray>Blocos da Recompensa";
            case COMMANDS -> "<dark_gray>Comandos da Recompensa";
        };
    }

    private void renderList(Player admin, Mine mine, Inventory inv, int page) {
        fillEmpty(inv);
        nav(inv, 36, () -> {
            if (adminMainMenu != null) {
                adminMainMenu.open(admin, mine.getId());
            }
        }, 38, 42, page);

        set(inv, 40, item(Material.PAPER, "<green><bold>Adicionar Recompensa",
                "<gray>Clique pra criar uma nova", "<gray>recompensa (chance 1)"), event -> {
            MineReward reward = new MineReward();
            mine.getRewards().add(reward);
            mineManager.save();
            open(admin, mine.getId(), Screen.EDITOR, mine.getRewards().size() - 1, 0);
        });

        List<MineReward> rewards = mine.getRewards();
        int start = page * 28;
        int slot = 0;
        for (int i = start; i < Math.min(start + 28, rewards.size()); i++) {
            if (slot >= 35) {
                break;
            }
            MineReward reward = rewards.get(i);
            int index = i;
            set(inv, slot++, item(Material.CHEST,
                    "<gold><bold>Recompensa #" + (i + 1),
                    "<gray>Chance: <yellow>" + trim(reward.getChance()) + "%",
                    "<gray>Blocos: <white>" + (reward.getBlocks().isEmpty()
                            ? "qualquer" : reward.getBlocks().size() + " tipo(s)"),
                    "<gray>Itens: <white>" + reward.getItems().size(),
                    "<gray>Comandos: <white>" + reward.getCommands().size(),
                    "<gray>Prevent drops: <white>" + (reward.isPreventDrops() ? "sim" : "nao"),
                    "",
                    "<yellow>Clique pra editar  <dark_red>Soltar (Q) pra remover"), event -> {
                if (event.getClick() == ClickType.DROP) {
                    mine.getRewards().remove(index);
                    mineManager.save();
                    open(admin, mine.getId(), Screen.LIST, 0, page);
                } else {
                    open(admin, mine.getId(), Screen.EDITOR, index, 0);
                }
            });
        }
    }

    private void renderEditor(Player admin, Mine mine, Inventory inv, int rewardIndex) {
        fillEmpty(inv);
        nav(inv, 36, () -> open(admin, mine.getId()), -1, -1, 0);

        if (rewardIndex < 0 || rewardIndex >= mine.getRewards().size()) {
            open(admin, mine.getId());
            return;
        }
        MineReward reward = mine.getRewards().get(rewardIndex);

        set(inv, 11, item(Material.GOLD_BLOCK, "<aqua><bold>Chance",
                "<gray>Atual: <yellow>" + trim(reward.getChance()) + "%",
                "",
                "<green>Esquerdo: <white>+1  <green>Shift: <white>+10",
                "<red>Direito: <white>-1  <red>Shift: <white>-10"), event -> {
            double delta = event.isLeftClick() ? (event.isShiftClick() ? 10 : 1)
                    : event.isRightClick() ? (event.isShiftClick() ? -10 : -1) : 0;
            if (delta != 0) {
                reward.setChance(Math.max(0, reward.getChance() + delta));
                mineManager.save();
                open(admin, mine.getId(), Screen.EDITOR, rewardIndex, 0);
            }
        });
        set(inv, 12, item(Material.IRON_INGOT, "<aqua><bold>Itens",
                "<gray>Atual: <white>" + reward.getItems().size() + " item(ns)",
                "<gray>Arraste itens e solte nos slots", "<gray>vazios pra adicionar; soltar (Q) remove"),
                event -> open(admin, mine.getId(), Screen.ITEMS, rewardIndex, 0));
        set(inv, 13, item(Material.COMMAND_BLOCK, "<aqua><bold>Comandos",
                "<gray>Atual: <white>" + reward.getCommands().size() + " comando(s)",
                "<gray>Placeholder: <white><player>",
                "<yellow>Clique pra gerenciar"),
                event -> open(admin, mine.getId(), Screen.COMMANDS, rewardIndex, 0));
        set(inv, 14, item(Material.STONE, "<aqua><bold>Blocos",
                "<gray>Atual: <white>" + (reward.getBlocks().isEmpty()
                        ? "qualquer bloco" : reward.getBlocks().size() + " tipo(s)"),
                "<gray>Vazio = dispara em qualquer bloco",
                "<yellow>Clique pra gerenciar"),
                event -> open(admin, mine.getId(), Screen.BLOCKS, rewardIndex, 0));
        set(inv, 15, item(reward.isPreventDrops() ? Material.REDSTONE_BLOCK : Material.REDSTONE_TORCH,
                "<light_purple><bold>Prevent drops: " + (reward.isPreventDrops() ? "SIM" : "nao"),
                "<gray>Quando SIM, o drop normal do bloco", "<gray>nao e entregue se esta recompensa disparar.",
                "", "<yellow>Clique pra alternar"),
                event -> {
                    reward.setPreventDrops(!reward.isPreventDrops());
                    mineManager.save();
                    open(admin, mine.getId(), Screen.EDITOR, rewardIndex, 0);
                });
    }

    private void renderMaterialList(Player admin, Mine mine, Inventory inv, Screen screen, int rewardIndex, int page) {
        fillNav(inv);
        nav(inv, 36, () -> open(admin, mine.getId(), Screen.EDITOR, rewardIndex, 0), 38, 42, page);

        if (rewardIndex < 0 || rewardIndex >= mine.getRewards().size()) {
            open(admin, mine.getId());
            return;
        }
        MineReward reward = mine.getRewards().get(rewardIndex);

        set(inv, 40, item(Material.HOPPER, "<green><bold>Adicionar",
                "<gray>Arraste do seu inventario pra um", "<gray>slot vazio acima"), null);

        int start = page * 28;
        int slot = 0;
        if (screen == Screen.ITEMS) {
            List<ItemStack> items = reward.getItems();
            for (int i = start; i < Math.min(start + 28, items.size()); i++) {
                if (slot >= 35) {
                    break;
                }
                ItemStack stack = items.get(i).clone();
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    meta.lore(List.of(ChatUtil.parse("<dark_red>Soltar (Q) pra remover")));
                    stack.setItemMeta(meta);
                }
                int index = i;
                set(inv, slot++, stack, event -> {
                    if (event.getClick() == ClickType.DROP) {
                        items.remove(index);
                        mineManager.save();
                        open(admin, mine.getId(), screen, rewardIndex, page);
                    }
                });
            }
        } else {
            List<Material> list = reward.getBlocks();
            for (int i = start; i < Math.min(start + 28, list.size()); i++) {
                if (slot >= 35) {
                    break;
                }
                Material mat = list.get(i);
                int index = i;
                set(inv, slot++, item(mat, "<white>" + mat.name(), "<dark_red>Soltar (Q) pra remover"), event -> {
                    if (event.getClick() == ClickType.DROP) {
                        list.remove(index);
                        mineManager.save();
                        open(admin, mine.getId(), screen, rewardIndex, page);
                    }
                });
            }
        }
    }

    private void renderCommands(Player admin, Mine mine, Inventory inv, int rewardIndex, int page) {
        fillNav(inv);
        nav(inv, 36, () -> open(admin, mine.getId(), Screen.EDITOR, rewardIndex, 0), 38, 42, page);

        if (rewardIndex < 0 || rewardIndex >= mine.getRewards().size()) {
            open(admin, mine.getId());
            return;
        }
        MineReward reward = mine.getRewards().get(rewardIndex);

        set(inv, 40, item(Material.PAPER, "<green><bold>Adicionar Comando",
                "<gray>Digite o comando no chat (use <white><player>"), event -> {
            pending.put(admin.getUniqueId(), new PendingRewardInput(mine.getId(), rewardIndex));
            admin.closeInventory();
            ChatUtil.send(admin, "<yellow>Digite o comando (sem /, use <white><player></white><yellow>). "
                    + "Digite <red>cancelar</red><yellow> pra voltar.");
        });

        int start = page * 28;
        int slot = 0;
        for (int i = start; i < Math.min(start + 28, reward.getCommands().size()); i++) {
            if (slot >= 35) {
                break;
            }
            String command = reward.getCommands().get(i);
            int index = i;
            set(inv, slot++, item(Material.PAPER, "<white>/" + command, "<dark_red>Soltar (Q) pra remover"), event -> {
                if (event.getClick() == ClickType.DROP) {
                    reward.getCommands().remove(index);
                    mineManager.save();
                    open(admin, mine.getId(), Screen.COMMANDS, rewardIndex, page);
                }
            });
        }
    }

    /** Chamado pelo MineRewardsChatListener com o comando digitado. */
    public void handleChatInput(Player player, String input) {
        PendingRewardInput request = pending.remove(player.getUniqueId());
        if (request == null) {
            return;
        }
        if (input.equalsIgnoreCase("cancelar")) {
            ChatUtil.send(player, "<yellow>Operacao cancelada.");
            open(player, request.mineId(), Screen.COMMANDS, request.rewardIndex(), 0);
            return;
        }

        Mine mine = mineManager.getMine(request.mineId()).orElse(null);
        if (mine == null || request.rewardIndex() < 0 || request.rewardIndex() >= mine.getRewards().size()) {
            return;
        }
        MineReward reward = mine.getRewards().get(request.rewardIndex());
        reward.getCommands().add(input.trim());
        mineManager.save();
        ChatUtil.send(player, "<green>Comando adicionado: /" + input.trim());
        open(player, request.mineId(), Screen.COMMANDS, request.rewardIndex(), 0);
    }

    /** Chamado pelo MineRewardsListener, ja cancelado - roteia drag-add e acoes por slot. */
    public void handleClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Mine mine = mineManager.getMine(holder.mineId()).orElse(null);
        if (mine == null) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot == 49) {
            player.closeInventory();
            if (holder.screen() == Screen.LIST && adminMainMenu != null) {
                adminMainMenu.open(player, holder.mineId());
            } else {
                open(player, holder.mineId(), Screen.LIST, 0, 0);
            }
            return;
        }
        if (slot < 0 || slot >= 45) {
            return;
        }

        ItemStack cursor = event.getCursor();
        boolean cursorHasItem = cursor != null && !cursor.getType().isAir();
        boolean slotHasItem = event.getCurrentItem() != null && !isFiller(event.getCurrentItem())
                && event.getCurrentItem().getType() != Material.AIR;

        // drag-add de item/bloco em slot vazio dos submenus ITEMS/BLOCKS
        if (cursorHasItem && !slotHasItem && (holder.screen() == Screen.ITEMS || holder.screen() == Screen.BLOCKS)) {
            if (holder.rewardIndex() >= 0 && holder.rewardIndex() < mine.getRewards().size()) {
                MineReward reward = mine.getRewards().get(holder.rewardIndex());
                if (holder.screen() == Screen.ITEMS) {
                    reward.getItems().add(cursor.clone());
                } else {
                    reward.getBlocks().add(cursor.getType());
                }
                mineManager.save();
                player.closeInventory();
                open(player, holder.mineId(), holder.screen(), holder.rewardIndex(), holder.page());
            }
            return;
        }

        Consumer<InventoryClickEvent> action = holder.actions.get(slot);
        if (action != null) {
            action.accept(event);
        }
    }

    private void nav(Inventory inv, int backSlot, Runnable back, int prevSlot, int nextSlot, int page) {
        if (back != null) {
            set(inv, backSlot, item(Material.TIPPED_ARROW, "<red><bold>Voltar", "<gray>Clique pra voltar"),
                    event -> back.run());
        }
        if (prevSlot >= 0) {
            set(inv, prevSlot, item(Material.ARROW, "<yellow><bold>Anterior", "<gray>Pagina " + page),
                    event -> reopen(inv, page - 1));
        }
        if (nextSlot >= 0) {
            set(inv, nextSlot, item(Material.ARROW, "<yellow><bold>Proxima", "<gray>Pagina " + (page + 2)),
                    event -> reopen(inv, page + 1));
        }
    }

    private void reopen(Inventory inv, int newPage) {
        if (!(inv.getHolder() instanceof Holder holder)) {
            return;
        }
        open(playerFrom(inv), holder.mineId(), holder.screen(), holder.rewardIndex(), Math.max(0, newPage));
    }

    private Player playerFrom(Inventory inv) {
        return inv.getViewers().isEmpty() ? null : (Player) inv.getViewers().get(0);
    }

    private void fillEmpty(Inventory inv) {
        for (int i = 0; i < 45; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler());
            }
        }
    }

    /** Preenche so a linha de navegacao (36-44) - usada nos submenus de itens/blocos/
     * comandos, onde os slots 0-35 ficam VAZIOS de proposito como alvos de drop. */
    private void fillNav(Inventory inv) {
        for (int i = 36; i < 45; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler());
            }
        }
    }

    private ItemStack filler() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.empty());
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "rewards_filler"), PersistentDataType.BYTE, (byte) 1);
        filler.setItemMeta(meta);
        return filler;
    }

    private boolean isFiller(ItemStack item) {
        if (item == null || item.getType() != Material.GRAY_STAINED_GLASS_PANE || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin, "rewards_filler"), PersistentDataType.BYTE);
    }

    private static void set(Inventory inv, int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inv.setItem(slot, item);
        if (action != null && inv.getHolder() instanceof Holder holder) {
            holder.actions.put(slot, action);
        }
    }

    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ChatUtil.parse(name));
        List<Component> loreList = new ArrayList<>();
        for (String line : lore) {
            loreList.add(ChatUtil.parse(line));
        }
        meta.lore(loreList);
        item.setItemMeta(meta);
        return item;
    }

    private String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format("%.1f", value);
    }

    enum Screen {
        LIST, EDITOR, ITEMS, BLOCKS, COMMANDS
    }

    /** Holder com acoes por slot (escritas pelo render) + estado de navegacao. */
    public static class Holder implements InventoryHolder {
        private final String mineId;
        private final Screen screen;
        private final int rewardIndex;
        private final int page;
        private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
        private Inventory inventory;

        public Holder(String mineId, Screen screen, int rewardIndex, int page) {
            this.mineId = mineId;
            this.screen = screen;
            this.rewardIndex = rewardIndex;
            this.page = page;
        }

        public String mineId() {
            return mineId;
        }

        public Screen screen() {
            return screen;
        }

        public int rewardIndex() {
            return rewardIndex;
        }

        public int page() {
            return page;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public record PendingRewardInput(String mineId, int rewardIndex) {
    }
}
