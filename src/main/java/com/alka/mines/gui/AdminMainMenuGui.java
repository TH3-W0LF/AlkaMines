package com.alka.mines.gui;

import com.alka.mines.config.MenuConfig;
import com.alka.mines.hologram.HologramManager;
import com.alka.mines.hook.ItemsAdderHook;
import com.alka.mines.manager.MineManager;
import com.alka.mines.model.Mine;
import com.alka.mines.util.ChatUtil;
import com.alkacode.core.gui.BaseGui;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

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
        return MenuConfig.getInstance().title("titles.admin",
                Map.of("mine", mine != null ? mine.getDisplayName() : mineId));
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

        String iconName = mine.getIconItemsAdder() != null ? "ItemsAdder: " + mine.getIconItemsAdder()
                : mine.getIcon() != null ? mine.getIcon().name() : "nenhum (usa la verde/vermelha)";
        MenuConfig cfg = MenuConfig.getInstance();

        fillBorder(new ItemStack(Material.PURPLE_STAINED_GLASS_PANE));

        setItem(4, cfg.item("items.admin.category", Map.of("category", mine.getCategory())),
                event -> {
                    if (mineResetMenu != null) {
                        mineResetMenu.promptCategory(player, mine.getId());
                    }
                });
        setItem(11, cfg.item("items.admin.rename", Map.of("mine", mine.getDisplayName())),
                event -> {
                    if (mineResetMenu != null) {
                        mineResetMenu.promptRename(player, mine.getId());
                    }
                });
        setItem(10, cfg.item("items.admin.composition", Map.of()),
                event -> {
                    player.closeInventory();
                    if (blockCompositionMenu != null) {
                        blockCompositionMenu.open(player, mine.getId());
                    }
                });
        setItem(12, cfg.item("items.admin.spawn", Map.of()),
                event -> {
                    mine.setSpawn(player.getLocation());
                    mineManager.save();
                    ChatUtil.send(player, "<green>Spawn da mina '" + mine.getId() + "' atualizado.");
                });
        setItem(13, cfg.item("items.admin.icon", Map.of("icon", iconName)),
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
        setItem(14, cfg.item("items.admin.reset", Map.of()),
                event -> {
                    player.closeInventory();
                    if (mineResetMenu != null) {
                        mineResetMenu.open(player, mine.getId());
                    }
                });
        setItem(15, cfg.item("items.admin.rewards", Map.of()),
                event -> {
                    player.closeInventory();
                    if (mineRewardsMenu != null) {
                        mineRewardsMenu.open(player, mine.getId());
                    }
                });
        setItem(16, cfg.item(hologramsEnabled ? "items.admin.hologram" : "items.admin.hologram-offline",
                Map.of("location", mine.getHologramLocation() != null ? "Definido" : "Nao definido")),
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
        setItem(22, cfg.item("items.admin.delete", Map.of()),
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
}
