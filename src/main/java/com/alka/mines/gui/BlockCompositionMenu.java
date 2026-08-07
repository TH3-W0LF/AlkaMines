package com.alka.mines.gui;

import com.alka.mines.hook.ItemsAdderHook;
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
 * (vidro cinza), slots 0-44 sao os blocos. Arraste um item do seu inventario pra um
 * slot vazio pra adicionar - se for um bloco custom do ItemsAdder (registrado como
 * bloco de verdade, nao so um item decorativo), entra como tal; qualquer outro item
 * vira um MineBlock pelo Material. Entra sempre com %/XP zerados, configure depois
 * clicando nele. Num bloco ja colocado (cursor vazio): clique esquerdo edita %,
 * clique direito edita XP normal (vanilla), shift+esquerdo edita XP de mcMMO,
 * shift+direito remove (precisa confirmar clicando de novo em ate 5s). Preco de
 * venda NAO e configurado aqui - isso e do AlkaShop agora (preco global por
 * Material, nao por mina).
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

        // preenche TODOS os slots vazios (0-44 sem bloco + 45-53 de navegacao, exceto o
        // botao de voltar) com o filler marcado via PDC - sem a marca, um slot "vazio"
        // preenchido com vidro seria confundido com um bloco real de composicao no
        // handleClick.
        ItemStack filler = buildFiller();
        for (int i = 0; i < 54; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }

        inv.setItem(49, MenuBuilder.buildItem(Material.ARROW, ChatUtil.parse("<red><bold>Voltar"),
                List.of(ChatUtil.parse("<gray>Clique para voltar ao menu"))));

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

    private NamespacedKey compositionKeyKey() {
        return new NamespacedKey(plugin, "composition_key");
    }

    private String displayName(MineBlock block) {
        return block.isCustomBlock() ? block.getCustomBlockId() : block.getMaterial().name();
    }

    /** Bloco custom mostra o item real do ItemsAdder (se disponivel); senao cai pro
     * Material fallback (STONE) - acontece se o ItemsAdder foi removido do servidor. */
    private ItemStack resolveDisplayItem(MineBlock block) {
        if (block.isCustomBlock() && ItemsAdderHook.isEnabled()) {
            ItemStack custom = ItemsAdderHook.getCustomBlockItem(block.getCustomBlockId());
            if (custom != null) {
                return custom;
            }
        }
        return new ItemStack(block.getMaterial());
    }

    private ItemStack buildBlockItem(MineBlock block) {
        ItemStack item = resolveDisplayItem(block);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ChatUtil.parse("<white>" + displayName(block)));
        meta.lore(List.of(
                ChatUtil.parse("<gray>Chance: <yellow>" + trim(block.getWeight()) + "%"),
                ChatUtil.parse("<gray>XP normal: <yellow>" + trim(block.getNormalXp())),
                ChatUtil.parse("<gray>XP mcMMO: <yellow>" + trim(block.getMcmmoXp())
                        + (block.getMcmmoXp() <= 0 ? " <dark_gray>(usa config.yml)" : "")),
                ChatUtil.parse("<gray>Clique esquerdo: <white>editar %"),
                ChatUtil.parse("<gray>Clique direito: <white>editar XP normal"),
                ChatUtil.parse("<gray>Shift+esquerdo: <white>editar XP mcMMO"),
                ChatUtil.parse("<gray>Shift+direito: <white>remover")
        ));
        meta.getPersistentDataContainer().set(compositionKeyKey(), PersistentDataType.STRING, block.getCompositionKey());
        item.setItemMeta(meta);
        return item;
    }

    /** Chamado pelo BlockCompositionMenuListener - ja cancela o evento antes de decidir o que fazer. */
    public void handleClick(InventoryClickEvent event, String mineId) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot == 49) {
            player.closeInventory();
            if (adminMainMenu != null) {
                adminMainMenu.open(player, mineId);
            }
            return;
        }
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
            addBlock(player, mine, cursor);
            return;
        }

        if (!slotHasItem) {
            return;
        }

        String compositionKey = clicked.getItemMeta().getPersistentDataContainer()
                .get(compositionKeyKey(), PersistentDataType.STRING);
        if (compositionKey == null) {
            return;
        }

        switch (event.getClick()) {
            case LEFT -> promptField(player, mineId, compositionKey, Field.WEIGHT);
            case RIGHT -> promptField(player, mineId, compositionKey, Field.NORMAL_XP);
            case SHIFT_LEFT -> promptField(player, mineId, compositionKey, Field.MCMMO_XP);
            case SHIFT_RIGHT -> handleRemoveRequest(player, mineId, compositionKey);
            default -> {
                // duplo-clique, tecla de numero, etc. - nao fazem sentido nesse menu, ignora.
            }
        }
    }

    private void addBlock(Player player, Mine mine, ItemStack cursor) {
        String namespace = ItemsAdderHook.isEnabled() ? ItemsAdderHook.getCustomBlockNamespace(cursor) : null;

        if (namespace != null) {
            if (mine.getComposition().stream().anyMatch(b -> namespace.equals(b.getCustomBlockId()))) {
                ChatUtil.send(player, "<red>Esse bloco custom ja esta na composicao - clique nele pra editar.");
                return;
            }
            mine.getComposition().add(new MineBlock(namespace, 0.0));
        } else {
            Material material = cursor.getType();
            if (mine.getComposition().stream().anyMatch(b -> !b.isCustomBlock() && b.getMaterial() == material)) {
                ChatUtil.send(player, "<red>Esse material ja esta na composicao - clique nele pra editar.");
                return;
            }
            mine.getComposition().add(new MineBlock(material, 0.0));
        }

        mineManager.save();
        player.closeInventory();
        open(player, mine.getId());
    }

    private void promptField(Player player, String mineId, String compositionKey, Field field) {
        pending.put(player.getUniqueId(), new PendingBlockInput(mineId, compositionKey, field));
        player.closeInventory();

        String question = switch (field) {
            case WEIGHT -> "<green>Digite a nova porcentagem de aparicao (0-100).";
            case NORMAL_XP -> "<green>Digite o XP normal (vanilla) dado ao quebrar este bloco (0 = nenhum).";
            case MCMMO_XP -> "<green>Digite o XP de mcMMO (Mineracao) dado ao quebrar este bloco (0 = usa o config.yml).";
        };
        ChatUtil.send(player, question + " Digite <red>cancelar</red><green> para voltar.");
    }

    /** Shift+clique direito exige confirmar clicando de novo no mesmo bloco em ate 5s. */
    private void handleRemoveRequest(Player player, String mineId, String compositionKey) {
        PendingRemoval current = pendingRemoval.get(player.getUniqueId());
        if (current != null && current.mineId().equals(mineId) && current.compositionKey().equals(compositionKey)
                && current.expiresAtMillis() > System.currentTimeMillis()) {
            pendingRemoval.remove(player.getUniqueId());

            Mine mine = mineManager.getMine(mineId).orElse(null);
            if (mine == null) {
                return;
            }
            MineBlock block = mine.getCompositionBlock(compositionKey);
            String name = block != null ? displayName(block) : compositionKey;
            mine.getComposition().removeIf(b -> b.getCompositionKey().equals(compositionKey));
            mineManager.save();
            ChatUtil.send(player, "<red>" + name + " removido da composicao.");
            player.closeInventory();
            open(player, mineId);
            return;
        }

        pendingRemoval.put(player.getUniqueId(), new PendingRemoval(mineId, compositionKey, System.currentTimeMillis() + 5000));
        ChatUtil.send(player, "<yellow>Shift+clique direito de novo em ate 5s pra confirmar a remocao.");
    }

    /**
     * Chamado pelo BlockCompositionChatListener, ja na main thread, com o texto
     * digitado no chat. Cada campo (%, XP normal, XP mcMMO) e independente agora -
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

        MineBlock block = mine.getCompositionBlock(request.compositionKey());
        if (block == null) {
            ChatUtil.send(player, "<red>Esse bloco nao esta mais na composicao.");
            return;
        }

        switch (request.field()) {
            case WEIGHT -> handleWeightInput(player, mine, block, request, input);
            case NORMAL_XP -> handleXpInput(player, block, request, input, false);
            case MCMMO_XP -> handleXpInput(player, block, request, input, true);
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
        ChatUtil.send(player, "<green>" + displayName(block) + " agora aparece " + trim(percentage) + "% das vezes.");
        reopen(player, request.mineId());
    }

    private void handleXpInput(Player player, MineBlock block, PendingBlockInput request, String input, boolean mcmmo) {
        double xp;
        try {
            xp = Double.parseDouble(input.replace(",", "."));
        } catch (NumberFormatException e) {
            ChatUtil.send(player, "<red>Valor invalido. Digite um numero (0 ou mais), ou 'cancelar'.");
            pending.put(player.getUniqueId(), request);
            return;
        }

        if (xp < 0) {
            ChatUtil.send(player, "<red>O valor nao pode ser negativo.");
            pending.put(player.getUniqueId(), request);
            return;
        }

        if (mcmmo) {
            block.setMcmmoXp(xp);
        } else {
            block.setNormalXp(xp);
        }
        mineManager.save();
        ChatUtil.send(player, "<green>" + displayName(block) + " agora da " + trim(xp) + " XP "
                + (mcmmo ? "de mcMMO" : "normal") + " ao quebrar.");
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
        WEIGHT, NORMAL_XP, MCMMO_XP
    }

    public record PendingBlockInput(String mineId, String compositionKey, Field field) {
    }

    private record PendingRemoval(String mineId, String compositionKey, long expiresAtMillis) {
    }
}
