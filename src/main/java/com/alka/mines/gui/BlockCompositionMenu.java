package com.alka.mines.gui;

import com.alka.mines.manager.MineManager;
import com.alka.mines.model.Mine;
import com.alka.mines.model.MineBlock;
import com.alka.mines.util.ChatUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Editor de composicao de uma mina: 54 slots, ultima linha (45-53) e so navegacao
 * (vidro cinza), slots 0-44 sao os blocos. Arraste um bloco do seu inventario pra um
 * slot vazio pra adicionar (entra com % zerada, configure depois clicando nele). Num
 * bloco ja colocado (cursor vazio): clique esquerdo edita %, shift+direito remove
 * (precisa confirmar clicando de novo em ate 5s). Preco de venda NAO e configurado
 * aqui - isso e do AlkaShop agora (preco global por Material, nao por mina).
 * Nao usa MenuBuilder (que so cobre handlers estaticos por slot) porque aqui o slot
 * que recebe o item nao e conhecido de antemao - o clique e sempre interceptado
 * diretamente por {@link BlockCompositionMenuListener}.
 */
public class BlockCompositionMenu {

    private final JavaPlugin plugin;
    private final MineManager mineManager;
    private final Map<UUID, PendingBlockInput> pending = new ConcurrentHashMap<>();
    private final Map<UUID, PendingRemoval> pendingRemoval = new ConcurrentHashMap<>();
    private AdminMainMenu adminMainMenu;

    public BlockCompositionMenu(JavaPlugin plugin, MineManager mineManager) {
        this.plugin = plugin;
        this.mineManager = mineManager;
    }

    /** Setter em vez de construtor: BlockCompositionMenu <-> AdminMainMenu se referenciam mutuamente. */
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

        Inventory inv = Bukkit.createInventory(new Holder(mineId), 54,
                ChatUtil.parse("<dark_gray>Composicao: " + mine.getDisplayName()));

        int slot = 0;
        for (MineBlock block : mine.getComposition()) {
            if (slot >= 45) {
                break;
            }
            inv.setItem(slot++, buildBlockItem(block));
        }

        // preenche TODOS os slots vazios (0-44 sem bloco + 45-53 de navegacao) com o
        // filler marcado via PDC - sem a marca, um slot "vazio" preenchido com vidro
        // seria confundido com um bloco real de composicao no handleClick.
        ItemStack filler = buildFiller();
        for (int i = 0; i < 54; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }

        admin.openInventory(inv);
    }

    private ItemStack buildFiller() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.empty());
        meta.getPersistentDataContainer().set(fillerKey(), PersistentDataType.BYTE, (byte) 1);
        filler.setItemMeta(meta);
        return filler;
    }

    private boolean isFiller(ItemStack item) {
        if (item == null || item.getType() != Material.GRAY_STAINED_GLASS_PANE || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(fillerKey(), PersistentDataType.BYTE);
    }

    private NamespacedKey fillerKey() {
        return new NamespacedKey(plugin, "composition_filler");
    }

    private ItemStack buildBlockItem(MineBlock block) {
        ItemStack item = new ItemStack(block.getMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ChatUtil.parse("<white>" + block.getMaterial().name()));
        meta.lore(List.of(
                ChatUtil.parse("<gray>Chance: <yellow>" + trim(block.getWeight()) + "%"),
                ChatUtil.parse("<gray>Clique esquerdo: <white>editar %"),
                ChatUtil.parse("<gray>Shift+direito: <white>remover")
        ));
        item.setItemMeta(meta);
        return item;
    }

    /** Chamado pelo BlockCompositionMenuListener - ja cancela o evento antes de decidir o que fazer. */
    public void handleClick(InventoryClickEvent event, String mineId) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 45) {
            return;
        }

        Mine mine = mineManager.getMine(mineId).orElse(null);
        if (mine == null) {
            return;
        }

        ItemStack cursor = event.getCursor();
        ItemStack clicked = event.getCurrentItem();
        boolean cursorHasItem = cursor != null && !cursor.getType().isAir();
        boolean slotHasItem = clicked != null && !clicked.getType().isAir() && !isFiller(clicked);

        if (cursorHasItem && !slotHasItem) {
            addBlock(player, mine, cursor.getType());
            return;
        }

        if (!slotHasItem) {
            return;
        }

        Material material = clicked.getType();

        switch (event.getClick()) {
            case LEFT -> promptField(player, mineId, material, Field.WEIGHT);
            case SHIFT_RIGHT -> handleRemoveRequest(player, mineId, material);
            default -> {
                // duplo-clique, tecla de numero, etc. - nao fazem sentido nesse menu, ignora.
            }
        }
    }

    private void addBlock(Player player, Mine mine, Material material) {
        if (mine.getComposition().stream().anyMatch(b -> b.getMaterial() == material)) {
            ChatUtil.send(player, "<red>Esse material ja esta na composicao - clique nele pra editar.");
            return;
        }
        mine.getComposition().add(new MineBlock(material, 0.0));
        mineManager.save();
        player.closeInventory();
        open(player, mine.getId());
    }

    private void promptField(Player player, String mineId, Material material, Field field) {
        pending.put(player.getUniqueId(), new PendingBlockInput(mineId, material, field));
        player.closeInventory();

        String question = "<green>Digite a nova porcentagem de aparicao (0-100) do bloco <white>" + material.name() + "</white><green>.";
        ChatUtil.send(player, question + " Digite <red>cancelar</red><green> para voltar.");
    }

    /** Shift+clique direito exige confirmar clicando de novo no mesmo bloco em ate 5s. */
    private void handleRemoveRequest(Player player, String mineId, Material material) {
        PendingRemoval current = pendingRemoval.get(player.getUniqueId());
        if (current != null && current.mineId().equals(mineId) && current.material() == material
                && current.expiresAtMillis() > System.currentTimeMillis()) {
            pendingRemoval.remove(player.getUniqueId());

            Mine mine = mineManager.getMine(mineId).orElse(null);
            if (mine == null) {
                return;
            }
            mine.getComposition().removeIf(b -> b.getMaterial() == material);
            mineManager.save();
            ChatUtil.send(player, "<red>" + material.name() + " removido da composicao.");
            player.closeInventory();
            open(player, mineId);
            return;
        }

        pendingRemoval.put(player.getUniqueId(), new PendingRemoval(mineId, material, System.currentTimeMillis() + 5000));
        ChatUtil.send(player, "<yellow>Shift+clique direito de novo em ate 5s pra confirmar a remocao de <white>"
                + material.name() + "</white><yellow>.");
    }

    /**
     * Chamado pelo BlockCompositionChatListener, ja na main thread, com o texto
     * digitado no chat. Cada campo (%, preco, recompensa) e independente agora -
     * edita direto o MineBlock ja existente na composicao, sem reconstruir o objeto.
     */
    public void handleChatInput(Player player, String input) {
        PendingBlockInput request = pending.remove(player.getUniqueId());
        if (request == null) {
            return;
        }

        if (input.equalsIgnoreCase("cancelar")) {
            ChatUtil.send(player, "<yellow>Operacao cancelada.");
            reopen(player, request.mineId());
            return;
        }

        Mine mine = mineManager.getMine(request.mineId()).orElse(null);
        if (mine == null) {
            return;
        }

        MineBlock block = mine.getCompositionBlock(request.material());
        if (block == null) {
            ChatUtil.send(player, "<red>Esse bloco nao esta mais na composicao.");
            return;
        }

        switch (request.field()) {
            case WEIGHT -> handleWeightInput(player, mine, block, request, input);
        }
    }

    private void handleWeightInput(Player player, Mine mine, MineBlock block, PendingBlockInput request, String input) {
        double percentage;
        try {
            percentage = Double.parseDouble(input.replace(",", "."));
        } catch (NumberFormatException e) {
            ChatUtil.send(player, "<red>Valor invalido. Digite um numero de 0 a 100, ou 'cancelar'.");
            pending.put(player.getUniqueId(), request);
            return;
        }

        if (percentage < 0 || percentage > 100) {
            ChatUtil.send(player, "<red>A porcentagem deve estar entre 0 e 100.");
            pending.put(player.getUniqueId(), request);
            return;
        }

        double othersTotal = mine.getComposition().stream()
                .filter(b -> b != block)
                .mapToDouble(MineBlock::getWeight)
                .sum();

        if (othersTotal + percentage > 100.0) {
            ChatUtil.send(player, "<red>Isso ultrapassa 100% no total (outros blocos somam " + trim(othersTotal)
                    + "%). Digite um valor menor.");
            pending.put(player.getUniqueId(), request);
            return;
        }

        block.setWeight(percentage);
        mineManager.save();
        ChatUtil.send(player, "<green>" + block.getMaterial().name() + " agora aparece " + trim(percentage) + "% das vezes.");
        reopen(player, request.mineId());
    }

    private void reopen(Player player, String mineId) {
        Bukkit.getScheduler().runTask(plugin, () -> open(player, mineId));
    }

    private String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format("%.1f", value);
    }

    public record Holder(String mineId) implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() {
            return null;
        }
    }

    private enum Field {
        WEIGHT
    }

    public record PendingBlockInput(String mineId, Material material, Field field) {
    }

    private record PendingRemoval(String mineId, Material material, long expiresAtMillis) {
    }
}
