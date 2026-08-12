package com.alka.mines.gui;

import com.alka.mines.config.MenuConfig;
import com.alka.mines.hook.AlkaEconomyHook;
import com.alka.mines.manager.PrivateMineManager;
import com.alka.mines.model.MineTemplate;
import com.alka.mines.model.PrivateMine;
import com.alka.mines.util.ChatUtil;
import com.alkacode.core.gui.BaseGui;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Menu da mina particular (BaseGui do AlkaCore) - itens/titulo 100% configurados em
 * menus.yml (MenuConfig). O dono ve raridade/expiração, define o tempo de reset (entre
 * as opcoes liberadas pro grupo dele) e compra upgrade pagando a moeda da AlkaEconomy.
 */
public class PrivateMineGui extends BaseGui {

    private static final SimpleDateFormat DATE = new SimpleDateFormat("dd/MM/yyyy");

    private final PrivateMineManager privateMineManager;

    public PrivateMineGui(JavaPlugin plugin, Player player, PrivateMineManager privateMineManager) {
        super(plugin, player, MenuConfig.getInstance().title("titles.private", Map.of()), 6, "alkamines-privada");
        this.privateMineManager = privateMineManager;
    }

    @Override
    public void render() {
        Optional<PrivateMine> optional = privateMineManager.getMineProtectingAt(player.getLocation());
        if (optional.isEmpty()) {
            ChatUtil.send(player, "<red>Voce nao esta numa mina particular.");
            player.closeInventory();
            return;
        }
        PrivateMine mine = optional.get();
        if (!mine.getOwner().equals(player.getUniqueId())) {
            ChatUtil.send(player, "<red>Essa mina nao e sua.");
            player.closeInventory();
            return;
        }
        MineTemplate template = privateMineManager.getTemplate(mine.getTemplateId()).orElse(null);
        MenuConfig cfg = MenuConfig.getInstance();

        fillBorder(new ItemStack(Material.BLACK_STAINED_GLASS_PANE));

        // Header (tipo/raridade)
        setItem(4, cfg.item("items.private.header", Map.of(
                "type", template != null ? template.getDisplayName() : mine.getTemplateId(),
                "rarity", template != null ? template.getRarity() : "★")), null);

        // Upgrade (custo escalona com o nivel da mina)
        double cost = privateMineManager.getUpgradeCost(mine);
        String currency = privateMineManager.getUpgradeCurrency();
        AlkaEconomyHook economy = AlkaEconomyHook.getInstance();
        String costStr = economy != null ? economy.format(cost) : String.valueOf((long) cost);
        String balanceStr = economy != null ? economy.format(economy.getBalance(player.getUniqueId(), currency)) : "?";
        setItem(11, cfg.item("items.private.upgrade", Map.of(
                "expand", String.valueOf(privateMineManager.getExpandAmount()),
                "level", String.valueOf(mine.getUpgradeLevel()),
                "cost", costStr,
                "currency", currency,
                "balance", balanceStr)), event -> {
            String error = privateMineManager.expandUpgraded(player);
            if (error != null) {
                ChatUtil.send(player, error);
            } else {
                ChatUtil.send(player, "<green>Mina expandida! Custo descontado.");
                refresh();
            }
        });

        // Tempo de reset (opcoes liberadas pro grupo do jogador)
        int current = mine.getResetIntervalMinutes();
        List<Integer> options = privateMineManager.getResetTimeOptions(player.getUniqueId());
        int startSlot = 20;
        for (int i = 0; i < options.size() && i < 9; i++) {
            int option = options.get(i);
            int slot = startSlot + i;
            boolean isCurrent = option == current;
            setItem(slot, cfg.item(isCurrent ? "items.private.reset-time-current" : "items.private.reset-time",
                    Map.of("minutes", String.valueOf(option))), event -> {
                String error = privateMineManager.setResetInterval(player, option);
                if (error != null) {
                    ChatUtil.send(player, error);
                } else {
                    ChatUtil.send(player, "<green>Reset definido pra " + option + " min.");
                    refresh();
                }
            });
        }

        // Informacoes
        String ownerName = Bukkit.getOfflinePlayer(mine.getOwner()).getName();
        long volume = mine.volume();
        double pct = volume > 0
                ? Math.round((mine.getBlocksRemaining() / (double) volume) * 1000.0) / 10.0 : 0.0;
        String founded = DATE.format(new Date(mine.getCreatedAt()));
        String expires = template != null && template.getExpiresInDays() > 0
                ? DATE.format(new Date(mine.getCreatedAt() + template.getExpiresInDays() * 86_400_000L))
                : "Eterna";
        setItem(13, cfg.item("items.private.info", Map.of(
                "owner", ownerName != null ? ownerName : mine.getOwner().toString(),
                "blocks", String.format(Locale.US, "%,d", mine.getBlocksRemaining()),
                "percentage", String.valueOf(pct),
                "founded", founded,
                "expires", expires)), null);

        setItem(49, cfg.item("items.private.close", Map.of()), event -> player.closeInventory());
    }
}
