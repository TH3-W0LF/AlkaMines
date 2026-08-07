package com.alka.mines.hook;

import com.alka.mines.model.MineBlock;
import com.alka.mines.model.MineRegion;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.function.pattern.BlockPattern;
import com.sk89q.worldedit.function.pattern.RandomPattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Substitui os blocos de uma regiao via FAWE (composicao 100% vanilla) ou, se houver
 * bloco custom do ItemsAdder na composicao, bloco-a-bloco via Bukkit/ItemsAdder API. */
public final class FAWEHook {

    // CustomBlock.place()/Block#setType sao chamadas de API sincronas relativamente
    // pesadas (nao e um simples write de array como o setBlocks do FAWE) - uma mina
    // grande inteira num unico tick trava o watchdog do servidor. Espalhar em lotes
    // por tick e o jeito padrao de evitar isso sem sair da main thread (exigida pelo
    // ItemsAdder pra CustomBlock.place).
    private static final int BATCH_SIZE = 800;

    private FAWEHook() {
    }

    /** onComplete roda na main thread depois do ultimo bloco trocado - pode ser
     * chamado ja no mesmo tick (composicao 100% vanilla, FAWE resolve tudo de uma vez)
     * ou so alguns ticks depois (composicao com bloco custom, ver resetWithCustomBlocks). */
    public static void resetRegion(JavaPlugin plugin, MineRegion mineRegion, List<MineBlock> composition, Runnable onComplete) {
        World bukkitWorld = Bukkit.getWorld(mineRegion.getWorld());
        if (bukkitWorld == null) {
            Logger.getLogger("AlkaMines").warning("Mundo '" + mineRegion.getWorld() + "' nao esta carregado - reset abortado.");
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        // FAWE nao sabe colocar blocos custom do ItemsAdder (nao sao blocos vanilla de
        // verdade) - se a composicao tiver algum, cai pro reset bloco-a-bloco via Bukkit.
        // Mais lento, mas e o unico jeito de misturar os dois tipos de bloco.
        if (composition.stream().anyMatch(MineBlock::isCustomBlock)) {
            resetWithCustomBlocks(plugin, bukkitWorld, mineRegion, composition, onComplete);
            return;
        }

        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(bukkitWorld);

        Region region = new CuboidRegion(weWorld,
                BlockVector3.at(mineRegion.getX1(), mineRegion.getY1(), mineRegion.getZ1()),
                BlockVector3.at(mineRegion.getX2(), mineRegion.getY2(), mineRegion.getZ2()));

        RandomPattern pattern = new RandomPattern();
        for (MineBlock block : composition) {
            BlockState state = BukkitAdapter.adapt(block.getMaterial().createBlockData());
            pattern.add(new BlockPattern(state), block.getWeight());
        }
        if (composition.isEmpty()) {
            // rede de seguranca - mina sem composicao configurada nunca deveria ficar "vazia" no reset.
            pattern.add(new BlockPattern(BukkitAdapter.adapt(Material.STONE.createBlockData())), 1.0);
        }

        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(weWorld)
                .fastMode(true)
                .limitUnlimited()
                .build()) {
            editSession.setBlocks(region, pattern);
        } catch (MaxChangedBlocksException e) {
            // nao deveria disparar com limitUnlimited(), mas protege contra limite forcado externamente.
            Logger.getLogger("AlkaMines").log(Level.WARNING, "Reset interrompido por limite de blocos.", e);
        }

        if (onComplete != null) {
            onComplete.run();
        }
    }

    /** Espalha a regiao inteira em lotes de BATCH_SIZE blocos, um lote por tick, pra
     * nao travar o servidor - CustomBlock.place() exige main thread (por isso nao da
     * pra so mandar tudo numa runTaskAsynchronously) mas e pesado demais pra fazer a
     * regiao inteira num unico tick. */
    private static void resetWithCustomBlocks(JavaPlugin plugin, World world, MineRegion region,
                                               List<MineBlock> composition, Runnable onComplete) {
        double totalWeight = composition.stream().mapToDouble(MineBlock::getWeight).sum();

        List<Location> locations = new ArrayList<>();
        List<MineBlock> chosenBlocks = new ArrayList<>();
        for (int x = region.getX1(); x <= region.getX2(); x++) {
            for (int y = region.getY1(); y <= region.getY2(); y++) {
                for (int z = region.getZ1(); z <= region.getZ2(); z++) {
                    locations.add(new Location(world, x, y, z));
                    chosenBlocks.add(chooseRandomBlock(composition, totalWeight));
                }
            }
        }

        int totalBatches = (int) Math.ceil(locations.size() / (double) BATCH_SIZE);
        if (totalBatches == 0) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        for (int batch = 0; batch < totalBatches; batch++) {
            int start = batch * BATCH_SIZE;
            int end = Math.min(start + BATCH_SIZE, locations.size());
            boolean lastBatch = batch == totalBatches - 1;

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (int i = start; i < end; i++) {
                    Location location = locations.get(i);
                    MineBlock chosen = chosenBlocks.get(i);
                    if (chosen.isCustomBlock() && ItemsAdderHook.isEnabled()) {
                        ItemsAdderHook.placeCustomBlock(chosen.getCustomBlockId(), location);
                    } else {
                        location.getBlock().setType(chosen.getMaterial(), false);
                    }
                }
                if (lastBatch && onComplete != null) {
                    onComplete.run();
                }
            }, batch);
        }
    }

    private static MineBlock chooseRandomBlock(List<MineBlock> composition, double totalWeight) {
        if (composition.isEmpty()) {
            return new MineBlock(Material.STONE, 1.0);
        }
        if (composition.size() == 1) {
            return composition.get(0);
        }

        double random = Math.random() * totalWeight;
        double current = 0.0;
        for (MineBlock block : composition) {
            current += block.getWeight();
            if (random <= current) {
                return block;
            }
        }
        return composition.get(composition.size() - 1);
    }
}
