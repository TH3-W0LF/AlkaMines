package com.alka.mines.hook;

import com.alka.mines.model.MineBlock;
import com.alka.mines.model.MineRegion;
import com.alka.mines.util.DebugLogger;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.pattern.BlockPattern;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.function.pattern.RandomPattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Operacoes FAWE do AlkaMines: substituir blocos de uma regiao (RandomPattern ponderado
 * pelo peso de cada MineBlock), limpar regiao (AIR) e colar schematic (minas particulares). */
public final class FAWEHook {

    private FAWEHook() {
    }

    public static void resetRegion(MineRegion mineRegion, List<MineBlock> composition) {
        RandomPattern pattern = new RandomPattern();
        for (MineBlock block : composition) {
            BlockState state = BukkitAdapter.adapt(block.getMaterial().createBlockData());
            pattern.add(new BlockPattern(state), block.getWeight());
        }
        if (composition.isEmpty()) {
            // rede de seguranca - mina sem composicao configurada nunca deveria ficar "vazia" no reset.
            pattern.add(new BlockPattern(BukkitAdapter.adapt(Material.STONE.createBlockData())), 1.0);
        }
        setBlocks(mineRegion, pattern);
    }

    /** Preenche a regiao inteira com ar - usado ao deletar uma mina particular. */
    public static void clearRegion(MineRegion mineRegion) {
        setBlocks(mineRegion, new BlockPattern(BukkitAdapter.adapt(Material.AIR.createBlockData())));
    }

    private static void setBlocks(MineRegion mineRegion, Pattern pattern) {
        World bukkitWorld = Bukkit.getWorld(mineRegion.getWorld());
        if (bukkitWorld == null) {
            Logger.getLogger("AlkaMines").warning("Mundo '" + mineRegion.getWorld() + "' nao esta carregado.");
            return;
        }

        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(bukkitWorld);
        Region region = new CuboidRegion(weWorld,
                BlockVector3.at(mineRegion.getX1(), mineRegion.getY1(), mineRegion.getZ1()),
                BlockVector3.at(mineRegion.getX2(), mineRegion.getY2(), mineRegion.getZ2()));

        long start = System.nanoTime();
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(weWorld)
                .fastMode(true)
                .limitUnlimited()
                .build()) {
            editSession.setBlocks(region, pattern);
        } catch (MaxChangedBlocksException e) {
            // nao deveria disparar com limitUnlimited(), mas protege contra limite forcado externamente.
            Logger.getLogger("AlkaMines").log(Level.WARNING, "Operacao FAWE interrompida por limite de blocos.", e);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        DebugLogger.log("FAWE setBlocks: %d blocos em '%s' em %d ms.",
                mineRegion.getVolume(), mineRegion.getWorld(), elapsedMs);
    }

    /** Cola um schematic .schem alinhado ao `origin` (canto minimo do schematic vai pra
     * origin) e devolve o tamanho colado pra varredura de marcadores. */
    public static Optional<SchematicPaste> pasteSchematic(File file, Location origin) {
        if (!file.exists()) {
            return Optional.empty();
        }
        World bukkitWorld = origin.getWorld();
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(bukkitWorld);
        try (FileInputStream in = new FileInputStream(file)) {
            var format = ClipboardFormats.findByFile(file);
            if (format == null) {
                Logger.getLogger("AlkaMines").warning("Formato de schematic nao reconhecido: " + file.getName());
                return Optional.empty();
            }
            try (ClipboardReader reader = format.getReader(in)) {
                Clipboard clipboard = reader.read();
                try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                        .world(weWorld)
                        .fastMode(true)
                        .limitUnlimited()
                        .build()) {
                    BlockVector3 to = BlockVector3.at(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());
                    Operation operation = new ForwardExtentCopy(
                            clipboard, clipboard.getRegion(), clipboard.getOrigin(), editSession, to);
                    Operations.complete(operation);
                }
                BlockVector3 dimensions = clipboard.getDimensions();
                return Optional.of(new SchematicPaste(origin,
                        dimensions.getX(), dimensions.getY(), dimensions.getZ()));
            }
        } catch (Throwable t) {
            Logger.getLogger("AlkaMines").log(Level.WARNING, "Falha ao colar schematic " + file.getName(), t);
            return Optional.empty();
        }
    }

    /** Resultado do paste: origem (canto minimo colado) + tamanho do cuboide colado. */
    public record SchematicPaste(Location origin, int sizeX, int sizeY, int sizeZ) {
    }

    /** Salva a selecao do WorldEdit do jogador como um schematic .schem (minas particulares:
     * o admin constroi, seleciona com //wand e o plugin guarda a mina sem depender de //copy).
     * Devolve o minY da selecao (alinhamento do paste) e a composicao detectada dos blocos. */
    public static SchematicSaveResult saveSelectionToSchematic(Player player, File outFile, Material marker) {
        try {
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(player.getWorld());
            // get() cria a sessao se ainda nao existir (findByName pode voltar null no Paper).
            LocalSession session = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player));
            Region region = session.getSelection(weWorld);
            int minY = region.getMinimumPoint().getY();
            BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
            try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                    .world(weWorld)
                    .limitUnlimited()
                    .build()) {
                ForwardExtentCopy copy = new ForwardExtentCopy(
                        editSession, region, clipboard, region.getMinimumPoint());
                Operations.complete(copy);
            }

            // composicao detectada dos blocos do schematic (interior entre marcadores, ou
            // a selecao inteira se nao houver marcadores) - o admin nao precisa editar yml.
            List<MineBlock> composition = detectComposition(clipboard, marker);

            // findByFile LANCIA NoSuchFileException em arquivo que ainda nao existe
            // (e chamado antes de criar o de saida) - findByAlias por extensao nao precisa do arquivo.
            var format = ClipboardFormats.findByAlias("schem");
            if (format == null) {
                format = ClipboardFormats.findByFile(outFile);
            }
            if (format == null) {
                Logger.getLogger("AlkaMines").warning("Nenhum formato de schematic suportado para .schem (FAWE?).");
                return new SchematicSaveResult(false, 0, List.of());
            }
            try (FileOutputStream out = new FileOutputStream(outFile);
                 ClipboardWriter writer = format.getWriter(out)) {
                writer.write(clipboard);
            }
            return new SchematicSaveResult(true, minY, composition);
        } catch (Throwable t) {
            Logger.getLogger("AlkaMines").log(Level.WARNING, "Falha ao salvar schematic " + outFile.getName(), t);
            return new SchematicSaveResult(false, 0, List.of());
        }
    }

    /** Conta os blocos do schematic dentro do volume mineravel (o cuboide entre os blocos
     * marcadores, ou a selecao inteira se nao houver marcadores) e monta a composicao com
     * peso proporcional a quantidade - exclui ar e os marcadores. */
    private static List<MineBlock> detectComposition(Clipboard clipboard, Material marker) {
        com.sk89q.worldedit.world.block.BlockType markerType =
                BukkitAdapter.adapt(marker.createBlockData()).getBlockType();
        BlockVector3 min = clipboard.getRegion().getMinimumPoint();
        BlockVector3 max = clipboard.getRegion().getMaximumPoint();

        // 1o passe: acha os marcadores pra definir o box do volume mineravel
        int mMinX = Integer.MAX_VALUE, mMinY = Integer.MAX_VALUE, mMinZ = Integer.MAX_VALUE;
        int mMaxX = Integer.MIN_VALUE, mMaxY = Integer.MIN_VALUE, mMaxZ = Integer.MIN_VALUE;
        int markerCount = 0;
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    if (clipboard.getBlock(x, y, z).getBlockType() == markerType) {
                        markerCount++;
                        mMinX = Math.min(mMinX, x); mMinY = Math.min(mMinY, y); mMinZ = Math.min(mMinZ, z);
                        mMaxX = Math.max(mMaxX, x); mMaxY = Math.max(mMaxY, y); mMaxZ = Math.max(mMaxZ, z);
                    }
                }
            }
        }

        int boxMinX, boxMinY, boxMinZ, boxMaxX, boxMaxY, boxMaxZ;
        if (markerCount >= 2) {
            boxMinX = mMinX; boxMinY = mMinY; boxMinZ = mMinZ;
            boxMaxX = mMaxX; boxMaxY = mMaxY; boxMaxZ = mMaxZ;
        } else {
            boxMinX = min.getX(); boxMinY = min.getY(); boxMinZ = min.getZ();
            boxMaxX = max.getX(); boxMaxY = max.getY(); boxMaxZ = max.getZ();
        }

        // 2o passe: conta os blocos (nao-ar, nao-marcador) dentro do box
        Map<String, Long> counts = new java.util.HashMap<>();
        for (int x = boxMinX; x <= boxMaxX; x++) {
            for (int y = boxMinY; y <= boxMaxY; y++) {
                for (int z = boxMinZ; z <= boxMaxZ; z++) {
                    var type = clipboard.getBlock(x, y, z).getBlockType();
                    if (type.getId().equals("minecraft:air") || type == markerType) {
                        continue;
                    }
                    counts.merge(type.getId(), 1L, Long::sum);
                }
            }
        }

        List<MineBlock> composition = new java.util.ArrayList<>();
        counts.forEach((id, count) -> {
            Material material = Material.matchMaterial(id);
            if (material != null) {
                composition.add(new MineBlock(material, count));
            }
        });
        return composition;
    }

    /** Resultado do save: sucesso + minY da selecao (alinhamento do paste) + composicao
     * detectada dos blocos do schematic. */
    public record SchematicSaveResult(boolean success, int minY, List<MineBlock> composition) {
    }

    /** Le so as dimensoes do schematic (.schem) sem colar nada - usado pra centralizar o
     * paste na plot (o centro do schematic fica no centro da plot). */
    public static Optional<BlockVector3> getSchematicDimensions(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            var format = ClipboardFormats.findByFile(file);
            if (format == null) {
                format = ClipboardFormats.findByAlias("schem");
            }
            if (format == null) {
                return Optional.empty();
            }
            try (ClipboardReader reader = format.getReader(in)) {
                return Optional.of(reader.read().getDimensions());
            }
        } catch (Throwable t) {
            Logger.getLogger("AlkaMines").log(Level.WARNING, "Falha ao ler dimensoes do schematic " + file.getName(), t);
            return Optional.empty();
        }
    }

    /** Salva o estado atual de uma regiao (ex: a plot inteira) como schematic .schem -
     * backup do estado original da plot antes de colar a mina, pra restaurar no delete. */
    public static boolean saveRegionToSchematic(File outFile, MineRegion region) {
        try {
            World bukkitWorld = Bukkit.getWorld(region.getWorld());
            if (bukkitWorld == null) {
                return false;
            }
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(bukkitWorld);
            Region weRegion = new CuboidRegion(weWorld,
                    BlockVector3.at(region.getX1(), region.getY1(), region.getZ1()),
                    BlockVector3.at(region.getX2(), region.getY2(), region.getZ2()));
            BlockArrayClipboard clipboard = new BlockArrayClipboard(weRegion);
            try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                    .world(weWorld)
                    .limitUnlimited()
                    .build()) {
                ForwardExtentCopy copy = new ForwardExtentCopy(
                        editSession, weRegion, clipboard, weRegion.getMinimumPoint());
                Operations.complete(copy);
            }

            // findByFile LANCIA NoSuchFileException em arquivo que ainda nao existe
            // (e chamado antes de criar o de saida) - findByAlias por extensao nao precisa do arquivo.
            var format = ClipboardFormats.findByAlias("schem");
            if (format == null) {
                format = ClipboardFormats.findByFile(outFile);
            }
            if (format == null) {
                return false;
            }
            try (FileOutputStream out = new FileOutputStream(outFile);
                 ClipboardWriter writer = format.getWriter(out)) {
                writer.write(clipboard);
            }
            return true;
        } catch (Throwable t) {
            Logger.getLogger("AlkaMines").log(Level.WARNING, "Falha ao salvar backup da plot", t);
            return false;
        }
    }
}
