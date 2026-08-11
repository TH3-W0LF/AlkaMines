package com.alka.mines.listener;

import com.alka.mines.hook.PlotSquaredHook;
import com.alka.mines.manager.PrivateMineManager;
import com.alka.mines.model.MineTemplate;
import com.alka.mines.util.ChatUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Ativacao do item gerador de mina particular: clique direito numa plot PRÓPRIA do
 * jogador (PlotSquared) ativa a mina com o template do item e consome o gerador.
 */
public class PrivateMineListener implements Listener {

    private final PrivateMineManager privateMineManager;

    public PrivateMineListener(PrivateMineManager privateMineManager) {
        this.privateMineManager = privateMineManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        // so a mao principal (evita disparar 2x com o mesmo item na offhand)
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!event.getPlayer().isSneaking()) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack held = event.getItem();
        Optional<String> templateId = privateMineManager.readGeneratorTemplate(held);
        if (templateId.isEmpty()) {
            return;
        }

        event.setCancelled(true);

        Optional<MineTemplate> template = privateMineManager.getTemplate(templateId.get());
        if (template.isEmpty()) {
            ChatUtil.send(player, "<red>Template de mina '" + templateId.get() + "' nao encontrado.");
            return;
        }

        Optional<PlotSquaredHook.PlotBounds> bounds = PlotSquaredHook.getOwnedPlotAt(player);
        if (bounds.isEmpty()) {
            if (!PlotSquaredHook.isEnabled()) {
                ChatUtil.send(player, "<red>PlotSquared nao esta instalado (ou a versao e incompativel). "
                        + "Veja o warning no boot do servidor com o motivo.");
            } else {
                ChatUtil.send(player, "<red>Voce precisa estar DENTRO de uma plot que seja SUA para ativar a mina "
                        + "(nao na estrada/entre plots). Se estiver, use /alkamines debug e veja o motivo no console.");
            }
            return;
        }

        String error = privateMineManager.createFromGenerator(player, bounds.get(), template.get());
        if (error != null) {
            ChatUtil.send(player, error);
            return;
        }

        // consome 1 gerador
        if (held.getAmount() > 1) {
            held.setAmount(held.getAmount() - 1);
        } else {
            held.setType(Material.AIR);
            player.getInventory().setItemInMainHand(held);
        }

        ChatUtil.send(player, "<green>Mina particular ativada! (template '" + template.get().getId()
                + "'). Ela reseta automaticamente a cada "
                + template.get().getResetIntervalMinutes() + " min.");
    }
}
