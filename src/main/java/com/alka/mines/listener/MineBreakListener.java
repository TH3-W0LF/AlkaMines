package com.alka.mines.listener;

import com.alkacode.economy.CurrencyType;
import com.alka.mines.hook.AlkaEconomyHook;
import com.alka.mines.hook.AlkaShopHook;
import com.alka.mines.manager.MineManager;
import com.alka.mines.manager.PlayerDataManager;
import com.alka.mines.manager.PlayerMineData;
import com.alka.mines.model.Mine;
import com.alka.mines.model.MineBlock;
import com.alka.mines.util.ChatUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Unico ponto que faz a mina "funcionar de verdade": entrega o drop real do bloco
 * (respeitando Fortune/Silk Touch via Block#getDrops(tool), nao um ItemStack cru do
 * proprio Material), decrementa blocksRemaining, conta blocos quebrados por jogador
 * e paga ESCARION (reward especifico do bloco quebrado).
 *
 * A mina NAO vende nada e NAO sabe o que e coins - preco/moeda sao decisao exclusiva
 * do AlkaShop (preco global por Material, independente de mina). Se AlkaDrop estiver
 * instalado e o jogador estiver dentro desta mina, o AlkaDropHook cede o controle do
 * bloco de volta pra ca (ver AlkaMinesHook no AlkaDrop) - por isso o drop ainda e
 * entregue diretamente aqui, e nao pelo AlkaDrop. Se a auto-venda do AlkaShop estiver
 * ativa pro jogador, o drop e entregue ao AlkaShopHook (que so pergunta "vendavel?" e
 * deposita) em vez de ir pro inventario - a mina nunca sabe o preco, so intermedeia.
 *
 * HIGHEST + ignoreCancelled=true: plugins de protecao (WorldGuard etc.) em
 * prioridades mais baixas ja tiveram chance de cancelar - se cancelaram, nosso
 * handler nem roda (ignoreCancelled=true) e o bloco desta mina nao e tocado. Se
 * chegou ate aqui, cancelamos o evento NOS MESMOS e assumimos o controle manual
 * completo (drops, remocao do bloco, recompensas) em vez de deixar o vanilla
 * processar em cima de um bloco que ja modificamos.
 *
 * Nota: cancelar aqui faz qualquer plugin de log/anti-grief registrado em MONITOR
 * (ex: CoreProtect) ver isCancelled()=true - se algum desses plugins decidir NAO
 * logar a quebra por causa disso, isso e um efeito colateral a se observar.
 *
 * Nao chama MineManager.save() aqui de proposito - reescreveria o mines.yml inteiro
 * a cada bloco quebrado. blocksRemaining so persiste de fato no proximo reset ou no
 * onDisable.
 */
public class MineBreakListener implements Listener {

    private final MineManager mineManager;
    private final PlayerDataManager playerDataManager;
    private final Optional<AlkaEconomyHook> economyHook;
    private final Optional<AlkaShopHook> shopHook;

    public MineBreakListener(MineManager mineManager, PlayerDataManager playerDataManager,
                              Optional<AlkaEconomyHook> economyHook, Optional<AlkaShopHook> shopHook) {
        this.mineManager = mineManager;
        this.playerDataManager = playerDataManager;
        this.economyHook = economyHook;
        this.shopHook = shopHook;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        Block block = event.getBlock();
        Mine mine = mineManager.getMineAt(block.getLocation()).orElse(null);
        if (mine == null) {
            return;
        }

        // drops calculados com o bloco ainda no tipo original, ANTES de cancelar/remover.
        Collection<ItemStack> drops = block.getDrops(player.getInventory().getItemInMainHand());

        event.setCancelled(true);
        event.setDropItems(false);
        event.setExpToDrop(0);

        // applyPhysics=true aqui gera ghost block: o servidor processa fisica
        // (redstone/luz/blocos adjacentes) em cima de um BlockBreakEvent que ele acha
        // cancelado, criando uma corrida em que o bloco pode nao sincronizar direito
        // com o cliente. Removemos sem fisica e sincronizamos manualmente com todos os
        // jogadores proximos (nao so quem quebrou), senao jogadores por perto ainda
        // veem o bloco antigo (ghost block pra eles).
        Location location = block.getLocation();
        block.setType(Material.AIR, false);
        for (Player nearby : block.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(location) < 2500) {
                nearby.sendBlockChange(location, Material.AIR.createBlockData());
            }
        }

        mine.setBlocksRemaining(Math.max(0, mine.getBlocksRemaining() - 1));

        PlayerMineData data = playerDataManager.get(player.getUniqueId());
        data.incrementBlocksBroken();

        MineBlock mineBlock = mine.getCompositionBlock(block.getType());
        StringBuilder actionBar = new StringBuilder();

        giveOrSellDrops(player, drops, actionBar);

        // escarion vem EXCLUSIVAMENTE do MineBlock quebrado - rewardPerBlock da mina
        // (MineSettings) nao e mais lido aqui, so mantido no modelo por compatibilidade
        // com mines.yml antigos (ver MineManager#load, que migra esse valor pros blocos).
        double reward = mineBlock != null ? mineBlock.getReward("escarion") : 0;
        if (reward > 0) {
            economyHook.ifPresent(hook -> hook.deposit(player.getUniqueId(), CurrencyType.ESCARION, reward));
            if (!actionBar.isEmpty()) {
                actionBar.append("  ");
            }
            actionBar.append("<gold>+").append(trim(reward)).append(" Escarion</gold>");
        }

        if (!actionBar.isEmpty()) {
            player.sendActionBar(ChatUtil.parse(actionBar.toString()));
        }
    }

    private void giveOrSellDrops(Player player, Collection<ItemStack> drops, StringBuilder actionBar) {
        boolean autoSell = shopHook.isPresent() && shopHook.get().isAutoSellActive(player);
        Map<String, Double> soldTotals = new LinkedHashMap<>();

        for (ItemStack drop : drops) {
            if (autoSell && shopHook.get().isSellable(drop.getType())) {
                Map<String, Double> totals = shopHook.get().sell(player, drop);
                for (Map.Entry<String, Double> entry : totals.entrySet()) {
                    soldTotals.merge(entry.getKey(), entry.getValue(), Double::sum);
                }
                if (!totals.isEmpty()) {
                    continue;
                }
            }

            Map<Integer, ItemStack> leftover = player.getInventory().addItem(drop);
            if (!leftover.isEmpty()) {
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
                ChatUtil.send(player, "<red><bold>INVENTARIO CHEIO!</bold> <gray>Itens dropados no chao.");
            }
        }

        if (!soldTotals.isEmpty()) {
            if (!actionBar.isEmpty()) {
                actionBar.append("  ");
            }
            actionBar.append("<green><bold>+</bold></green> <gray>(auto-venda: ");
            int i = 0;
            for (Map.Entry<String, Double> entry : soldTotals.entrySet()) {
                if (i++ > 0) {
                    actionBar.append(", ");
                }
                actionBar.append(trim(entry.getValue())).append(' ').append(entry.getKey());
            }
            actionBar.append(")</gray>");
        }
    }

    private String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format("%.1f", value);
    }
}
