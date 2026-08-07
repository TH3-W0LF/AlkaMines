package com.alka.mines.listener;

import com.alka.mines.hook.AdvancedEnchantmentsHook;
import com.alka.mines.hook.AlkaShopHook;
import com.alka.mines.hook.ItemsAdderHook;
import com.alka.mines.hook.McMMOHook;
import com.alka.mines.manager.MineManager;
import com.alka.mines.manager.PickaxeLevelManager;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Unico ponto que faz a mina "funcionar de verdade": entrega o drop real do bloco
 * (respeitando Fortune/Silk Touch via Block#getDrops(tool), nao um ItemStack cru do
 * proprio Material), decrementa blocksRemaining e conta blocos quebrados por jogador.
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
 * completo (drops e remocao do bloco) em vez de deixar o vanilla processar em cima
 * de um bloco que ja modificamos.
 *
 * EXCECAO: bloco custom do ItemsAdder. NAO cancelamos o evento pra esses - o
 * ItemsAdder mantem estado interno por bloco (luz, hologram, etc) e so o dropa
 * corretamente se o proprio evento seguir seu curso normal. Cancelar e remover via
 * setType(AIR) como fazemos pros blocos normais deixa o ItemsAdder com um bloco
 * "fantasma" pro cliente (nunca avisado da remocao) e o drop correto nunca acontece
 * (o vanilla dropa o Material base, nao o item custom). Pra bloco custom so fazemos
 * o que e nosso mesmo (XP, contagem de progresso) e deixamos o resto pro
 * ItemsAdder/vanilla - com a limitacao de que a auto-venda do AlkaShop nao rola
 * pra esse drop, ja que ele nunca passa pelo nosso pipeline de inventario.
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
    private final PickaxeLevelManager levelManager;
    private final Optional<AlkaShopHook> shopHook;
    private final Optional<McMMOHook> mcmmoHook;
    private final Optional<AdvancedEnchantmentsHook> aeHook;

    public MineBreakListener(MineManager mineManager, PlayerDataManager playerDataManager,
                              PickaxeLevelManager levelManager, Optional<AlkaShopHook> shopHook,
                              Optional<McMMOHook> mcmmoHook, Optional<AdvancedEnchantmentsHook> aeHook) {
        this.mineManager = mineManager;
        this.playerDataManager = playerDataManager;
        this.levelManager = levelManager;
        this.shopHook = shopHook;
        this.mcmmoHook = mcmmoHook;
        this.aeHook = aeHook;
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

        // AdvancedEnchantments processa BlockBreakEvent em HIGH - precisa rodar ANTES
        // de cancelarmos o evento em HIGHEST, senao os efeitos/encantamentos dele nunca disparam.
        aeHook.ifPresent(hook -> hook.processBlockBreak(player, block));

        // entrada da composicao que corresponde a este bloco (null se nao configurado) -
        // traz os overrides de %/XP definidos direto no BlockCompositionMenu. Precisa
        // ser resolvido AGORA, antes de qualquer remocao (usa o Material/namespace original).
        MineBlock compositionBlock = resolveCompositionBlock(mine, block);

        if (ItemsAdderHook.isCustomBlock(block)) {
            // ver javadoc da classe - nao cancelamos, deixamos o ItemsAdder/vanilla
            // processarem a quebra e o drop custom sozinhos.
            trackProgress(player, mine);
            applyXp(player, block, compositionBlock);
            return;
        }

        // drops calculados com o bloco ainda no tipo original, ANTES de cancelar/remover.
        Collection<ItemStack> drops = block.getDrops(player.getInventory().getItemInMainHand());

        event.setCancelled(true);
        event.setDropItems(false);
        event.setExpToDrop(0);

        applyXp(player, block, compositionBlock);

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

        trackProgress(player, mine);

        StringBuilder actionBar = new StringBuilder();

        giveOrSellDrops(player, drops, actionBar);

        if (!actionBar.isEmpty()) {
            player.sendActionBar(ChatUtil.parse(actionBar.toString()));
        }
    }

    private void trackProgress(Player player, Mine mine) {
        mine.setBlocksRemaining(Math.max(0, mine.getBlocksRemaining() - 1));

        PlayerMineData data = playerDataManager.get(player.getUniqueId());
        data.incrementBlocksBroken();

        if (data.recalculateLevel(levelManager.getThresholds())) {
            announceLevelUp(player, data);
        }
    }

    private void applyXp(Player player, Block block, MineBlock compositionBlock) {
        mcmmoHook.ifPresent(hook -> hook.addMiningXp(player, block, compositionBlock));
        if (compositionBlock != null && compositionBlock.getNormalXp() > 0) {
            player.giveExp((int) Math.round(compositionBlock.getNormalXp()));
        }
    }

    /** Acha a entrada da composicao correspondente ao bloco quebrado - checa namespace
     * do ItemsAdder primeiro (bloco custom "de verdade"), Material vanilla depois. */
    private MineBlock resolveCompositionBlock(Mine mine, Block block) {
        if (ItemsAdderHook.isEnabled()) {
            String namespace = ItemsAdderHook.getBlockNamespace(block);
            if (namespace != null) {
                for (MineBlock candidate : mine.getComposition()) {
                    if (candidate.isCustomBlock() && namespace.equals(candidate.getCustomBlockId())) {
                        return candidate;
                    }
                }
            }
        }
        for (MineBlock candidate : mine.getComposition()) {
            if (!candidate.isCustomBlock() && candidate.getMaterial() == block.getType()) {
                return candidate;
            }
        }
        return null;
    }

    private void announceLevelUp(Player player, PlayerMineData data) {
        int newLevel = data.getPickaxeLevel();
        long next = levelManager.getBlocksForNextLevel(newLevel);

        ChatUtil.send(player, "");
        ChatUtil.send(player, "<gold><bold>⛏ NIVEL DE PICARETA UP! <yellow>Voce alcancou o nivel <white>" + newLevel + "<yellow>!");
        ChatUtil.send(player, "<gray>Blocos minerados: <white>" + String.format(Locale.US, "%,d", data.getBlocksBroken()));
        if (next > 0) {
            ChatUtil.send(player, "<gray>Proximo nivel em: <white>" + String.format(Locale.US, "%,d", next) + " <gray>blocos");
        } else {
            ChatUtil.send(player, "<gold><bold>NIVEL MAXIMO!</bold> <gray>Voce e uma lenda.");
        }
        ChatUtil.send(player, "");
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
