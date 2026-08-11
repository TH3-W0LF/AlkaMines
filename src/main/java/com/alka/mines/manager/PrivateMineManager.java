package com.alka.mines.manager;

import com.alka.mines.hook.FAWEHook;
import com.alka.mines.hook.FAWEHook.SchematicPaste;
import com.alka.mines.hook.PlotSquaredHook;
import com.alka.mines.model.MineBlock;
import com.alka.mines.model.MineRegion;
import com.alka.mines.model.MineTemplate;
import com.alka.mines.model.PrivateMine;
import com.alka.mines.util.ChatUtil;
import com.alka.mines.util.DebugLogger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/**
 * Minas particulares (Arquitetura da Mina Particular): o jogador usa um item gerador
 * (por template) na propria plot do PlotSquared, e a plot vira uma mina privada.
 *
 * Duas formas de definir o volume mineravel:
 * - SEM schematic: preenche as {@link MineTemplate#getHeight()} camadas superiores da plot.
 * - COM schematic (construcao custom): cola o .schem (plugins/AlkaMines/schematics ou
 *   plugins/FastAsyncWorldEdit/schematics) e varre os blocos-marcador
 *   ({@link MineTemplate#getMineMarker()}) dentro dele - o cuboide entre os marcadores
 *   e o volume mineravel; o resto da construcao (paredes, chao, decor) fica intacto.
 *
 * Persistencia em private-mines.yml. Reset por intervalo do template; deletar limpa os
 * blocos da mina (FAWE AIR).
 */
public class PrivateMineManager {

    private static final String GENERATOR_KEY = "private_mine_template";

    private final JavaPlugin plugin;
    private final Map<String, MineTemplate> templates = new LinkedHashMap<>();
    private final Map<UUID, List<PrivateMine>> byOwner = new ConcurrentHashMap<>();
    private final Map<Long, List<PrivateMine>> chunkIndex = new ConcurrentHashMap<>();
    private final File minesFile;
    private final File templatesFile;
    private NamespacedKey generatorPdcKey;

    public PrivateMineManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.minesFile = new File(plugin.getDataFolder(), "private-mines.yml");
        this.templatesFile = new File(plugin.getDataFolder(), "private-mine-templates.yml");
        this.generatorPdcKey = new NamespacedKey(plugin, GENERATOR_KEY);
        load();
    }

    public void reload() {
        load();
    }

    /**
     * Registra uma mina a partir da selecao do WorldEdit do admin: salva a selecao como
     * schematic <nome>.schem em plugins/AlkaMines/schematics/ e amarra no template.
     * Retorna mensagem de erro ou null se OK.
     */
    public String registerMineSchematic(Player player, String name, String templateId) {
        MineTemplate template = templates.get(templateId.toLowerCase());
        if (template == null) {
            // cria o template na hora se nao existir - /alkamines registrarmina <nome>
            // funciona como "criar tipo de mina novo", sem editar yml antes.
            template = new MineTemplate(templateId.toLowerCase());
            template.setDisplayName(templateId);
            template.setIcon(Material.GOLD_BLOCK);
            template.setResetIntervalMinutes(30);
            template.setHeight(40);
            template.setMineMarker(Material.REDSTONE_BLOCK);
            template.getComposition().add(new MineBlock(Material.STONE, 60));
            template.getComposition().add(new MineBlock(Material.GOLD_ORE, 40));
            templates.put(templateId.toLowerCase(), template);
            plugin.getLogger().info("Template '" + templateId + "' criado pelo registrarmina.");
        }
        String clean = name.endsWith(".schem") ? name : name.substring(0, name.length() - 6);
        if (clean.isEmpty() || !clean.matches("[a-z0-9_-]+")) {
            return "<red>Nome invalido (use letras minusculas, numeros, _ e -).";
        }
        File schematicsDir = new File(plugin.getDataFolder(), "schematics");
        schematicsDir.mkdirs();
        File outFile = new File(schematicsDir, clean + ".schem");

        FAWEHook.SchematicSaveResult result = FAWEHook.saveSelectionToSchematic(player, outFile, template.getMineMarker());
        if (!result.success()) {
            return "<red>Falha ao salvar o schematic. Voce precisa de uma selecao valida do WorldEdit "
                    + "(//wand -> clique esquerdo/direito nos dois cantos da mina).";
        }

        // composicao detectada automaticamente dos blocos do schematic - sem editar yml.
        if (!result.composition().isEmpty()) {
            template.setComposition(result.composition());
            StringBuilder summary = new StringBuilder();
            for (MineBlock block : result.composition()) {
                summary.append("<white>").append(block.getMaterial().name())
                        .append("</white> <gray>x").append((long) block.getWeight()).append("  ");
            }
            ChatUtil.send(player, "<green>Composicao detectada do schematic: " + summary);
            DebugLogger.log("Composicao detectada do schematic '%s': %d tipo(s) de bloco.",
                    clean, result.composition().size());
        } else {
            ChatUtil.send(player, "<yellow>Nenhum bloco encontrado na selecao (so ar/marcadores?) - "
                    + "defina a composicao no yml se precisar.");
        }

        // registra o deslocamento vertical pra o paste colar na MESMA altura em que a mina
        // foi construida (a selecao pode estar acima do fundo da plot).
        int offset = 0;
        Optional<PlotSquaredHook.PlotBounds> plot = PlotSquaredHook.getOwnedPlotAt(player);
        if (plot.isPresent()) {
            offset = result.minY() - plot.get().minY();
            DebugLogger.log("Offset vertical do schematic '%s': selecao minY=%d, fundo da plot=%d -> offset=%d",
                    clean, result.minY(), plot.get().minY(), offset);
        } else {
            DebugLogger.log("Offset vertical do schematic '%s': admin fora de plot, offset=0 (colara no fundo).", clean);
        }
        template.setPasteYOffset(offset);

        template.setSchematic(clean);
        saveTemplates();
        loadTemplates();
        DebugLogger.log("Mina '%s' registrada como schematic no template '%s'.", clean, templateId);
        return null;
    }

    private void saveTemplates() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection root = config.createSection("templates");
        for (MineTemplate t : templates.values()) {
            ConfigurationSection section = root.createSection(t.getId());
            section.set("display-name", t.getDisplayName());
            section.set("icon", t.getIcon() != null ? t.getIcon().name() : "GOLD_BLOCK");
            section.set("reset-interval-minutes", t.getResetIntervalMinutes());
            section.set("schematic", t.getSchematic());
            section.set("mine-marker", t.getMineMarker().name());
            section.set("height", t.getHeight());
            section.set("paste-y-offset", t.getPasteYOffset());
            ConfigurationSection comp = section.createSection("composition");
            for (MineBlock block : t.getComposition()) {
                comp.set(block.getMaterial().name(), block.getWeight());
            }
        }
        try {
            config.save(templatesFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao salvar private-mine-templates.yml", e);
        }
    }

    // ---------- templates ----------

    public Optional<MineTemplate> getTemplate(String id) {
        return Optional.ofNullable(templates.get(id.toLowerCase()));
    }

    public Collection<MineTemplate> getTemplates() {
        return templates.values();
    }

    private void loadTemplates() {
        if (!templatesFile.exists()) {
            String defaultContent = """
                    # Templates de mina particular (cada um vira um item gerador vendivel).
                    #
                    # SEM schematic: a mina preenche 'height' camadas do CHAO da plot pra cima.
                    #
                    # COM schematic (construcao custom): construa a mina no chao da plot e rode
                    #   /alkamines registrarmina <template> <nome>
                    # com a selecao do //wand na construcao - o plugin salva o schematic e amarra
                    # aqui sozinho (nao precisa de //copy nem //schem save).
                    #   - SEM marcadores: a construcao INTEIRA vira a mina.
                    #   - COM marcadores (2 blocos 'mine-marker' em cantos opostos do volume
                    #     mineravel): so o cuboide entre eles e minerado - paredes/decor ficam.
                    # A mina precisa ter o piso no fundo da plot (a colagem alinha por la).
                    #
                    # Depois de editar, rode /alkamines reload.
                    templates:
                      gold:
                        display-name: "<#FFD700>Mina de Ouro"
                        icon: GOLD_BLOCK
                        reset-interval-minutes: 30
                        height: 40
                        mine-marker: REDSTONE_BLOCK
                        # schematic: minagold
                        composition:
                          STONE: 60
                          GOLD_ORE: 40
                      diamond:
                        display-name: "<#00CED1>Mina de Diamante"
                        icon: DIAMOND_BLOCK
                        reset-interval-minutes: 45
                        height: 40
                        mine-marker: REDSTONE_BLOCK
                        # schematic: minadiamante
                        composition:
                          STONE: 70
                          DIAMOND_ORE: 30
                    """;
            try {
                java.nio.file.Files.writeString(templatesFile.toPath(), defaultContent);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Falha ao salvar private-mine-templates.yml", e);
            }
        }

        templates.clear();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(templatesFile);
        ConfigurationSection section = config.getConfigurationSection("templates");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            MineTemplate template = new MineTemplate(id);
            template.setDisplayName(section.getString(id + ".display-name", id));
            String iconName = section.getString(id + ".icon");
            template.setIcon(iconName != null ? Material.matchMaterial(iconName) : Material.GOLD_BLOCK);
            template.setResetIntervalMinutes(section.getInt(id + ".reset-interval-minutes", 30));
            template.setSchematic(section.getString(id + ".schematic"));
            String markerName = section.getString(id + ".mine-marker");
            if (markerName != null) {
                Material marker = Material.matchMaterial(markerName);
                if (marker != null) {
                    template.setMineMarker(marker);
                }
            }
            template.setHeight(section.getInt(id + ".height", 40));
            template.setPasteYOffset(section.getInt(id + ".paste-y-offset", 0));

            List<MineBlock> composition = new ArrayList<>();
            ConfigurationSection compSection = section.getConfigurationSection(id + ".composition");
            if (compSection != null) {
                for (String materialName : compSection.getKeys(false)) {
                    Material material = Material.matchMaterial(materialName);
                    if (material != null) {
                        composition.add(new MineBlock(material, compSection.getDouble(materialName, 10.0)));
                    }
                }
            }
            template.setComposition(composition);
            templates.put(id.toLowerCase(), template);
        }
        plugin.getLogger().info("PrivateMineManager: " + templates.size() + " template(s) de mina particular carregados.");
    }

    // ---------- gerador ----------

    public ItemStack createGeneratorItem(MineTemplate template) {
        ItemStack item = new ItemStack(template.getIcon() != null ? template.getIcon() : Material.GOLD_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ChatUtil.parse(template.getDisplayName()));
        meta.lore(List.of(
                ChatUtil.parse("<gray>Gerador de mina particular"),
                ChatUtil.parse("<gray>Clique com o botao direito (agachado)"),
                ChatUtil.parse("<gray>na sua plot para ativar esta mina.")
        ));
        meta.getPersistentDataContainer().set(generatorPdcKey, PersistentDataType.STRING, template.getId());
        item.setItemMeta(meta);
        return item;
    }

    public Optional<String> readGeneratorTemplate(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(generatorPdcKey, PersistentDataType.STRING);
        return id != null ? Optional.of(id) : Optional.empty();
    }

    // ---------- minas ----------

    public Optional<PrivateMine> getMineAt(Location location) {
        List<PrivateMine> candidates = chunkIndex.get(chunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4));
        if (candidates == null) {
            return Optional.empty();
        }
        for (PrivateMine mine : candidates) {
            if (mine.contains(location)) {
                return Optional.of(mine);
            }
        }
        return Optional.empty();
    }

    public List<PrivateMine> getForPlayer(UUID owner) {
        return byOwner.getOrDefault(owner, List.of());
    }

    public boolean hasMineAt(Location location) {
        return getMineAt(location).isPresent();
    }

    /** Limite de minas particulares do jogador: max valor entre as permissoes que ele
     * tem (config private-mine-limits); sem nenhuma, usa o default. */
    public int getLimit(UUID owner) {
        Player player = Bukkit.getPlayer(owner);
        if (player == null) {
            return 0;
        }
        ConfigurationSection limits = plugin.getConfig().getConfigurationSection("private-mine-limits");
        if (limits == null) {
            return 0;
        }
        int limit = limits.getInt("default", 0);
        for (String key : limits.getKeys(false)) {
            if (key.equalsIgnoreCase("default")) {
                continue;
            }
            if (player.hasPermission(key) || player.hasPermission("alkaminas.particular.limite." + key)) {
                limit = Math.max(limit, limits.getInt(key, 0));
            }
        }
        return limit;
    }

    /**
     * Cria a mina particular do jogador na plot atual. Retorna mensagem de erro ou null
     * se OK (no caso de schematic, a criacao termina async - erro/sucesso via chat).
     */
    public String createFromGenerator(Player player, PlotSquaredHook.PlotBounds bounds, MineTemplate template) {
        if (!PlotSquaredHook.isEnabled()) {
            return "<red>PlotSquared nao esta instalado.";
        }
        if (getForPlayer(player.getUniqueId()).size() >= getLimit(player.getUniqueId())) {
            return "<red>Voce ja atingiu o limite de minas particulares do seu grupo.";
        }
        if (template.getComposition().isEmpty()) {
            return "<red>O template '" + template.getId()
                    + "' nao tem composicao configurada - adicione blocos em private-mine-templates.yml.";
        }
        Location probe = new Location(Bukkit.getWorld(bounds.world()), bounds.minX(), bounds.minY(), bounds.minZ());
        if (hasMineAt(probe)) {
            return "<red>Ja existe uma mina particular nessa plot.";
        }

        if (template.getSchematic() != null) {
            activateWithSchematic(player, bounds, template);
            return null;
        }

        // preenche do CHAO da plot pra cima (height camadas), nao do topo pra baixo -
        // plot vai ate o ceu (Y alto) e preencher do topo escondia a mina la em cima.
        int bottomY = bounds.minY();
        int topY = Math.min(bounds.maxY(), bottomY + template.getHeight() - 1);
        PrivateMine mine = new PrivateMine(player.getUniqueId(), bounds.world(),
                bounds.minX(), bottomY, bounds.minZ(),
                bounds.maxX(), topY, bounds.maxZ(), template.getId());
        addIndexed(mine);
        save();
        // FAWE async (docs FAWE): preencher na main thread travaria o tick com minas grandes.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> fill(mine, template));
        DebugLogger.log("Mina particular criada (height=%d, do chao): owner=%s plot=%s volume=%d",
                template.getHeight(), player.getName(), bounds.minX() + "," + bounds.minZ(), mine.volume());
        return null;
    }

    private void activateWithSchematic(Player player, PlotSquaredHook.PlotBounds bounds, MineTemplate template) {
        File schematicFile = findSchematic(template.getSchematic());
        if (schematicFile == null) {
            ChatUtil.send(player, "<red>Arquivo de schematic '" + template.getSchematic()
                    + ".schem' nao encontrado (plugins/AlkaMines/schematics/ ou plugins/FastAsyncWorldEdit/schematics/).");
            return;
        }
        DebugLogger.log("Schematic '%s' encontrado em %s", template.getSchematic(), schematicFile.getPath());

        Location origin = new Location(Bukkit.getWorld(bounds.world()),
                bounds.minX(), bounds.minY() + template.getPasteYOffset(), bounds.minZ());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Optional<SchematicPaste> paste = FAWEHook.pasteSchematic(schematicFile, origin);
            if (paste.isEmpty()) {
                syncMsg(player, "<red>Falha ao colar o schematic '" + template.getSchematic()
                        + "' (veja o warning no console).");
                return;
            }
            DebugLogger.log("Schematic colado: origem %s tamanho %dx%dx%d",
                    paste.get().origin().getBlockX() + "," + paste.get().origin().getBlockY() + ","
                            + paste.get().origin().getBlockZ(),
                    paste.get().sizeX(), paste.get().sizeY(), paste.get().sizeZ());

            MineRegion mineRegion = scanMarkers(bounds.world(), paste.get(), template.getMineMarker());
            if (mineRegion == null) {
                // sem marcadores suficientes: a construcao INTEIRA colada vira a mina.
                mineRegion = new MineRegion(bounds.world(),
                        paste.get().origin().getBlockX(),
                        paste.get().origin().getBlockY(),
                        paste.get().origin().getBlockZ(),
                        paste.get().origin().getBlockX() + paste.get().sizeX() - 1,
                        paste.get().origin().getBlockY() + paste.get().sizeY() - 1,
                        paste.get().origin().getBlockZ() + paste.get().sizeZ() - 1);
                syncMsg(player, "<yellow>Sem marcadores (" + template.getMineMarker().name()
                        + ") suficientes no schematic - a construcao INTEIRA foi definida como mina. "
                        + "Se quiser manter paredes/decoracao, coloque 2 marcadores nos cantos do volume "
                        + "mineravel e registre de novo.");
            } else {
                DebugLogger.log("Marcadores encontrados: volume mineravel = %d,%d,%d -> %d,%d,%d",
                        mineRegion.getX1(), mineRegion.getY1(), mineRegion.getZ1(),
                        mineRegion.getX2(), mineRegion.getY2(), mineRegion.getZ2());
            }

            final MineRegion region = mineRegion;
            // so preenche o interior se estiver VAZIO (shell) - se o admin construiu os
            // blocos, a construcao dele e preservada ate o primeiro reset.
            World world = Bukkit.getWorld(bounds.world());
            final boolean fillInterior = world != null && isRegionEmpty(world, region, template.getMineMarker());
            if (!fillInterior) {
                DebugLogger.log("Interior da mina '%s' ja tem blocos construidos - construcao preservada no ativar.",
                        template.getId());
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                PrivateMine mine = new PrivateMine(player.getUniqueId(), bounds.world(),
                        region.getX1(), region.getY1(), region.getZ1(),
                        region.getX2(), region.getY2(), region.getZ2(), template.getId());
                addIndexed(mine);
                save();
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    if (fillInterior) {
                        fill(mine, template);
                    }
                    syncMsg(player, "<green>Mina particular ativada! (template '" + template.getId() + "')"
                            + (fillInterior ? "" : " - sua construcao foi preservada."));
                });
                DebugLogger.log("Mina particular criada (schematic=%s): owner=%s volume=%d",
                        template.getSchematic(), player.getName(), mine.volume());
            });
        });
    }

    public boolean deleteAt(Player player, Location location) {
        Optional<PrivateMine> mine = getMineAt(location);
        if (mine.isEmpty() || !mine.get().getOwner().equals(player.getUniqueId())) {
            return false;
        }
        MineRegion region = new MineRegion(mine.get().getWorldName(),
                mine.get().getMinX(), mine.get().getMinY(), mine.get().getMinZ(),
                mine.get().getMaxX(), mine.get().getMaxY(), mine.get().getMaxZ());
        removeIndexed(mine.get());
        save();
        // limpa os blocos da mina (deixa a plot do jeito que PlotSquared regenera)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> FAWEHook.clearRegion(region));
        DebugLogger.log("Mina particular deletada: owner=%s", player.getName());
        return true;
    }

    /** Reset por intervalo - roda a cada segundo (ver AlkaMines#onPluginEnable). */
    public void tickResets() {
        if (templates.isEmpty()) {
            return;
        }
        List<PrivateMine> all = new ArrayList<>();
        byOwner.values().forEach(all::addAll);
        for (PrivateMine mine : all) {
            MineTemplate template = templates.get(mine.getTemplateId());
            if (template == null) {
                continue;
            }
            int intervalMinutes = template.getResetIntervalMinutes();
            if (intervalMinutes > 0
                    && System.currentTimeMillis() - mine.getLastReset() >= intervalMinutes * 60_000L) {
                // FAWE async (docs FAWE) - preencher na main thread travaria o tick.
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> fill(mine, template));
                mine.setLastReset(System.currentTimeMillis());
                mine.setBlocksRemaining((int) Math.min(mine.volume(), Integer.MAX_VALUE));
            }
        }
    }

    private void fill(PrivateMine mine, MineTemplate template) {
        if (template.getComposition().isEmpty()) {
            return;
        }
        MineRegion region = new MineRegion(mine.getWorldName(),
                mine.getMinX(), mine.getMinY(), mine.getMinZ(),
                mine.getMaxX(), mine.getMaxY(), mine.getMaxZ());
        FAWEHook.resetRegion(region, template.getComposition());
    }

    // ---------- schematic ----------

    private File findSchematic(String name) {
        String clean = name.endsWith(".schem") ? name : name + ".schem";
        for (File file : listSchematicFiles()) {
            if (file.getName().equalsIgnoreCase(clean)) {
                DebugLogger.log("Schematic '%s' encontrado em %s", clean, file.getPath());
                return file;
            }
        }
        return null;
    }

    /** Lista todos os .schem que o plugin consegue enxergar (AlkaMines + FAWE + WorldEdit). */
    public List<File> listSchematicFiles() {
        List<File> files = new ArrayList<>();
        collectSchematics(new File(plugin.getDataFolder(), "schematics"), files);
        if (Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null) {
            collectSchematics(new File(Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit").getDataFolder(), "schematics"), files);
        }
        if (Bukkit.getPluginManager().getPlugin("WorldEdit") != null) {
            collectSchematics(new File(Bukkit.getPluginManager().getPlugin("WorldEdit").getDataFolder(), "schematics"), files);
        }
        return files;
    }

    private void collectSchematics(File dir, List<File> out) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".schem"));
        if (files != null) {
            for (File file : files) {
                out.add(file);
            }
        }
    }

    /** Varre o cuboide colado procurando os blocos-marcador; o bounding box deles e o
     * volume mineravel da mina. Null se nao houver 2+ marcadores formando um cuboide. */
    private MineRegion scanMarkers(String worldName, SchematicPaste paste, Material marker) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        int count = 0;
        int bx = paste.origin().getBlockX(), by = paste.origin().getBlockY(), bz = paste.origin().getBlockZ();
        for (int x = 0; x < paste.sizeX(); x++) {
            for (int y = 0; y < paste.sizeY(); y++) {
                for (int z = 0; z < paste.sizeZ(); z++) {
                    if (world.getBlockAt(bx + x, by + y, bz + z).getType() == marker) {
                        count++;
                        minX = Math.min(minX, bx + x);
                        minY = Math.min(minY, by + y);
                        minZ = Math.min(minZ, bz + z);
                        maxX = Math.max(maxX, bx + x);
                        maxY = Math.max(maxY, by + y);
                        maxZ = Math.max(maxZ, bz + z);
                    }
                }
            }
        }
        DebugLogger.log("Marcadores: %d bloco(s) %s encontrado(s) no schematic.",
                count, marker.name());
        if (count < 2) {
            return null;
        }
        return new MineRegion(worldName, minX, minY, minZ, maxX, maxY, maxZ);
    }

    private void syncMsg(Player player, String message) {
        if (player.isOnline()) {
            Bukkit.getScheduler().runTask(plugin, () -> ChatUtil.send(player, message));
        }
    }

    /** true se o cuboide so tem ar (desconsiderando os blocos-marcador) - usado pra decidir
     * se o plugin preenche o interior no ativar ou preserva a construcao do admin. */
    private boolean isRegionEmpty(World world, MineRegion region, Material marker) {
        for (int x = region.getX1(); x <= region.getX2(); x++) {
            for (int y = region.getY1(); y <= region.getY2(); y++) {
                for (int z = region.getZ1(); z <= region.getZ2(); z++) {
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (!type.isAir() && type != marker) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // ---------- indice espacial ----------

    private void addIndexed(PrivateMine mine) {
        byOwner.computeIfAbsent(mine.getOwner(), k -> new CopyOnWriteArrayList<>()).add(mine);
        for (int cx = mine.getMinX() >> 4; cx <= mine.getMaxX() >> 4; cx++) {
            for (int cz = mine.getMinZ() >> 4; cz <= mine.getMaxZ() >> 4; cz++) {
                chunkIndex.computeIfAbsent(chunkKey(cx, cz), k -> new CopyOnWriteArrayList<>()).add(mine);
            }
        }
    }

    private void removeIndexed(PrivateMine mine) {
        List<PrivateMine> ownerMines = byOwner.get(mine.getOwner());
        if (ownerMines != null) {
            ownerMines.remove(mine);
            if (ownerMines.isEmpty()) {
                byOwner.remove(mine.getOwner());
            }
        }
        for (int cx = mine.getMinX() >> 4; cx <= mine.getMaxX() >> 4; cx++) {
            for (int cz = mine.getMinZ() >> 4; cz <= mine.getMaxZ() >> 4; cz++) {
                long key = chunkKey(cx, cz);
                List<PrivateMine> list = chunkIndex.get(key);
                if (list != null) {
                    list.remove(mine);
                    if (list.isEmpty()) {
                        chunkIndex.remove(key);
                    }
                }
            }
        }
    }

    private long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    // ---------- persistencia ----------

    private void load() {
        loadTemplates();
        chunkIndex.clear();
        byOwner.clear();
        if (!minesFile.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(minesFile);
        for (Map<?, ?> entry : config.getMapList("private-mines")) {
            try {
                UUID owner = UUID.fromString(String.valueOf(entry.get("owner")));
                PrivateMine mine = new PrivateMine(owner,
                        String.valueOf(entry.get("world")),
                        ((Number) entry.get("min-x")).intValue(),
                        ((Number) entry.get("min-y")).intValue(),
                        ((Number) entry.get("min-z")).intValue(),
                        ((Number) entry.get("max-x")).intValue(),
                        ((Number) entry.get("max-y")).intValue(),
                        ((Number) entry.get("max-z")).intValue(),
                        String.valueOf(entry.get("template")));
                mine.setCreatedAt(((Number) entry.get("created-at")).longValue());
                mine.setLastReset(((Number) entry.get("last-reset")).longValue());
                addIndexed(mine);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Entrada invalida em private-mines.yml", e);
            }
        }
        plugin.getLogger().info("PrivateMineManager: " + byOwner.size() + " jogador(es) com mina particular.");
    }

    private synchronized void save() {
        YamlConfiguration config = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        List<PrivateMine> all = new ArrayList<>();
        byOwner.values().forEach(all::addAll);
        for (PrivateMine mine : all) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("owner", mine.getOwner().toString());
            entry.put("world", mine.getWorldName());
            entry.put("min-x", mine.getMinX());
            entry.put("min-y", mine.getMinY());
            entry.put("min-z", mine.getMinZ());
            entry.put("max-x", mine.getMaxX());
            entry.put("max-y", mine.getMaxY());
            entry.put("max-z", mine.getMaxZ());
            entry.put("template", mine.getTemplateId());
            entry.put("created-at", mine.getCreatedAt());
            entry.put("last-reset", mine.getLastReset());
            list.add(entry);
        }
        config.set("private-mines", list);
        try {
            config.save(minesFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Falha ao salvar private-mines.yml", e);
        }
    }
}
