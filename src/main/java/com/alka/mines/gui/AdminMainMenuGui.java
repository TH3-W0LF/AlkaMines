package com.alka.mines.gui;

import com.alka.mines.hologram.HologramManager;
import com.alka.mines.hook.ItemsAdderHook;
import com.alka.mines.manager.MineManager;
import com.alka.mines.model.Mine;
import com.alka.mines.util.ChatUtil;
import com.alkacode.core.gui.BaseGui;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/** GUI de 27 slots com as acoes administrativas de uma mina especifica (BaseGui do AlkaCore). */
public class AdminMainMenuGui extends BaseGui {

    private final MineManager mineManager;
    private final HologramManager hologramManager;
    private final BlockCompositionMenu blockCompositionMenu;
    private final MineResetMenu mineResetMenu;
    private final MineRewardsMenu mineRewardsMenu;
    private final String mineId;

    public AdminMainMenuGui(JavaPlugin plugin, Player admin, MineManager mineManager, HologramManager hologramManager,
                            BlockCompositionMenu blockCompositionMenu, MineResetMenu mineResetMenu,
                            MineRewardsMenu mineRewardsMenu, String mineId) {
        super(plugin, admin, title(plugin, mineManager, mineId), 3, "alkamines-admin");
        this.mineManager = mineManager;
        this.hologramManager = hologramManager;
        this.blockCompositionMenu = blockCompositionMenu;
        this.mineResetMenu = mineResetMenu;
        this.mineRewardsMenu = mineRewardsMenu;
        this.mineId = mineId;
    }

    private static String title(JavaPlugin plugin, MineManager mineManager, String mineId) {
        Mine mine = mineManager.getMine(mineId).orElse(null);
        return "<dark_gray>Mina: " + (mine != null ? mine.getDisplayName() : mineId);
    }

    @Override
    public void render() {
        Mine mine = mineManager.getMine(mineId).orElse(null);
        if (mine == null) {
            ChatUtil.send(player, "<red>Mina nao encontrada: " + mineId);
            player.closeInventory();
            return;
        }

        boolean hologramsEnabled = hologramManager.isEnabled();
        Component hologramName = hologramsEnabled
                ? ChatUtil.parse("<aqua><bold>Setar Holograma")
                : ChatUtil.parse("<gray><bold>Holograma Offline");
        List<Component> hologramLore = hologramsEnabled
                ? List.of(
                        ChatUtil.parse("<gray>Local: <white>" + (mine.getHologramLocation() != null ? "Definido" : "Nao definido")),
                        ChatUtil.parse(""),
                        ChatUtil.parse("<yellow>Clique para spawnar/atualizar"))
                : List.of(ChatUtil.parse("<red>DecentHolograms nao encontrado"));

        String iconName = mine.getIconItemsAdder() != null ? "ItemsAdder: " + mine.getIconItemsAdder()
                : mine.getIcon() != null ? mine.getIcon().name() : "nenhum (usa la verde/vermelha)";

        fillBorder(new ItemStack(Material.PURPLE_STAINED_GLASS_PANE));

        setItem(4, icon(Material.OAK_SIGN, "<gold><bold>Definir Categoria",
                "<gray>Atual: <white>" + mine.getCategory(), "", "<yellow>Clique para alterar (ex: vip, pvp, ranking)"),
                event -> {
                    if (mineResetMenu != null) {
                        mineResetMenu.promptCategory(player, mine.getId());
                    }
                });
        setItem(11, icon(Material.PAPER, "<yellow><bold>Renomear Mina",
                "<gray>Atual: <white>" + mine.getDisplayName(), "", "<yellow>Clique para definir um nome novo"),
                event -> {
                    if (mineResetMenu != null) {
                        mineResetMenu.promptRename(player, mine.getId());
                    }
                });
        setItem(10, icon(Material.GRASS_BLOCK, "<green><bold>Composicao de Blocos",
                "<gray>Gerencie os blocos que", "<gray>compoem esta mina.", "", "<yellow>Clique para editar"),
                event -> {
                    player.closeInventory();
                    if (blockCompositionMenu != null) {
                        blockCompositionMenu.open(player, mine.getId());
                    }
                });
        setItem(12, icon(Material.ENDER_PEARL, "<aqua><bold>Definir Spawn",
                "<gray>Define onde o jogador", "<gray>entra nesta mina.", "", "<yellow>Clique para setar spawn no seu pe"),
                event -> {
                    mine.setSpawn(player.getLocation());
                    mineManager.save();
                    ChatUtil.send(player, "<green>Spawn da mina '" + mine.getId() + "' atualizado.");
                });
        setItem(13, icon(Material.EMERALD, "<light_purple><bold>Definir Icone",
                "<gray>Atual: <white>" + iconName, "", "<yellow>Segure um item na mao e clique",
                "<yellow>(aceita itens/blocos custom do ItemsAdder)"),
                event -> {
                    ItemStack held = player.getInventory().getItemInMainHand();
                    if (held.getType().isAir()) {
                        ChatUtil.send(player, "<red>Segure um item na mao pra usar como icone.");
                        return;
                    }
                    String namespace = ItemsAdderHook.isEnabled()
                            ? ItemsAdderHook.getCustomStackNamespace(held) : null;
                    if (namespace != null) {
                        mine.setIconItemsAdder(namespace);
                        mine.setIcon(held.getType());
                        ChatUtil.send(player, "<green>Icone da mina '" + mine.getId()
                                + "' definido como item custom '" + namespace + "'.");
                    } else {
                        mine.setIcon(held.getType());
                        mine.setIconItemsAdder(null);
                        ChatUtil.send(player, "<green>Icone da mina '" + mine.getId() + "' definido como "
                                + held.getType().name() + ".");
                    }
                    mineManager.save();
                    player.closeInventory();
                    new AdminMainMenuGui(plugin, player, mineManager, hologramManager,
                            blockCompositionMenu, mineResetMenu, mineRewardsMenu, mine.getId()).open();
                });
        setItem(14, icon(Material.CLOCK, "<gold><bold>Configurar Reset",
                "<gray>Tempo e porcentagem", "<gray>automatica de reset.", "", "<yellow>Clique para configurar"),
                event -> {
                    player.closeInventory();
                    if (mineResetMenu != null) {
                        mineResetMenu.open(player, mine.getId());
                    }
                });
        setItem(15, icon(Material.EMERALD_BLOCK, "<green><bold>Recompensas",
                "<gray>Itens e comandos aleatorios", "<gray>ao quebrar blocos.", "", "<yellow>Clique para editar"),
                event -> {
                    player.closeInventory();
                    if (mineRewardsMenu != null) {
                        mineRewardsMenu.open(player, mine.getId());
                    }
                });
        setItem(16, hologramItem(Material.ARMOR_STAND, hologramName, hologramLore),
                event -> {
                    if (!hologramsEnabled) {
                        ChatUtil.send(player, "<red>DecentHolograms nao esta instalado neste servidor.");
                        return;
                    }
                    Location loc = player.getLocation();
                    mine.setHologramLocation(loc);
                    hologramManager.createOrUpdate(mine, loc);
                    mineManager.save();
                    ChatUtil.send(player, "<green>Holograma setado em <white>" + loc.getBlockX() + " "
                            + loc.getBlockY() + " " + loc.getBlockZ() + "</white><green>. Use o menu para atualizar.");
                });
        setItem(22, icon(Material.BARRIER, "<red><bold>Deletar Mina",
                "<gray><bold>ATENCAO!</bold> Acao irreversivel.", "", "<red>Shift+Click para confirmar"),
                event -> {
                    if (!event.isShiftClick()) {
                        ChatUtil.send(player, "<yellow>Shift+Click para confirmar a exclusao.");
                        return;
                    }
                    player.closeInventory();
                    String id = mine.getId();
                    mineManager.deleteMine(id);
                    hologramManager.delete(id);
                    ChatUtil.send(player, "<green>Mina '" + id + "' deletada.");
                });
    }

    private static ItemStack icon(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(ChatUtil.parse(name));
        List<Component> loreList = new ArrayList<>();
        for (String line : lore) {
            loreList.add(ChatUtil.parse(line));
        }
        meta.lore(loreList);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack hologramItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
