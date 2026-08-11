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
import org.bukkit.event.inventory.ClickType;
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
 * Editor de composicao de uma mina (estilo AxMines, reimplementado): 54 slots, ultima
 * linha e navegacao. NAO usa BaseGui de proposito: o GuiListener do AlkaCore cancela
 * cliques no inventario inferior, e aqui o admin precisa pegar item do proprio
 * inventario pra "arrastar" pra composicao. Usa Holder + listener proprio.
 *
 * Modo composicao (padrao): arrasta item do inventario pra um slot vazio pra adicionar
 * (chance 10, RELATIVA - nao precisa somar 100); esquerdo +1, shift+esquerdo +10,
 * direito -1, shift+direito -10; soltar o item (tecla Q) remove o bloco.
 * Modo XP (botao "XP por bloco"): esquerdo edita XP normal (vanilla), shift+esquerdo
 * edita XP de mcMMO - via prompt de chat, igual antes.
 */
public class BlockCompositionMenu {

    private final JavaPlugin plugin;
    private final MineManager mineManager;
    private final Map<UUID, PendingBlockInput> pending = new ConcurrentHashMap<>();
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
        open(admin, mineId, false);
    }

    public void openXp(Player admin, String mineId) {
        open(admin, mineId, true);
    }

    private void open(Player admin, String mineId, boolean xpMode) {
        Mine mine = mineManager.getMine(mineId).orElse(null);
        if (mine == null) {
            ChatUtil.send(admin, "<red>Mina nao encontrada: " + mineId);
            return;
        }

        String title = xpMode
                ? "<dark_gray>XP por bloco: " + mine.getDisplayName()
                : "<dark_gray>Composicao: " + mine.getDisplayName();
        Inventory inv = Bukkit.createInventory(new Holder(mineId, xpMode), 54, ChatUtil.parse(title));

        int slot = 0;
        for (MineBlock block : mine.getComposition()) {
            if (slot >= 45) {
                break;
            }
            inv.setItem(slot++, xpMode ? buildXpItem(block) : buildWeightItem(block));
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

        inv.setItem(45, buildItem(Material.EXPERIENCE_BOTTLE,
                ChatUtil.parse(xpMode ? "<aqua><bold>Modo Chance" : "<aqua><bold>XP por Bloco"),
                List.of(ChatUtil.parse(xpMode
                        ? "<gray>Voltar ao modo de chance"
                        : "<gray>Editar XP normal / mcMMO por bloco"))));
        inv.setItem(49, buildItem(Material.ARROW, ChatUtil.parse("<red><bold>Voltar"),
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

    private ItemStack buildWeightItem(MineBlock block) {
        ItemStack item = new ItemStack(block.getMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ChatUtil.parse("<white>" + block.getMaterial().name()));
        meta.lore(List.of(
                ChatUtil.parse("<gray>Chance: <yellow>" + trim(block.getWeight())
                        + " <dark_gray>(relativa, nao precisa somar 100)"),
                ChatUtil.parse("<gray>XP normal: <yellow>" + trim(block.getNormalXp())
                        + " <gray>| XP mcMMO: <yellow>" + trim(block.getMcmmoXp())),
                ChatUtil.parse(""),
                ChatUtil.parse("<green>Esquerdo: <white>+1"),
                ChatUtil.parse("<green>Shift+Esquerdo: <white>+10"),
                ChatUtil.parse("<red>Direito: <white>-1"),
                ChatUtil.parse("<red>Shift+Direito: <white>-10"),
                ChatUtil.parse("<dark_red>Soltar (Q): <white>remover")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildXpItem(MineBlock block) {
        ItemStack item = new ItemStack(block.getMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ChatUtil.parse("<white>" + block.getMaterial().name()));
        meta.lore(List.of(
                ChatUtil.parse("<gray>Chance: <yellow>" + trim(block.getWeight())),
                ChatUtil.parse("<gray>XP normal: <yellow>" + trim(block.getNormalXp())),
                ChatUtil.parse("<gray>XP mcMMO: <yellow>" + trim(block.getMcmmoXp())
                        + (block.isMcmmoXpConfigured()
                        ? (block.getMcmmoXp() <= 0 ? " <dark_gray>(desligado)" : "")
                        : " <dark_gray>(usa config.yml)")),
                ChatUtil.parse(""),
                ChatUtil.parse("<green>Esquerdo: <white>editar XP normal"),
                ChatUtil.parse("<green>Shift+Esquerdo: <white>editar XP mcMMO"),
                ChatUtil.parse("<dark_red>Soltar (Q): <white>remover")
        ));
        item.setItemMeta(meta);
        return item;
    }

    /** Chamado pelo BlockCompositionMenuListener - ja cancela o evento antes de decidir o que fazer. */
    public void handleClick(InventoryClickEvent event, String mineId, boolean xpMode) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot == 49) {
            player.closeInventory();
            if (xpMode) {
                open(player, mineId);
            } else if (adminMainMenu != null) {
                adminMainMenu.open(player, mineId);
            }
            return;
        }
        if (slot == 45) {
            if (xpMode) {
                open(player, mineId);
            } else {
                openXp(player, mineId);
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
            addBlock(player, mine, cursor.getType());
            return;
        }
        if (!slotHasItem) {
            return;
        }

        Material material = clicked.getType();

        if (event.getClick() == ClickType.DROP) {
            removeBlock(player, mine, material);
            return;
        }

        if (xpMode) {
            if (event.isLeftClick()) {
                promptField(player, mineId, material, Field.NORMAL_XP);
            } else if (event.isShiftClick()) {
                promptField(player, mineId, material, Field.MCMMO_XP);
            }
            return;
        }

        int delta = 0;
        if (event.isLeftClick()) {
            delta = event.isShiftClick() ? 10 : 1;
        } else if (event.isRightClick()) {
            delta = event.isShiftClick() ? -10 : -1;
        }
        if (delta == 0) {
            return;
        }

        MineBlock block = mine.getCompositionBlock(material);
        if (block == null) {
            return;
        }
        block.setWeight(Math.max(0, Math.round((block.getWeight() + delta) * 10.0) / 10.0));
        mineManager.save();
        player.closeInventory();
        open(player, mineId);
    }

    private void addBlock(Player player, Mine mine, Material material) {
        if (mine.getComposition().stream().anyMatch(b -> b.getMaterial() == material)) {
            ChatUtil.send(player, "<red>Esse material ja esta na composicao - clique nele pra editar.");
            return;
        }
        mine.getComposition().add(new MineBlock(material, 10.0));
        mineManager.save();
        ChatUtil.send(player, "<green>" + material.name() + " adicionado com chance 10 (relativa).");
        player.closeInventory();
        open(player, mine.getId());
    }

    private void removeBlock(Player player, Mine mine, Material material) {
        mine.getComposition().removeIf(b -> b.getMaterial() == material);
        mineManager.save();
        ChatUtil.send(player, "<red>" + material.name() + " removido da composicao.");
        player.closeInventory();
        open(player, mine.getId());
    }

    private void promptField(Player player, String mineId, Material material, Field field) {
        pending.put(player.getUniqueId(), new PendingBlockInput(mineId, material, field));
        player.closeInventory();

        String question = switch (field) {
            case NORMAL_XP -> "<green>Digite o XP normal (vanilla) dado ao quebrar este bloco (0 = nenhum).";
            case MCMMO_XP -> "<green>Digite o XP de mcMMO (Mineracao) dado ao quebrar este bloco (0 = nenhum XP).";
        };
        ChatUtil.send(player, question + " Digite <red>cancelar</red><green> para voltar.");
    }

    /**
     * Chamado pelo BlockCompositionChatListener, ja na main thread, com o texto
     * digitado no chat. Cada campo (XP normal, XP mcMMO) e independente - edita
     * direto o MineBlock ja existente na composicao, sem reconstruir o objeto.
     */
    public void handleChatInput(Player player, String input) {
        PendingBlockInput request = pending.remove(player.getUniqueId());
        if (request == null) {
            return;
        }

        if (input.equalsIgnoreCase("cancelar")) {
            ChatUtil.send(player, "<yellow>Operacao cancelada.");
            reopenXp(player, request.mineId());
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

        if (request.field() == Field.MCMMO_XP) {
            block.setMcmmoXp(xp);
            block.setMcmmoXpConfigured(true);
        } else {
            block.setNormalXp(xp);
        }
        mineManager.save();
        ChatUtil.send(player, "<green>" + block.getMaterial().name() + " agora da " + trim(xp) + " XP "
                + (request.field() == Field.MCMMO_XP ? "de mcMMO" : "normal") + " ao quebrar"
                + (request.field() == Field.MCMMO_XP && xp <= 0 ? " (desligado na mina)." : "."));
        reopenXp(player, request.mineId());
    }

    private void reopenXp(Player player, String mineId) {
        Bukkit.getScheduler().runTask(plugin, () -> openXp(player, mineId));
    }

    private String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format("%.1f", value);
    }

    private static ItemStack buildItem(Material material, Component displayName, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(displayName);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public record Holder(String mineId, boolean xpMode) implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() {
            return null;
        }
    }

    private enum Field {
        NORMAL_XP, MCMMO_XP
    }

    public record PendingBlockInput(String mineId, Material material, Field field) {
    }
}
