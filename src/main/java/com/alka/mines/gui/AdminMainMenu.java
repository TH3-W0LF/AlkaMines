package com.alka.mines.gui;

import com.alka.mines.hologram.HologramManager;
import com.alka.mines.hook.ItemsAdderHook;
import com.alka.mines.manager.MineManager;
import com.alka.mines.model.Mine;
import com.alka.mines.util.ChatUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** GUI de 27 slots com as acoes administrativas de uma mina especifica. */
public class AdminMainMenu {

    private final MineManager mineManager;
    private final HologramManager hologramManager;
    private BlockCompositionMenu blockCompositionMenu;
    private MineResetMenu mineResetMenu;

    public AdminMainMenu(MineManager mineManager, HologramManager hologramManager) {
        this.mineManager = mineManager;
        this.hologramManager = hologramManager;
    }

    /** Setters em vez de construtor: os sub-menus se referenciam mutuamente com este menu. */
    public void setBlockCompositionMenu(BlockCompositionMenu blockCompositionMenu) {
        this.blockCompositionMenu = blockCompositionMenu;
    }

    public void setMineResetMenu(MineResetMenu mineResetMenu) {
        this.mineResetMenu = mineResetMenu;
    }

    public void open(Player admin, String mineId) {
        Mine mine = mineManager.getMine(mineId).orElse(null);
        if (mine == null) {
            ChatUtil.send(admin, "<red>Mina nao encontrada: " + mineId);
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

        Inventory inv = new MenuBuilder(27, ChatUtil.parse("<dark_gray>Mina: " + mine.getDisplayName()))
                .fillBorder(Material.PURPLE_STAINED_GLASS_PANE)
                .item(4, Material.OAK_SIGN, ChatUtil.parse("<gold><bold>Definir Categoria"),
                        List.of(
                                ChatUtil.parse("<gray>Atual: <white>" + mine.getCategory()),
                                ChatUtil.parse(""),
                                ChatUtil.parse("<yellow>Clique para alterar (ex: vip, pvp, ranking)")
                        ),
                        event -> {
                            if (mineResetMenu != null) {
                                mineResetMenu.promptCategory(admin, mine.getId());
                            }
                        })
                .item(11, Material.PAPER, ChatUtil.parse("<yellow><bold>Renomear Mina"),
                        List.of(
                                ChatUtil.parse("<gray>Atual: <white>" + mine.getDisplayName()),
                                ChatUtil.parse(""),
                                ChatUtil.parse("<yellow>Clique para definir um nome novo")
                        ),
                        event -> {
                            if (mineResetMenu != null) {
                                mineResetMenu.promptRename(admin, mine.getId());
                            }
                        })
                .item(10, Material.GRASS_BLOCK, ChatUtil.parse("<green><bold>Composicao de Blocos"),
                        List.of(
                                ChatUtil.parse("<gray>Gerencie os blocos que"),
                                ChatUtil.parse("<gray>compoem esta mina."),
                                ChatUtil.parse(""),
                                ChatUtil.parse("<yellow>Clique para editar")
                        ),
                        event -> {
                            admin.closeInventory();
                            if (blockCompositionMenu != null) {
                                blockCompositionMenu.open(admin, mine.getId());
                            }
                        })
                .item(12, Material.ENDER_PEARL, ChatUtil.parse("<aqua><bold>Definir Spawn"),
                        List.of(
                                ChatUtil.parse("<gray>Define onde o jogador"),
                                ChatUtil.parse("<gray>entra nesta mina."),
                                ChatUtil.parse(""),
                                ChatUtil.parse("<yellow>Clique para setar spawn no seu pe")
                        ),
                        event -> {
                            mine.setSpawn(admin.getLocation());
                            mineManager.save();
                            ChatUtil.send(admin, "<green>Spawn da mina '" + mine.getId() + "' atualizado.");
                        })
                .item(13, Material.EMERALD, ChatUtil.parse("<light_purple><bold>Definir Icone"),
                        List.of(
                                ChatUtil.parse("<gray>Atual: <white>" + iconName),
                                ChatUtil.parse(""),
                                ChatUtil.parse("<yellow>Segure um item na mao e clique"),
                                ChatUtil.parse("<yellow>(aceita itens/blocos custom do ItemsAdder)")
                        ),
                        event -> {
                            ItemStack held = admin.getInventory().getItemInMainHand();
                            if (held.getType().isAir()) {
                                ChatUtil.send(admin, "<red>Segure um item na mao pra usar como icone.");
                                return;
                            }

                            // icone e so cosmetico (nunca colocado no mundo) - aceita QUALQUER
                            // stack custom do ItemsAdder, nao so blocos placeaveis.
                            String namespace = ItemsAdderHook.isEnabled()
                                    ? ItemsAdderHook.getCustomStackNamespace(held) : null;

                            if (namespace != null) {
                                mine.setIconItemsAdder(namespace);
                                mine.setIcon(null);
                                ChatUtil.send(admin, "<green>Icone da mina '" + mine.getId()
                                        + "' definido como item custom '" + namespace + "'.");
                            } else {
                                mine.setIcon(held.getType());
                                mine.setIconItemsAdder(null);
                                ChatUtil.send(admin, "<green>Icone da mina '" + mine.getId() + "' definido como "
                                        + held.getType().name() + ".");
                            }

                            mineManager.save();
                            admin.closeInventory();
                            open(admin, mine.getId());
                        })
                .item(14, Material.CLOCK, ChatUtil.parse("<gold><bold>Configurar Reset"),
                        List.of(
                                ChatUtil.parse("<gray>Tempo e porcentagem"),
                                ChatUtil.parse("<gray>automatica de reset."),
                                ChatUtil.parse(""),
                                ChatUtil.parse("<yellow>Clique para configurar")
                        ),
                        event -> {
                            admin.closeInventory();
                            if (mineResetMenu != null) {
                                mineResetMenu.open(admin, mine.getId());
                            }
                        })
                .item(16, Material.ARMOR_STAND, hologramName, hologramLore,
                        event -> {
                            if (!hologramsEnabled) {
                                ChatUtil.send(admin, "<red>DecentHolograms nao esta instalado neste servidor.");
                                return;
                            }
                            Location loc = admin.getLocation();
                            mine.setHologramLocation(loc);
                            hologramManager.createOrUpdate(mine, loc);
                            mineManager.save();
                            ChatUtil.send(admin, "<green>Holograma setado em <white>" + loc.getBlockX() + " "
                                    + loc.getBlockY() + " " + loc.getBlockZ() + "</white><green>. Use o menu para atualizar.");
                        })
                .item(22, Material.BARRIER, ChatUtil.parse("<red><bold>Deletar Mina"),
                        List.of(
                                ChatUtil.parse("<gray><bold>ATENCAO!</bold> Acao irreversivel."),
                                ChatUtil.parse(""),
                                ChatUtil.parse("<red>Shift+Click para confirmar")
                        ),
                        event -> {
                            if (!event.isShiftClick()) {
                                ChatUtil.send(admin, "<yellow>Shift+Click para confirmar a exclusao.");
                                return;
                            }
                            admin.closeInventory();
                            String id = mine.getId();
                            mineManager.deleteMine(id);
                            hologramManager.delete(id);
                            ChatUtil.send(admin, "<green>Mina '" + id + "' deletada.");
                        })
                .build();

        admin.openInventory(inv);
    }
}
