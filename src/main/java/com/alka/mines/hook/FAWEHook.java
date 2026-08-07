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

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Substitui os blocos de uma regiao via FAWE - EditSession + RandomPattern ponderado pelo peso de cada MineBlock. */
public final class FAWEHook {

    private FAWEHook() {
    }

    public static void resetRegion(MineRegion mineRegion, List<MineBlock> composition) {
        World bukkitWorld = Bukkit.getWorld(mineRegion.getWorld());
        if (bukkitWorld == null) {
            Logger.getLogger("AlkaMines").warning("Mundo '" + mineRegion.getWorld() + "' nao esta carregado - reset abortado.");
            return;
        }

        // FAWE nao sabe colocar blocos custom do ItemsAdder (nao sao blocos vanilla de
        // verdade) - se a composicao tiver algum, cai pro reset bloco-a-bloco via Bukkit.
        // Mais lento, mas e o unico jeito de misturar os dois tipos de bloco.
        if (composition.stream().anyMatch(MineBlock::isCustomBlock)) {
            resetWithCustomBlocks(bukkitWorld, mineRegion, composition);
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
    }

    private static void resetWithCustomBlocks(World world, MineRegion region, List<MineBlock> composition) {
        double totalWeight = composition.stream().mapToDouble(MineBlock::getWeight).sum();

        for (int x = region.getX1(); x <= region.getX2(); x++) {
            for (int y = region.getY1(); y <= region.getY2(); y++) {
                for (int z = region.getZ1(); z <= region.getZ2(); z++) {
                    MineBlock chosen = chooseRandomBlock(composition, totalWeight);
                    Location location = new Location(world, x, y, z);

                    if (chosen.isCustomBlock() && ItemsAdderHook.isEnabled()) {
                        ItemsAdderHook.placeCustomBlock(chosen.getCustomBlockId(), location);
                    } else {
                        location.getBlock().setType(chosen.getMaterial(), false);
                    }
                }
            }
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
