package com.alka.mines.listener;

import com.alka.mines.event.MineBlockBreakEvent;
import com.alka.mines.hook.AdvancedEnchantmentsHook;
import com.alka.mines.hook.AlkaDropHook;
import com.alka.mines.hook.AlkaShopHook;
import com.alka.mines.hook.McMMOHook;
import com.alka.mines.manager.MineManager;
import com.alka.mines.manager.PickaxeLevelManager;
import com.alka.mines.manager.PlayerDataManager;
import com.alka.mines.manager.PlayerMineData;
import com.alka.mines.manager.PrivateMineManager;
import com.alka.mines.model.Mine;
import com.alka.mines.model.MineBlock;
import com.alka.mines.model.MineReward;
import com.alka.mines.model.MineTemplate;
import com.alka.mines.model.PrivateMine;
import com.alka.mines.util.ChatUtil;
import com.alka.mines.util.DebugLogger;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Unico ponto que faz a mina "funcionar de verdade": entrega o drop real do bloco
 * (respeitando Fortune/Silk Touch via Block#getDrops(tool), nao um ItemStack cru do
 * proprio Material), decrementa blocksRemaining e conta blocos quebrados por jogador.
 *
 * A mina NAO vende nada e NAO sabe o que e gold - preco/moeda sao decisao exclusiva
 * do AlkaShop (preco global por Material, independente de mina). Se AlkaDrop estiver
 * instalado e o jogador estiver dentro desta mina, o AlkaDropHook cede o controle do
 * bloco de volta pra ca (ver AlkaMinesHook no AlkaDrop) - por isso o drop ainda e
 * entregue diretamente aqui, e nao pelo AlkaDrop. Se a auto-venda do AlkaShop estiver
 * ativa pro jogador, o drop e entregue ao AlkaShopHook (que so pergunta "vendavel?" e
 * deposita) em vez de ir pro inventario - a mina nunca sabe o preco, so intermedeia.
 *
 * A mina tambem NUNCA insere item direto no inventario por conta propria - o que nao
 * foi vendido e sempre entregue via {@link AlkaDropHook#deliverDrops} (respeita o
 * toggle de coleta do jogador: inventario ou chao) quando o AlkaDrop esta presente,
 * ou solto no chao (vanilla, `dropItemNaturally`) quando nao esta - "auto-coletar
 * sem precisar andar em cima do item" e uma feature do AlkaDrop, nao da mina.
 *
 * Soft-cancel das quebras de mina (ver {@link #onBreakPre} e {@link #onBreak}):
 * o evento e cancelado em LOWEST e re-ativado em HIGHEST. No meio do caminho,
 * listeners com ignoreCancelled=true (mcMMO, AlkaDrop, etc.) veem o evento
 * cancelado e nao concedem XP/drops nativos em cima do que o AlkaMines entrega;
 * no final, o evento NAO termina cancelado, entao o vanilla quebra o bloco
 * (fisica, som, particula, broadcast pra todos) sem ghost block nem kick de
 * "floating too long".
 *
 * Nota: a quebra termina como real (isCancelled=false) - plugins de log/anti-grief
 * (ex: CoreProtect) logam normalmente, comportamento correto pra uma mina.
 *
 * Nao chama MineManager.save() aqui de proposito - reescreveria o mines.yml inteiro
 * a cada bloco quebrado. blocksRemaining so persiste de fato no proximo reset ou no
 * onDisable.
 */
public class MineBreakListener implements Listener {

    /**
     * Soft-cancel das quebras de mina: o evento e cancelado em LOWEST (antes do mcMMO e
     * de qualquer listener com ignoreCancelled=true processar, impedindo XP/drops nativos
     * em dobro) e re-ativado em HIGHEST (ver {@link #onBreak}) pra o vanilla quebrar o
     * bloco normalmente - o que elimina o ghost block (o Paper reenvia o bloco original
     * pro quebrador quando o evento TERMINA cancelado) e o kick de "floating too long"
     * (remocao sem fisica). A chave e a posicao do bloco; o par LOWEST/HIGHEST sempre se
     * equilibra na mesma dispatch do evento, entao a entrada nunca fica pendurada.
     */
    private final Set<String> softCancelledBreaks = ConcurrentHashMap.newKeySet();

    private final MineManager mineManager;
    private final PlayerDataManager playerDataManager;
    private final PickaxeLevelManager levelManager;
    private final PrivateMineManager privateMineManager;
    private final Supplier<Optional<AlkaShopHook>> shopHookSupplier;
    private final Supplier<Optional<McMMOHook>> mcmmoHookSupplier;
    private final Supplier<Optional<AdvancedEnchantmentsHook>> aeHookSupplier;
    /**
     * Supplier, NAO um Optional fixo - o hook e resolvido de verdade so na PRIMEIRA
     * chamada apos o servidor terminar de habilitar todos os plugins (ver
     * AlkaMines#onEnable), nao durante o onEnable desta classe. softdepend no
     * plugin.yml nao garante ordem estrita de enable com muitos plugins/dependencias
     * cruzadas no servidor - resolver o hook cedo demais (direto no onEnable) pode
     * rodar ANTES do AlkaDrop registrar a propria API no ServicesManager, mesmo com
     * os dois plugins presentes e saudaveis, deixando o hook permanentemente vazio
     * pro resto da sessao. Bug real encontrado 2026-08-09 (AlkaMines habilitava
     * antes do AlkaDrop no log do servidor, apesar do softdepend).
     */
    private final Supplier<Optional<AlkaDropHook>> dropHookSupplier;
    private final Supplier<Optional<com.alka.mines.hook.AlkaVipsHook>> vipsHookSupplier;

    public MineBreakListener(MineManager mineManager, PlayerDataManager playerDataManager,
                              PickaxeLevelManager levelManager, PrivateMineManager privateMineManager,
                              Supplier<Optional<AlkaShopHook>> shopHookSupplier,
                              Supplier<Optional<McMMOHook>> mcmmoHookSupplier,
                              Supplier<Optional<AdvancedEnchantmentsHook>> aeHookSupplier,
                              Supplier<Optional<AlkaDropHook>> dropHookSupplier,
                              Supplier<Optional<com.alka.mines.hook.AlkaVipsHook>> vipsHookSupplier) {
        this.mineManager = mineManager;
        this.playerDataManager = playerDataManager;
        this.levelManager = levelManager;
        this.privateMineManager = privateMineManager;
        this.shopHookSupplier = shopHookSupplier;
        this.mcmmoHookSupplier = mcmmoHookSupplier;
        this.aeHookSupplier = aeHookSupplier;
        this.dropHookSupplier = dropHookSupplier;
        this.vipsHookSupplier = vipsHookSupplier;
    }

    /**
     * LOWEST: marca a quebra de um bloco de composicao de mina como cancelado pro resto
     * do evento - mcMMO (e qualquer outro listener com ignoreCancelled=true) ve o evento
     * cancelado e nao concede XP/drops nativos em cima do que o AlkaMines entrega. Nao
     * e cancelamento definitivo: o {@link #onBreak} (HIGHEST) re-ativa e o vanilla quebra
     * normal. Bloco fora da composicao nem entra aqui - quem bloqueia e o
     * MineProtectionListener.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreakPre(BlockBreakEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        Block block = event.getBlock();
        Mine mine = mineManager.getMineAt(block.getLocation()).orElse(null);
        if (mine != null && mine.isResetting()) {
            // FAWE esta escrevendo essa regiao AGORA (reset em voo) - nunca deixar o
            // vanilla quebrar um bloco que pode estar sendo sobrescrito nesse exato
            // instante em outra thread. Resincroniza o bloco pro jogador pra desfazer
            // qualquer previsao de quebra que o client ja tenha feito (Paper 1.21+).
            event.setCancelled(true);
            player.sendBlockChange(block.getLocation(), block.getBlockData());
            return;
        }
        if (mine == null) {
            // mina particular: a plot INTEIRA e protegida - so blocos da composicao do
            // template quebram (paredes/decoracao ficam intransponiveis).
            PrivateMine privateMine = privateMineManager != null
                    ? privateMineManager.getMineProtectingAt(block.getLocation()).orElse(null) : null;
            if (privateMine != null && privateMine.isResetting()) {
                event.setCancelled(true);
                player.sendBlockChange(block.getLocation(), block.getBlockData());
                return;
            }
            if (privateMine != null) {
                MineTemplate template = privateMineManager.getTemplate(privateMine.getTemplateId()).orElse(null);
                // so quebra o que e minerio E esta na composicao do template - qualquer
                // outro Material (pedra/decor/paredes que o admin construiu) nunca quebra,
                // isso ja protege a construcao inteira sem precisar de nenhuma regra de
                // "borda" adicional (o volume mineravel e so o bounding box do minerio
                // detectado, entao excluir a borda dele excluiria minerio de verdade -
                // ver [[project-alkamines]]).
                boolean breakable = template != null
                        && template.getCompositionBlock(block.getType()) != null
                        && privateMineManager.isMinable(block.getType());
                if (!breakable) {
                    event.setCancelled(true);
                    return;
                }
                event.setCancelled(true);
                softCancelledBreaks.add(blockKey(block));
            }
            return;
        }
        if (resolveCompositionBlock(mine, block) == null) {
            return;
        }

        event.setCancelled(true);
        softCancelledBreaks.add(blockKey(block));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!softCancelledBreaks.remove(blockKey(block))) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        Mine mine = mineManager.getMineAt(block.getLocation()).orElse(null);
        PrivateMine privateMine = mine == null
                ? privateMineManager.getMineProtectingAt(block.getLocation()).orElse(null) : null;
        if (mine == null && privateMine == null) {
            return;
        }

        // API: MineBlockBreakEvent cancelavel (so minas publicas, que tem Mine) - se
        // cancelado, o evento de quebra fica cancelado e o bloco permanece no lugar.
        if (mine != null) {
            MineBlockBreakEvent breakEvent = new MineBlockBreakEvent(player, mine, block);
            Bukkit.getPluginManager().callEvent(breakEvent);
            if (breakEvent.isCancelled()) {
                event.setCancelled(true);
                return;
            }
        }

        // re-ativa a quebra pro vanilla processar (fisica, som, particula, broadcast) -
        // o bloco so e removido porque o evento NAO termina cancelado.
        event.setCancelled(false);

        DebugLogger.log("Break: %s quebrou %s em %d,%d,%d (mina '%s').",
                player.getName(), block.getType(), block.getX(), block.getY(), block.getZ(),
                mine != null ? mine.getId() : "privada:" + privateMine.getOwner());

        // Resolvido por chamada, nao no construtor - ver javadoc de dropHookSupplier.
        Optional<AlkaDropHook> dropHook = dropHookSupplier.get();

        // AdvancedEnchantments processa BlockBreakEvent em HIGH - precisa rodar ANTES
        // de cancelarmos o evento em HIGHEST, senao os efeitos/encantamentos dele nunca disparam.
        aeHookSupplier.get().ifPresent(hook -> hook.processBlockBreak(player, block));

        // entrada da composicao que corresponde a este bloco (null se nao configurado) -
        // traz os overrides de %/XP definidos direto no BlockCompositionMenu. Precisa
        // ser resolvido AGORA, antes de qualquer remocao (usa o Material original).
        MineBlock compositionBlock = mine != null ? resolveCompositionBlock(mine, block) : null;
        if (compositionBlock == null && privateMine != null) {
            compositionBlock = privateMineManager.getTemplate(privateMine.getTemplateId())
                    .flatMap(t -> Optional.ofNullable(t.getCompositionBlock(block.getType())))
                    .orElse(null);
        }

        // drops calculados com o bloco ainda no tipo original, ANTES de cancelar/remover.
        // Lista mutavel (nao a Collection crua de getDrops) porque o hook do AlkaDrop
        // precisa poder substituir itens no lugar (ex: RAW_IRON -> IRON_INGOT).
        ItemStack tool = player.getInventory().getItemInMainHand();
        List<ItemStack> drops = new ArrayList<>(block.getDrops(tool));
        boolean silkTouch = tool.containsEnchantment(Enchantment.SILK_TOUCH);

        // O evento foi soft-cancelado em LOWEST e re-ativado aqui em HIGHEST - termina
        // NAO cancelado, entao o vanilla quebra o bloco (fisica, som, particula e
        // broadcast pra todos os jogadores), sem ghost block nem kick de "floating".
        // So suprimimos drops/XP nativos e entregamos os nossos abaixo.
        event.setDropItems(false);
        event.setExpToDrop(0);

        // Resincroniza AIR explicitamente pra qualquer jogador proximo, redundante ao
        // pacote que o vanilla ja deveria mandar sozinho (o evento termina nao-cancelado
        // logo acima). Visto em teste real: um OP quebrava o bloco de verdade (servidor
        // ja ficava com AIR - por isso o OP conseguia) mas outro jogador continuava
        // vendo o bloco solido ate relogar - classico pacote de atualizacao que nao
        // chegou em algum client, nao corrupcao de dado (relog corrigiu, o que so
        // acontece se o dado do servidor ja estava certo). Custa quase nada e fecha
        // esse gap especifico sem depender so do broadcast interno do Paper.
        Location breakLoc = block.getLocation();
        var airData = Material.AIR.createBlockData();
        for (Player nearby : block.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(breakLoc) <= 64.0 * 64.0) {
                nearby.sendBlockChange(breakLoc, airData);
            }
        }

        if (mine != null) {
            applyRewards(player, block, mine, drops);
        }

        applyXp(player, block, compositionBlock);

        if (mine != null) {
            trackProgress(player, mine);
        } else {
            trackPrivateProgress(player, privateMine);
        }
        DebugLogger.log("Break ok: restantes=%d (drops=%d, vendido=%s).",
                mine != null ? mine.getBlocksRemaining() : privateMine.getBlocksRemaining(), drops.size(),
                shopHookSupplier.get().isPresent() ? "sim" : "nao");

        // Auto-smelt do AlkaDrop (se o jogador tiver ativo) processa o drop ANTES de
        // dar/vender - assim a auto-venda do AlkaShop logo abaixo ja vende pelo preco
        // do ingot, nao do minerio bruto. AlkaDropHook nunca lanca (ver javadoc dele).
        dropHook.ifPresent(hook -> hook.trySmelt(player, drops, silkTouch));

        giveOrSellDrops(player, drops, block.getLocation(), dropHook);

        // Auto-condensar do AlkaDrop roda DEPOIS, sobre o inventario inteiro do
        // jogador (nao so o que acabou de ser minerado) - ver CondenseManager#
        // condenseInventory no AlkaDrop pro motivo (acumula corretamente entre
        // blocos diferentes minerados um de cada vez).
        dropHook.ifPresent(hook -> hook.tryCondenseInventory(player));
    }

    /** Random rewards da mina (estilo AxMines, reimplementado): itens + comandos +
     * prevent-drops. Roda no bloco quebrado; preventDrops limpa os drops normais. */
    private void applyRewards(Player player, Block block, Mine mine, List<ItemStack> drops) {
        if (mine.getRewards().isEmpty()) {
            return;
        }

        boolean preventDrops = false;
        List<ItemStack> rewardItems = new ArrayList<>();
        List<String> rewardCommands = new ArrayList<>();

        // Boost de servidor do AlkaVips ("VIP Solidario") amplifica a chance efetiva de
        // reward - nunca sobe a chance base configurada pelo admin, so multiplica na hora
        // da rolagem. 1.0 se nenhum boost ativo/AlkaVips ausente (getMineRareChanceMultiplier nunca lanca).
        double rareChanceMultiplier = vipsHookSupplier.get()
                .map(com.alka.mines.hook.AlkaVipsHook::getMineRareChanceMultiplier).orElse(1.0);

        for (MineReward reward : mine.getRewards()) {
            if (!reward.matches(block.getType())) {
                continue;
            }
            if (ThreadLocalRandom.current().nextDouble() * 100.0 < reward.getChance() * rareChanceMultiplier) {
                rewardItems.addAll(reward.getItems());
                rewardCommands.addAll(reward.getCommands());
                preventDrops |= reward.isPreventDrops();
            }
        }

        for (String command : rewardCommands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("<player>", player.getName()));
        }
        for (ItemStack item : rewardItems) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
            for (ItemStack extra : leftover.values()) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), extra);
            }
        }
        if (preventDrops) {
            drops.clear();
        }
    }

    private void trackProgress(Player player, Mine mine) {
        mine.setBlocksRemaining(Math.max(0, mine.getBlocksRemaining() - 1));
        // so marca pra persistir no proximo saveDirty (autosave periodico/onDisable) - chamar
        // mineManager.save() aqui reescreveria o mines.yml inteiro a cada bloco quebrado.
        mineManager.markDirty(mine.getId());

        PlayerMineData data = playerDataManager.get(player.getUniqueId());
        data.incrementBlocksBroken();

        if (data.recalculateLevel(levelManager.getThresholds())) {
            announceLevelUp(player, data);
        }
    }

    /** Progresso de quebra numa mina PARTICULAR (contagem in-memory + XP de picareta). */
    private void trackPrivateProgress(Player player, PrivateMine mine) {
        mine.setBlocksRemaining(Math.max(0, mine.getBlocksRemaining() - 1));
        PlayerMineData data = playerDataManager.get(player.getUniqueId());
        data.incrementBlocksBroken();

        if (data.recalculateLevel(levelManager.getThresholds())) {
            announceLevelUp(player, data);
        }
    }

    private void applyXp(Player player, Block block, MineBlock compositionBlock) {
        mcmmoHookSupplier.get().ifPresent(hook -> hook.addMiningXp(player, block, compositionBlock));
        if (compositionBlock != null && compositionBlock.getNormalXp() > 0) {
            player.giveExp((int) Math.round(compositionBlock.getNormalXp()));
        }
    }

    /** Acha a entrada da composicao correspondente ao Material do bloco quebrado. */
    private MineBlock resolveCompositionBlock(Mine mine, Block block) {
        for (MineBlock candidate : mine.getComposition()) {
            if (candidate.getMaterial() == block.getType()) {
                return candidate;
            }
        }
        return null;
    }

    /** Chave unica de posicao de bloco usada no soft-cancel (mundo + X/Y/Z). */
    private static String blockKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + "," + block.getY() + "," + block.getZ();
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

    private void giveOrSellDrops(Player player, Collection<ItemStack> drops, Location dropLocation,
                                  Optional<AlkaDropHook> dropHook) {
        Map<String, Double> soldTotals = new LinkedHashMap<>();
        List<ItemStack> toDeliver = new ArrayList<>();
        Optional<AlkaShopHook> shopHook = shopHookSupplier.get();

        for (ItemStack drop : drops) {
            boolean autoSell = shopHook.isPresent() && shopHook.get().isAutoSellActive(player, drop.getType());
            if (autoSell && shopHook.get().isSellable(drop.getType())) {
                Map<String, Double> totals = shopHook.get().sell(player, drop);
                for (Map.Entry<String, Double> entry : totals.entrySet()) {
                    soldTotals.merge(entry.getKey(), entry.getValue(), Double::sum);
                }
                if (!totals.isEmpty()) {
                    continue;
                }
            }
            toDeliver.add(drop);
        }

        // Entrega o que nao foi vendido - via AlkaDrop se presente (respeita o
        // toggle de coleta do jogador: inventario ou chao). Sem AlkaDrop, a mina
        // NAO insere direto no inventario - solta no chao como o vanilla faz ao
        // minerar, o jogador pega andando em cima (pickup padrao do Bukkit).
        if (!toDeliver.isEmpty()) {
            if (dropHook.isPresent()) {
                dropHook.get().deliverDrops(player, toDeliver, dropLocation);
            } else {
                for (ItemStack drop : toDeliver) {
                    player.getWorld().dropItemNaturally(dropLocation, drop);
                }
            }
        }

        if (!soldTotals.isEmpty()) {
            shopHook.ifPresent(hook -> hook.notifyAutoSell(player, soldTotals));
        }
    }
}
