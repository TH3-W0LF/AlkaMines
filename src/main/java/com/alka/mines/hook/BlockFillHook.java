package com.alka.mines.hook;

import com.alka.mines.model.MineBlock;
import com.alka.mines.model.MineRegion;
import com.alka.mines.util.DebugLogger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Preenche regiao de mina com composicao aleatoria ponderada - SEM FAWE. Escreve direto via
 * {@code World#setBlockData(x,y,z,data,false)}, sincrono, sempre na main thread, sem fila
 * assincrona nenhuma (inspirado no AxMines - https://github.com/Artillex-Studios/AxMines -
 * que resolve exatamente esse problema sem depender de WorldEdit/FAWE). Isso elimina de vez a
 * categoria inteira de bug que o FAWE causava aqui (EditSession assincrono concorrente com
 * quebra de bloco do jogador, chunk desatualizado no cliente, excecao engolida deixando a
 * mina travada) - nao ha janela de tempo onde outra thread mexe na mesma regiao, porque so
 * existe UMA thread envolvida do inicio ao fim do fill.
 *
 * {@code applyPhysics=false} evita cascata de fisica (areia caindo, redstone atualizando) por
 * bloco - mesmo motivo do FAWE fastMode. Escrita direta via API padrao do Bukkit (nao um
 * bypass tipo NMS cru) ja dispara os pacotes de atualizacao de chunk corretamente pros
 * clientes; ainda assim chama {@code World#refreshChunk} no final de cada operacao, mesma
 * rede de seguranca que o FAWEHook antigo tinha.
 *
 * FAWE continua sendo usado (ver {@link FAWEHook}) SO pra colar/salvar schematic (minas
 * particulares com parede/decoracao) - isso e um uso genuino de "clipboard", nao cabe aqui.
 */
public final class BlockFillHook {

    private BlockFillHook() {
    }

    /** Preenche a regiao inteira com composicao aleatoria - sem preservar nada. */
    public static void fillRegion(MineRegion region, List<MineBlock> composition) {
        fill(region, composition, null, null);
    }

    /** So substitui blocos que sao AIR ou ja fazem parte da composicao - preserva paredes/decor. */
    public static void fillRegionPreserving(MineRegion region, List<MineBlock> composition) {
        fill(region, composition, preserveMask(composition), null);
    }

    /** Preenche APENAS a "casca" nova de uma expansao (fora do bounding box X/Z antigo). */
    public static void fillRegionOuter(MineRegion newRegion, int oldMinX, int oldMaxX, int oldMinZ, int oldMaxZ,
                                        List<MineBlock> composition) {
        fill(newRegion, composition, null, outerFilter(oldMinX, oldMaxX, oldMinZ, oldMaxZ));
    }

    /** Igual {@link #fillRegionOuter}, mas preservando parede/decor dentro da casca nova. */
    public static void fillRegionOuterPreserving(MineRegion newRegion, int oldMinX, int oldMaxX, int oldMinZ, int oldMaxZ,
                                                  List<MineBlock> composition) {
        fill(newRegion, composition, preserveMask(composition), outerFilter(oldMinX, oldMaxX, oldMinZ, oldMaxZ));
    }

    /** Preenche a regiao inteira com AR - usado ao deletar uma mina particular. */
    public static void clearRegion(MineRegion region) {
        fill(region, List.of(new MineBlock(Material.AIR, 1.0)), null, null);
    }

    private static Set<Material> preserveMask(List<MineBlock> composition) {
        Set<Material> mask = EnumSet.of(Material.AIR);
        for (MineBlock block : composition) {
            mask.add(block.getMaterial());
        }
        return mask;
    }

    /** true = coordenada X/Z esta FORA do bounding box antigo (faz parte da casca nova). */
    private static OuterFilter outerFilter(int oldMinX, int oldMaxX, int oldMinZ, int oldMaxZ) {
        return (x, z) -> x < oldMinX || x > oldMaxX || z < oldMinZ || z > oldMaxZ;
    }

    @FunctionalInterface
    private interface OuterFilter {
        boolean isOuter(int x, int z);
    }

    private static void fill(MineRegion region, List<MineBlock> composition, Set<Material> preserveMask, OuterFilter outerFilter) {
        World world = Bukkit.getWorld(region.getWorld());
        if (world == null) {
            Logger.getLogger("AlkaMines").warning("Mundo '" + region.getWorld() + "' nao esta carregado.");
            return;
        }

        WeightedComposition sampler = new WeightedComposition(composition);
        long start = System.nanoTime();
        long written = 0;

        int chunkMinX = region.getX1() >> 4;
        int chunkMaxX = region.getX2() >> 4;
        int chunkMinZ = region.getZ1() >> 4;
        int chunkMaxZ = region.getZ2() >> 4;

        for (int chunkX = chunkMinX; chunkX <= chunkMaxX; chunkX++) {
            int minX = Math.max(region.getX1(), chunkX << 4);
            int maxX = Math.min(region.getX2(), (chunkX << 4) + 15);

            for (int chunkZ = chunkMinZ; chunkZ <= chunkMaxZ; chunkZ++) {
                int minZ = Math.max(region.getZ1(), chunkZ << 4);
                int maxZ = Math.min(region.getZ2(), (chunkZ << 4) + 15);

                for (int y = region.getY1(); y <= region.getY2(); y++) {
                    for (int x = minX; x <= maxX; x++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            if (outerFilter != null && !outerFilter.isOuter(x, z)) {
                                continue;
                            }
                            if (preserveMask != null) {
                                Material current = world.getBlockAt(x, y, z).getType();
                                if (!preserveMask.contains(current)) {
                                    continue;
                                }
                            }
                            world.getBlockAt(x, y, z).setBlockData(sampler.sample(), false);
                            written++;
                        }
                    }
                }
            }
        }

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        DebugLogger.log("BlockFillHook: %d bloco(s) escritos em '%s' em %d ms.", written, region.getWorld(), elapsedMs);

        for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
            for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
                if (world.isChunkLoaded(cx, cz)) {
                    world.refreshChunk(cx, cz);
                }
            }
        }
    }

    /** Amostragem ponderada da composicao (equivalente ao RandomPattern do FAWE). */
    private static final class WeightedComposition {
        private final Material[] materials;
        private final BlockData[] blockData;
        private final double[] cumulativeWeights;
        private final double totalWeight;

        WeightedComposition(List<MineBlock> composition) {
            List<MineBlock> effective = composition.isEmpty()
                    ? List.of(new MineBlock(Material.STONE, 1.0)) // rede de seguranca - nunca fica "vazio"
                    : composition;

            materials = new Material[effective.size()];
            blockData = new BlockData[effective.size()];
            cumulativeWeights = new double[effective.size()];
            double sum = 0;
            for (int i = 0; i < effective.size(); i++) {
                MineBlock block = effective.get(i);
                materials[i] = block.getMaterial();
                blockData[i] = block.getMaterial().createBlockData();
                sum += Math.max(0, block.getWeight());
                cumulativeWeights[i] = sum;
            }
            totalWeight = sum > 0 ? sum : 1.0;
        }

        BlockData sample() {
            double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
            for (int i = 0; i < cumulativeWeights.length; i++) {
                if (roll <= cumulativeWeights[i]) {
                    return blockData[i];
                }
            }
            return blockData[blockData.length - 1];
        }
    }
}
