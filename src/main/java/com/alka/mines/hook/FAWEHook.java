package com.alka.mines.hook;

import com.alka.mines.model.MineBlock;
import com.alka.mines.model.MineRegion;
import com.alka.mines.util.DebugLogger;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
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
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Operacoes de schematic do AlkaMines (colar/salvar .schem pra minas particulares) - o
 * unico uso de FAWE/WorldEdit que sobra no plugin. Preencher/resetar regiao com composicao
 * aleatoria NAO usa mais FAWE - ver {@link BlockFillHook} (escrita direta via Bukkit API,
 * sincrona, sem a categoria inteira de bug que EditSession assincrono causava aqui).
 */
public final class FAWEHook {

    private FAWEHook() {
    }

    /** Cola um schematic .schem alinhado ao `origin` (canto minimo do schematic vai pra
     * origin) e devolve o tamanho colado + o Clipboard lido (pra varredura de marcadores
     * direto na memoria - ver {@link #scanOreBoundsInClipboard}, NAO ler o mundo depois do
     * paste: com fastMode(true) os blocos colados nao tem garantia de estar visiveis pro
     * Bukkit#getBlockAt imediatamente apos o EditSession fechar). */
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
                if (bukkitWorld != null) {
                    refreshChunks(bukkitWorld, origin.getBlockX(), origin.getBlockZ(),
                            origin.getBlockX() + dimensions.getX() - 1, origin.getBlockZ() + dimensions.getZ() - 1);
                }
                return Optional.of(new SchematicPaste(origin,
                        dimensions.getX(), dimensions.getY(), dimensions.getZ(), clipboard));
            }
        } catch (Throwable t) {
            Logger.getLogger("AlkaMines").log(Level.WARNING, "Falha ao colar schematic " + file.getName(), t);
            return Optional.empty();
        }
    }

    /** Resultado do paste: origem (canto minimo colado) + tamanho do cuboide colado + o
     * Clipboard lido (em memoria, continua valido depois do ClipboardReader fechar). */
    public record SchematicPaste(Location origin, int sizeX, int sizeY, int sizeZ, Clipboard clipboard) {
    }

    /** true se o BlockType e um minerio - qualquer bloco cujo id termine em "_ore"
     * (gold_ore, deepslate_gold_ore, diamond_ore, emerald_ore, iron_ore, lapis_ore,
     * redstone_ore, coal_ore, copper_ore e as variantes deepslate/nether de cada um,
     * automaticamente). Base de toda a deteccao de volume/composicao das minas
     * particulares com schematic - ver [[project-alkamines]]. */
    private static boolean isOreBlockType(com.sk89q.worldedit.world.block.BlockType type) {
        return type.getId().endsWith("_ore");
    }

    /** Varre o Clipboard (coordenadas relativas ao proprio origin do schematic) procurando
     * blocos de minerio (ver {@link #isOreBlockType}) e converte o bounding box deles pra
     * coordenadas do MUNDO usando o `pasteOrigin` real (onde o schematic foi de fato
     * colado). Le direto da memoria - ao contrario de ler o mundo apos o paste, nao depende
     * de os blocos ja estarem visiveis via Bukkit#getBlockAt (fastMode(true) nao garante
     * isso no exato tick em que o EditSession fecha). Retorna null se nao houver nenhum
     * bloco de minerio no schematic. */
    public static OreScanResult scanOreBoundsInClipboard(Clipboard clipboard, Location pasteOrigin) {
        BlockVector3 min = clipboard.getRegion().getMinimumPoint();
        BlockVector3 max = clipboard.getRegion().getMaximumPoint();
        BlockVector3 clipOrigin = clipboard.getOrigin();
        int pasteX = pasteOrigin.getBlockX(), pasteY = pasteOrigin.getBlockY(), pasteZ = pasteOrigin.getBlockZ();

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        int oreCount = 0;

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    if (isOreBlockType(clipboard.getBlock(x, y, z).getBlockType())) {
                        oreCount++;
                        int wx = pasteX + (x - clipOrigin.getX());
                        int wy = pasteY + (y - clipOrigin.getY());
                        int wz = pasteZ + (z - clipOrigin.getZ());
                        minX = Math.min(minX, wx); minY = Math.min(minY, wy); minZ = Math.min(minZ, wz);
                        maxX = Math.max(maxX, wx); maxY = Math.max(maxY, wy); maxZ = Math.max(maxZ, wz);
                    }
                }
            }
        }
        DebugLogger.log("Minerio (clipboard): %d bloco(s) *_ORE encontrado(s) no schematic.", oreCount);
        if (oreCount == 0) {
            return null;
        }
        return new OreScanResult(minX, minY, minZ, maxX, maxY, maxZ, oreCount);
    }

    /** Bounding box (coordenadas do MUNDO) dos blocos de minerio encontrados no schematic -
     * esse cuboide vira o volume mineravel (PrivateMine) da mina particular. */
    public record OreScanResult(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int oreCount) {
    }

    /** Forca o reenvio dos chunks da regiao pros clientes ja carregados - operacoes FAWE em
     * fastMode(true) podem terminar sem o cliente ver o resultado (ghost blocks/chunk
     * desatualizado) ate o chunk recarregar sozinho. */
    private static void refreshChunks(World world, int x1, int z1, int x2, int z2) {
        int minCx = Math.min(x1, x2) >> 4, maxCx = Math.max(x1, x2) >> 4;
        int minCz = Math.min(z1, z2) >> 4, maxCz = Math.max(z1, z2) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (world.isChunkLoaded(cx, cz)) {
                    world.refreshChunk(cx, cz);
                }
            }
        }
    }

    /** Salva a selecao do WorldEdit do jogador como um schematic .schem (minas particulares:
     * o admin constroi, seleciona com //wand e o plugin guarda a mina sem depender de //copy).
     * Devolve o minY da selecao (alinhamento do paste) e a composicao detectada dos blocos. */
    public static SchematicSaveResult saveSelectionToSchematic(Player player, File outFile) {
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

            // composicao detectada automaticamente: qualquer bloco de minerio (*_ORE) na
            // selecao - o admin nao precisa editar yml, so construir a mina de verdade.
            List<MineBlock> composition = detectComposition(clipboard);

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

    /** Conta os blocos de minerio (ver {@link #isOreBlockType}) no schematic inteiro e monta
     * a composicao com peso proporcional a quantidade de cada tipo - so minerio entra na
     * composicao (nunca pedra/decor: eles ficam como paredes fixas, nunca resetam). */
    private static List<MineBlock> detectComposition(Clipboard clipboard) {
        BlockVector3 min = clipboard.getRegion().getMinimumPoint();
        BlockVector3 max = clipboard.getRegion().getMaximumPoint();

        Map<String, Long> counts = new java.util.HashMap<>();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    var type = clipboard.getBlock(x, y, z).getBlockType();
                    if (isOreBlockType(type)) {
                        counts.merge(type.getId(), 1L, Long::sum);
                    }
                }
            }
        }

        List<MineBlock> composition = new ArrayList<>();
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
