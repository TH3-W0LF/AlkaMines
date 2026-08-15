package com.alka.mines.manager;

import com.alka.mines.config.MessagesConfig;
import com.alka.mines.hook.BlockFillHook;
import com.alka.mines.hook.FAWEHook;
import com.alka.mines.hook.FAWEHook.SchematicPaste;
import com.alka.mines.hook.AlkaEconomyHook;
import com.alka.mines.hook.PlotSquaredHook;
import com.alka.mines.model.MineBlock;
import com.alka.mines.model.MineRegion;
import com.alka.mines.model.MineTemplate;
import com.alka.mines.model.PrivateMine;
import com.alka.mines.util.ChatUtil;
import com.alka.mines.util.DebugLogger;
import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Minas particulares (Arquitetura da Mina Particular): o jogador usa um item gerador
 * (por template) na propria plot do PlotSquared, e a plot vira uma mina privada.
 *
 * Duas formas de definir o volume mineravel:
 * - SEM schematic: preenche as {@link MineTemplate#getHeight()} camadas superiores da plot.
 * - COM schematic (construcao custom): cola o .schem (plugins/AlkaMines/schematics ou
 *   plugins/FastAsyncWorldEdit/schematics) e varre os blocos de MINERIO (qualquer Material
 *   terminado em "_ORE") dentro dele - o bounding box deles e o volume mineravel/a
 *   composicao (auto-detectada pelas proporcoes de cada minerio); tudo que nao e minerio
 *   (pedra/decor/paredes) fica intacto PRA SEMPRE, nunca reseta - so as posicoes de
 *   minerio sao re-sorteadas no reset. Sem marcador manual nenhum.
 *
 * Modelo mental PT1/PT2 pra minas com schematic:
 * - PT1 = a construcao colada (paredes, chao, decor, qualquer bloco que NAO seja da
 *   composicao do template). E fixa desde a ativacao - nenhuma operacao (reset, expand,
 *   fill) altera PT1. So e tocada em delete() (limpa tudo, dono desistiu da mina).
 * - PT2 = o volume mineravel (bounding box do minerio detectado + a casca ganha pelo
 *   expand). So blocos AIR ou ja listados na composicao do template sao substituidos -
 *   ver {@link FAWEHook#resetRegionPreserving} (reset) e
 *   {@link FAWEHook#resetRegionOuterPreserving} (expand). PT2 expande so em X/Z (a altura Y
 *   nunca muda - ver {@link #expand}), sempre parando private-mine-expand-wall-margin blocos
 *   antes da parede da plot pra nunca encostar na PT1 externa.
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
    private com.alka.mines.hologram.HologramManager hologramManager;

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

    /** Injetado depois do HologramManager ser criado (ver AlkaMines#onPluginEnable). */
    public void setHologramManager(com.alka.mines.hologram.HologramManager hologramManager) {
        this.hologramManager = hologramManager;
    }

    /** Chave unica da mina particular (mundo + plot min) usada pros hologramas. */
    private String mineKey(PrivateMine mine) {
        return mine.getWorldName() + "_" + mine.getPlotMinX() + "_" + mine.getPlotMinZ();
    }

    private void refreshHologram(PrivateMine mine) {
        if (hologramManager == null) {
            return;
        }
        hologramManager.updatePrivate(mineKey(mine), mine, templates.get(mine.getTemplateId()));
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
            template.getComposition().add(new MineBlock(Material.STONE, 60));
            template.getComposition().add(new MineBlock(Material.GOLD_ORE, 40));
            templates.put(templateId.toLowerCase(), template);
            plugin.getLogger().info("Template '" + templateId + "' criado pelo registrarmina.");
        }
        String clean = name.toLowerCase().endsWith(".schem")
                ? name.substring(0, name.length() - 6)
                : name;
        if (clean.isEmpty() || !clean.matches("[a-z0-9_-]+")) {
            return "<red>Nome invalido (use letras minusculas, numeros, _ e -).";
        }
        File schematicsDir = new File(plugin.getDataFolder(), "schematics");
        schematicsDir.mkdirs();
        File outFile = new File(schematicsDir, clean + ".schem");

        FAWEHook.SchematicSaveResult result = FAWEHook.saveSelectionToSchematic(player, outFile);
        if (!result.success()) {
            return "<red>Falha ao salvar o schematic. Voce precisa de uma selecao valida do WorldEdit "
                    + "(//wand -> clique esquerdo/direito nos dois cantos da mina).";
        }

        // composicao detectada automaticamente: qualquer bloco de minerio (*_ORE) na
        // selecao - sem editar yml, sem marcador manual.
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
            ChatUtil.send(player, "<yellow>Nenhum bloco de minerio (*_ORE) encontrado na selecao - "
                    + "a mina nao vai ter nada quebravel. Defina a composicao no yml ou construa "
                    + "com blocos de minerio de verdade.");
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
            section.set("height", t.getHeight());
            section.set("paste-y-offset", t.getPasteYOffset());
            section.set("rarity", t.getRarity());
            section.set("expires-in-days", t.getExpiresInDays());
            section.set("expand-amount", t.getExpandAmount());
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
                    # COM schematic (construcao custom): construa a mina de verdade no chao da
                    # plot (tamanho da plot, com a altura que quiser) e rode
                    #   /alkamines registrarmina <template> <nome>
                    # com a selecao do //wand na construcao - o plugin salva o schematic e detecta
                    # a composicao E o volume mineravel SOZINHO: qualquer bloco de minerio que
                    # voce colocar (GOLD_ORE, DIAMOND_ORE, EMERALD_ORE, IRON_ORE, LAPIS_ORE,
                    # REDSTONE_ORE, COAL_ORE, COPPER_ORE, e as variantes DEEPSLATE_*/NETHER_* de
                    # cada um) vira parte da mina - o bounding box deles e o volume mineravel.
                    # TUDO que nao e minerio (pedra, decor, paredes) fica intacto PRA SEMPRE -
                    # so as posicoes de minerio resetam. Sem marcador manual nenhum.
                    # A mina precisa ter o piso no fundo da plot (a colagem alinha por la).
                    #
                    # expand-amount: quantos blocos esse template cresce por lado a cada
                    # /expandir ou upgrade pago (-1 = usa o private-mine-expand-amount global
                    # do config.yml). O expand NUNCA atravessa parede/estrutura de verdade -
                    # se bater em algo solido antes de completar a quantidade pedida, para
                    # ali e avisa o jogador (limite atingido).
                    #
                    # Depois de editar, rode /alkamines reload.
                    templates:
                      gold:
                        display-name: "<#FFD700>Mina de Ouro"
                        icon: GOLD_BLOCK
                        reset-interval-minutes: 30
                        height: 40
                        rarity: "★★★"
                        expires-in-days: 0
                        expand-amount: -1
                        # schematic: minagold
                        composition:
                          STONE: 60
                          GOLD_ORE: 40
                      diamond:
                        display-name: "<#00CED1>Mina de Diamante"
                        icon: DIAMOND_BLOCK
                        reset-interval-minutes: 45
                        height: 40
                        rarity: "★★★★★"
                        expires-in-days: 0
                        expand-amount: -1
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
            template.setHeight(section.getInt(id + ".height", 40));
            template.setPasteYOffset(section.getInt(id + ".paste-y-offset", Integer.MIN_VALUE));
            template.setRarity(section.getString(id + ".rarity", "★"));
            template.setExpiresInDays(section.getInt(id + ".expires-in-days", 0));
            template.setExpandAmount(section.getInt(id + ".expand-amount", -1));

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

    /** Mina particular cuja PLOT inteira contem a posicao (inclui paredes/decoracao fora do
     * volume mineravel) - usado pra protecao: so blocos da composicao quebram dentro da plot. */
    public Optional<PrivateMine> getMineProtectingAt(Location location) {
        for (List<PrivateMine> mines : byOwner.values()) {
            for (PrivateMine mine : mines) {
                if (mine.isInsidePlot(location)) {
                    return Optional.of(mine);
                }
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
            return MessagesConfig.getInstance().get("private-mine.limit-reached");
        }
        if (template.getComposition().isEmpty()) {
            return "<red>O template '" + template.getId()
                    + "' nao tem composicao configurada - adicione blocos em private-mine-templates.yml.";
        }
        Location probe = new Location(Bukkit.getWorld(bounds.world()), bounds.minX(), bounds.minY(), bounds.minZ());
        if (hasMineAt(probe)) {
            return MessagesConfig.getInstance().get("private-mine.plot-occupied");
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
        mine.setPlotBounds(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
        addIndexed(mine);
        save();
        createHologram(mine);
        // main thread (runTask): o FAWE ja opera async internamente; rodar no pool async
        // do Bukkit causava race/ghost blocks no cliente.
        Bukkit.getScheduler().runTask(plugin, () -> {
            backupPlot(bounds);
            fill(mine, template);
            teleportAboveMine(player, mine);
        });
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

        Bukkit.getScheduler().runTask(plugin, () -> {
            // 1. backup do estado ORIGINAL da plot (antes da mina) - restaura no delete.
            backupPlot(bounds);

            // 2. centraliza o schematic na plot: o centro do schematic coincide com o
            // centro da plot, respeitando a margem configurada (private-mine-margin).
            Optional<BlockVector3> dimensions = FAWEHook.getSchematicDimensions(schematicFile);
            if (dimensions.isEmpty()) {
                syncMsg(player, "<red>Falha ao ler as dimensoes do schematic '" + template.getSchematic() + "'.");
                return;
            }
            int schemW = dimensions.get().getX();
            int schemD = dimensions.get().getZ();
            int margin = plugin.getConfig().getInt("private-mine-margin", 4);
            int originX = clampCenter((bounds.minX() + bounds.maxX()) / 2 - schemW / 2,
                    bounds.minX(), bounds.maxX(), schemW, margin);
            int originZ = clampCenter((bounds.minZ() + bounds.maxZ()) / 2 - schemD / 2,
                    bounds.minZ(), bounds.maxZ(), schemD, margin);
            // respeita o paste-y-offset salvo no /alkamines registrarmina: cola a mina na
            // MESMA altura em relacao ao fundo da plot em que o admin a construiu. So cai
            // pro getPlotSurfaceY() (piso da plot) quando nao ha offset registrado. Sentinela
            // e Integer.MIN_VALUE, NAO 0 - 0 e um offset valido (schematic construido rente
            // ao fundo da plot), tratar 0 como "nao registrado" jogava essas minas na
            // superficie da plot em vez do fundo onde foram de fato construidas.
            int originY = template.getPasteYOffset() != Integer.MIN_VALUE
                    ? bounds.minY() + template.getPasteYOffset()
                    : getPlotSurfaceY(bounds);
            DebugLogger.log("Paste Y (offset=%d): minY da plot=%d -> originY=%d.",
                    template.getPasteYOffset(), bounds.minY(), originY);
            Location centeredOrigin = new Location(Bukkit.getWorld(bounds.world()), originX, originY, originZ);
            DebugLogger.log("Centralizando mina: origem %d,%d,%d tamanho %dx%dx%d margem alvo=%d",
                    originX, originY, originZ, schemW, dimensions.get().getY(), schemD, margin);

            Optional<SchematicPaste> paste = FAWEHook.pasteSchematic(schematicFile, centeredOrigin);
            if (paste.isEmpty()) {
                syncMsg(player, "<red>Falha ao colar o schematic '" + template.getSchematic()
                        + "' (veja o warning no console).");
                return;
            }
            DebugLogger.log("Schematic colado: origem %s tamanho %dx%dx%d",
                    paste.get().origin().getBlockX() + "," + paste.get().origin().getBlockY() + ","
                            + paste.get().origin().getBlockZ(),
                    paste.get().sizeX(), paste.get().sizeY(), paste.get().sizeZ());

            // varre o Clipboard em memoria (nao o mundo pos-paste): com fastMode(true) os
            // blocos colados nao tem garantia de estar visiveis via getBlockAt no exato
            // instante em que o EditSession fecha - ler do clipboard elimina essa corrida
            // por completo (causa raiz do antigo "bug surreal"). O volume mineravel e o
            // bounding box dos blocos de MINERIO (*_ORE) encontrados - sem marcador manual
            // nenhum. Tudo que nao e minerio (pedra/decor/paredes que o admin construiu)
            // fica intacto pra sempre: so as posicoes de minerio sao re-sorteadas no reset
            // (ver fill() / FAWEHook#resetRegionPreserving - a composicao so tem minerio).
            FAWEHook.OreScanResult oreScan = FAWEHook.scanOreBoundsInClipboard(paste.get().clipboard(), paste.get().origin());
            MineRegion mineRegion;
            if (oreScan == null) {
                // sem minerio nenhum: a construcao INTEIRA colada vira a mina, mas sem
                // composicao configurada nada vai ser quebravel ainda - so avisa o dono.
                mineRegion = new MineRegion(bounds.world(),
                        paste.get().origin().getBlockX(),
                        paste.get().origin().getBlockY(),
                        paste.get().origin().getBlockZ(),
                        paste.get().origin().getBlockX() + paste.get().sizeX() - 1,
                        paste.get().origin().getBlockY() + paste.get().sizeY() - 1,
                        paste.get().origin().getBlockZ() + paste.get().sizeZ() - 1);
                syncMsg(player, "<yellow>Nenhum bloco de minerio (*_ORE) encontrado no schematic - "
                        + "a mina ainda nao tem nada quebravel.");
            } else {
                mineRegion = new MineRegion(bounds.world(),
                        oreScan.minX(), oreScan.minY(), oreScan.minZ(),
                        oreScan.maxX(), oreScan.maxY(), oreScan.maxZ());
                DebugLogger.log("Minerio encontrado: volume mineravel = %d,%d,%d -> %d,%d,%d (%d bloco(s)).",
                        mineRegion.getX1(), mineRegion.getY1(), mineRegion.getZ1(),
                        mineRegion.getX2(), mineRegion.getY2(), mineRegion.getZ2(), oreScan.oreCount());
            }

            PrivateMine mine = new PrivateMine(player.getUniqueId(), bounds.world(),
                    mineRegion.getX1(), mineRegion.getY1(), mineRegion.getZ1(),
                    mineRegion.getX2(), mineRegion.getY2(), mineRegion.getZ2(), template.getId());
            mine.setPlotBounds(bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ());
            addIndexed(mine);
            save();
            createHologram(mine);
            // a construcao do admin e SEMPRE preservada exatamente como esta - o plugin
            // nunca preenche/randomiza por cima dela na ativacao, so no reset periodico
            // (e so as posicoes de minerio, nunca a pedra/decor ao redor delas).
            syncMsg(player, "<green>Mina particular ativada! (template '" + template.getId() + "')");
            teleportAboveMine(player, mine);
            DebugLogger.log("Mina particular criada (schematic=%s): owner=%s volume=%d",
                    template.getSchematic(), player.getName(), mine.volume());
        });
    }

    public boolean deleteAt(Player player, Location location) {
        Optional<PrivateMine> mine = getMineAt(location);
        if (mine.isEmpty() || !mine.get().getOwner().equals(player.getUniqueId())) {
            return false;
        }
        MineRegion plotRegion = new MineRegion(mine.get().getWorldName(),
                mine.get().getPlotMinX(), mine.get().getPlotMinY(), mine.get().getPlotMinZ(),
                mine.get().getPlotMaxX(), mine.get().getPlotMaxY(), mine.get().getPlotMaxZ());
        File backupFile = backupFileFor(mine.get().getWorldName(),
                mine.get().getPlotMinX(), mine.get().getPlotMinZ());
        removeIndexed(mine.get());
        save();
        if (hologramManager != null) {
            hologramManager.removePrivate(mineKey(mine.get()));
        }

        // restaura a plot ao terreno PADRAO do PlotSquared (igual /plot clear) - assim ela
        // volta ao estado normal em vez de virar void. Sem PlotSquared, cai pro backup
        // salvo antes da mina; sem backup, limpa com ar.
        if (PlotSquaredHook.clearPlot(player)) {
            teleportToPlotFloor(player, mine.get());
            return true;
        }
        // main thread (runTask): o FAWE ja opera async internamente; rodar no pool async
        // do Bukkit causava race/ghost blocks no cliente (mesmo motivo do resto da classe).
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (backupFile.exists()) {
                Location origin = new Location(Bukkit.getWorld(mine.get().getWorldName()),
                        mine.get().getPlotMinX(), mine.get().getPlotMinY(), mine.get().getPlotMinZ());
                FAWEHook.pasteSchematic(backupFile, origin);
                backupFile.delete(); // proxima ativacao salva um backup novo
            } else {
                BlockFillHook.clearRegion(plotRegion);
            }
            teleportToPlotFloor(player, mine.get());
        });
        DebugLogger.log("Mina particular deletada (fallback): owner=%s", player.getName());
        return true;
    }

    /** Centraliza o paste na plot, limitando a origem pra respeitar a margem (se couber)
     * e nunca sair dos limites da plot. */
    private int clampCenter(int origin, int plotMin, int plotMax, int schemSize, int margin) {
        int minAllowed = plotMin + margin;
        int maxAllowed = plotMax - margin - schemSize + 1;
        if (maxAllowed >= minAllowed) {
            return Math.max(minAllowed, Math.min(origin, maxAllowed));
        }
        // schematic grande demais pra margem: mantem dentro da plot, sem margem.
        return Math.max(plotMin + 1, Math.min(origin, plotMax - schemSize));
    }

    /** Salva o estado atual da plot como backup (so na primeira vez) - o arquivo fica em
     * plugins/AlkaMines/plot-backups/<mundo>_<minX>_<minZ>.schem. */
    private void backupPlot(PlotSquaredHook.PlotBounds bounds) {
        File backupFile = backupFileFor(bounds.world(), bounds.minX(), bounds.minZ());
        if (backupFile.exists()) {
            return;
        }
        MineRegion plotRegion = new MineRegion(bounds.world(),
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
        if (FAWEHook.saveRegionToSchematic(backupFile, plotRegion)) {
            DebugLogger.log("Backup da plot %s salvo em plot-backups/%s", bounds.minX() + "," + bounds.minZ(), backupFile.getName());
        }
    }

    private File backupFileFor(String world, int minX, int minZ) {
        File dir = new File(plugin.getDataFolder(), "plot-backups");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, world + "_" + minX + "_" + minZ + ".schem");
    }

    /** Expande o volume mineravel da mina em ate `amount` blocos pra cada lado (X/Z apenas -
     * a altura Y nunca muda), limitado a `private-mine-expand-wall-margin` blocos ANTES da
     * parede da plot (nunca encosta na borda), e re-preenche a casca nova com a composicao
     * do template (so minerio - ver [[project-alkamines]]). Estilo X-PrivateMines.
     *
     * Cada lado (X-, X+, Z-, Z+) para de crescer SOZINHO no primeiro bloco solido que nao
     * seja AIR nem da composicao do template - ou seja, uma parede/estrutura de verdade
     * (ex: parede de quartzo da PT1) trava so aquele lado, sem destruir nada e sem "pular"
     * pro espaco do outro lado da parede. Se pelo menos um lado bater em algo assim, o
     * jogador recebe um aviso de limite atingido mesmo quando os outros lados conseguiram
     * crescer normalmente. */
    public String expand(Player player, int amount) {
        if (amount <= 0) {
            return "<red>Quantidade invalida (use um numero inteiro > 0).";
        }
        Optional<PrivateMine> optional = getMineProtectingAt(player.getLocation());
        if (optional.isEmpty()) {
            return MessagesConfig.getInstance().get("private-mine.not-in-mine");
        }
        PrivateMine mine = optional.get();
        if (!mine.getOwner().equals(player.getUniqueId())) {
            return MessagesConfig.getInstance().get("private-mine.not-owner");
        }
        World world = Bukkit.getWorld(mine.getWorldName());
        if (world == null) {
            return "<red>Mundo da mina nao esta carregado.";
        }

        int wallMargin = plugin.getConfig().getInt("private-mine-expand-wall-margin", 2);
        int targetMinX = Math.max(mine.getPlotMinX() + wallMargin, mine.getMinX() - amount);
        int targetMinZ = Math.max(mine.getPlotMinZ() + wallMargin, mine.getMinZ() - amount);
        int targetMaxX = Math.min(mine.getPlotMaxX() - wallMargin, mine.getMaxX() + amount);
        int targetMaxZ = Math.min(mine.getPlotMaxZ() - wallMargin, mine.getMaxZ() + amount);

        // limite de tamanho por VIP (half-size) - 0 = sem limite extra (so a plot).
        int sizeLimit = getSizeLimit(player.getUniqueId());
        if (sizeLimit > 0) {
            int centerX = (mine.getPlotMinX() + mine.getPlotMaxX()) / 2;
            int centerZ = (mine.getPlotMinZ() + mine.getPlotMaxZ()) / 2;
            targetMinX = Math.max(targetMinX, centerX - sizeLimit);
            targetMaxX = Math.min(targetMaxX, centerX + sizeLimit);
            targetMinZ = Math.max(targetMinZ, centerZ - sizeLimit);
            targetMaxZ = Math.min(targetMaxZ, centerZ + sizeLimit);
        }

        if (targetMinX == mine.getMinX() && targetMaxX == mine.getMaxX()
                && targetMinZ == mine.getMinZ() && targetMaxZ == mine.getMaxZ()) {
            return "<red>A mina ja atingiu o limite" + (sizeLimit > 0 ? " do seu grupo." : " da plot.");
        }

        MineTemplate template = templates.get(mine.getTemplateId());
        Set<Material> passable = passableMaterials(template);
        int minY = mine.getMinY(), maxY = mine.getMaxY();

        int newMinX = mine.getMinX();
        boolean blocked = false;
        for (int x = mine.getMinX() - 1; x >= targetMinX; x--) {
            if (!isPlaneClearX(world, x, targetMinZ, targetMaxZ, minY, maxY, passable)) {
                blocked = true;
                break;
            }
            newMinX = x;
        }
        int newMaxX = mine.getMaxX();
        for (int x = mine.getMaxX() + 1; x <= targetMaxX; x++) {
            if (!isPlaneClearX(world, x, targetMinZ, targetMaxZ, minY, maxY, passable)) {
                blocked = true;
                break;
            }
            newMaxX = x;
        }
        int newMinZ = mine.getMinZ();
        for (int z = mine.getMinZ() - 1; z >= targetMinZ; z--) {
            if (!isPlaneClearZ(world, z, newMinX, newMaxX, minY, maxY, passable)) {
                blocked = true;
                break;
            }
            newMinZ = z;
        }
        int newMaxZ = mine.getMaxZ();
        for (int z = mine.getMaxZ() + 1; z <= targetMaxZ; z++) {
            if (!isPlaneClearZ(world, z, newMinX, newMaxX, minY, maxY, passable)) {
                blocked = true;
                break;
            }
            newMaxZ = z;
        }

        if (newMinX == mine.getMinX() && newMaxX == mine.getMaxX()
                && newMinZ == mine.getMinZ() && newMaxZ == mine.getMaxZ()) {
            return "<red>Limite atingido - tem uma parede/estrutura colada na mina, sem espaco pra expandir.";
        }

        int oldMinX = mine.getMinX(), oldMaxX = mine.getMaxX();
        int oldMinZ = mine.getMinZ(), oldMaxZ = mine.getMaxZ();
        removeIndexed(mine);
        mine.setMineRegion(newMinX, mine.getMinY(), newMinZ, newMaxX, mine.getMaxY(), newMaxZ);
        mine.setBlocksRemaining((int) Math.min(mine.volume(), Integer.MAX_VALUE));
        addIndexed(mine);
        save();

        if (blocked) {
            syncMsg(player, "<yellow>Limite atingido em pelo menos um lado - tem uma parede/estrutura "
                    + "no caminho, essa direcao nao expande mais.");
        }
        // main thread (runTask): o FAWE opera async internamente; rodar no pool async do
        // Bukkit causava race/ghost blocks no cliente. So preenche a CASCA nova da
        // expansao - o que ja existia (paredes/minerio) e preservado.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (template != null) {
                fillExpand(mine, template, oldMinX, oldMaxX, oldMinZ, oldMaxZ);
            }
        });
        DebugLogger.log("Mina particular expandida: owner=%s X %d->%d, Z %d->%d",
                player.getName(), oldMinX, newMaxX, oldMinZ, newMaxZ);
        return null;
    }

    /** Upgrade pago via AlkaEconomy: cobra a moeda configurada (custo ESCALONA com o nivel
     * da mina) e expande. Cada upgrade aumenta o nivel. */
    public String expandUpgraded(Player player) {
        Optional<PrivateMine> optional = getMineProtectingAt(player.getLocation());
        if (optional.isEmpty()) {
            return MessagesConfig.getInstance().get("private-mine.not-in-mine");
        }
        PrivateMine mine = optional.get();
        if (!mine.getOwner().equals(player.getUniqueId())) {
            return MessagesConfig.getInstance().get("private-mine.not-owner");
        }
        double cost = getUpgradeCost(mine);
        String currency = getUpgradeCurrency();
        AlkaEconomyHook economy = AlkaEconomyHook.getInstance();
        if (economy == null) {
            return "<red>AlkaEconomy nao esta instalado - nao da pra cobrar o upgrade.";
        }
        double balance = economy.getBalance(player.getUniqueId(), currency);
        if (balance < cost) {
            return "<red>Voce precisa de " + economy.format(cost) + " " + currency
                    + " pra expandir (tem " + economy.format(balance) + ").";
        }
        // Soh cobra e incrementa DEPOIS de o expand ter sucesso: se expand() retornar
        // erro (mina ja no limite, etc), nada e cobrado nem o nivel avanca.
        MineTemplate template = templates.get(mine.getTemplateId());
        String error = expand(player, getExpandAmount(template));
        if (error != null) {
            return error;
        }
        economy.withdraw(player.getUniqueId(), currency, cost);
        mine.setUpgradeLevel(mine.getUpgradeLevel() + 1);
        save();
        DebugLogger.log("Upgrade pago: %s expandiu a mina (nivel %d) por %s %s.",
                player.getName(), mine.getUpgradeLevel(), economy.format(cost), currency);
        return null;
    }

    /** Define o intervalo de reset da mina do jogador (menu particular). */
    public String setResetInterval(Player player, int minutes) {
        Optional<PrivateMine> optional = getMineProtectingAt(player.getLocation());
        if (optional.isEmpty()) {
            return MessagesConfig.getInstance().get("private-mine.not-in-mine");
        }
        PrivateMine mine = optional.get();
        if (!mine.getOwner().equals(player.getUniqueId())) {
            return MessagesConfig.getInstance().get("private-mine.not-owner");
        }
        mine.setResetIntervalMinutes(minutes);
        save();
        return null;
    }

    /** True se o bloco pode ser minerado dentro de uma mina particular (whitelist/blacklist). */
    public boolean isMinable(Material material) {
        List<String> whitelist = plugin.getConfig().getStringList("private-mine-mining.whitelist");
        if (!whitelist.isEmpty() && !whitelist.contains(material.name())) {
            return false;
        }
        List<String> blacklist = plugin.getConfig().getStringList("private-mine-mining.blacklist");
        return !blacklist.contains(material.name());
    }

    /** Opcoes de tempo de reset que o jogador pode escolher (default + por permissao). */
    public List<Integer> getResetTimeOptions(UUID owner) {
        LinkedHashSet<Integer> options = new LinkedHashSet<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("private-mine-reset-times");
        if (section != null) {
            for (String value : section.getStringList("default")) {
                try {
                    options.add(Integer.parseInt(value));
                } catch (NumberFormatException ignored) {
                }
            }
            Player player = Bukkit.getPlayer(owner);
            if (player != null) {
                for (String key : section.getKeys(false)) {
                    if (key.equalsIgnoreCase("default")) {
                        continue;
                    }
                    if (player.hasPermission(key) || player.hasPermission("alkaminas.particular.limite." + key)) {
                        for (String value : section.getStringList(key)) {
                            try {
                                options.add(Integer.parseInt(value));
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }
            }
        }
        List<Integer> sorted = new ArrayList<>(options);
        Collections.sort(sorted);
        return sorted;
    }

    /** Custo base do upgrade, escalando com o nivel da mina.
     * FOMULA ATIVA (linear): base * (nivel + 1) -> 1o upgrade (nivel 0) custa base, 2o (nivel 1) custa 2x base, etc.
     * Alternativa exponencial (se quiser que o 1o ja custe o dobro): base * Math.pow(2, nivel).
     * Troque comentando/descomentando o return abaixo. */
    public double getUpgradeCost(PrivateMine mine) {
        double base = plugin.getConfig().getDouble("private-mine-upgrade-cost", 10000);
        // FOMULA ATIVA: linear (1o upgrade = base).
        return base * (mine.getUpgradeLevel() + 1);
        // FOMULA ALTERNATIVA: exponencial (1o upgrade = 2x base).
        // return base * Math.pow(2, mine.getUpgradeLevel());
    }

    /** Moeda do upgrade (qualquer currencyId da AlkaEconomy). */
    public String getUpgradeCurrency() {
        return plugin.getConfig().getString("private-mine-upgrade-currency", "coins");
    }

    /** Quantos blocos crescer por lado num expand/upgrade - o template pode sobrescrever
     * (expand-amount no private-mine-templates.yml), senao cai pro
     * private-mine-expand-amount global do config.yml (default 1 = cresce de 1 em 1). */
    public int getExpandAmount(MineTemplate template) {
        if (template != null && template.getExpandAmount() >= 0) {
            return template.getExpandAmount();
        }
        return plugin.getConfig().getInt("private-mine-expand-amount", 1);
    }

    /** Limite de TAMANHO (half-size, dist do centro ate a borda em X/Z) por permissao pro
     * upgrade - config private-mine-upgrade-size-limit. 0 = sem limite extra (so a plot). */
    public int getSizeLimit(UUID owner) {
        Player player = Bukkit.getPlayer(owner);
        if (player == null) {
            return 0;
        }
        ConfigurationSection limits = plugin.getConfig().getConfigurationSection("private-mine-upgrade-size-limit");
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

    /** Localizacao segura no topo da mina (centro X/Z, maxY+1) - pro /mina particular home. */
    public Location getHomeLocation(PrivateMine mine) {
        World world = Bukkit.getWorld(mine.getWorldName());
        int centerX = (mine.getMinX() + mine.getMaxX()) / 2;
        int centerZ = (mine.getMinZ() + mine.getMaxZ()) / 2;
        return new Location(world, centerX + 0.5, mine.getMaxY() + 1.0, centerZ + 0.5);
    }

    /** Superficie (maior Y nao-ar) no centro da plot - o piso onde a mina e colada. */
    private int getPlotSurfaceY(PlotSquaredHook.PlotBounds bounds) {
        World world = Bukkit.getWorld(bounds.world());
        if (world == null) {
            return bounds.minY();
        }
        int cx = (bounds.minX() + bounds.maxX()) / 2;
        int cz = (bounds.minZ() + bounds.maxZ()) / 2;
        for (int y = bounds.maxY(); y >= bounds.minY(); y--) {
            if (!world.getBlockAt(cx, y, cz).getType().isAir()) {
                return y;
            }
        }
        return bounds.minY();
    }

    /** Compartilha a mina: adiciona o jogador como membro da plot (PlotSquared) pra poder
     * entrar e minerar. */
    public String addMember(Player owner, Player target) {
        Optional<PrivateMine> optional = getMineProtectingAt(owner.getLocation());
        if (optional.isEmpty()) {
            return MessagesConfig.getInstance().get("private-mine.not-in-mine");
        }
        PrivateMine mine = optional.get();
        if (!mine.getOwner().equals(owner.getUniqueId())) {
            return MessagesConfig.getInstance().get("private-mine.not-owner");
        }
        if (!PlotSquaredHook.addMember(owner, target.getUniqueId())) {
            return "<red>Falha ao adicionar " + target.getName() + " a plot (PlotSquared?).";
        }
        save();
        return null;
    }

    /** Reset por intervalo - roda a cada segundo (ver AlkaMines#onPluginEnable). */
    private int hologramTick;

    public void tickResets() {
        if (templates.isEmpty()) {
            return;
        }
        List<PrivateMine> all = new ArrayList<>();
        byOwner.values().forEach(all::addAll);
        boolean updateHolograms = ++hologramTick % 2 == 0; // a cada 2s
        for (PrivateMine mine : all) {
            MineTemplate template = templates.get(mine.getTemplateId());
            if (template == null) {
                continue;
            }
            // intervalo do dono (menu particular) tem prioridade sobre o do template.
            int intervalMinutes = mine.getResetIntervalMinutes() > 0
                    ? mine.getResetIntervalMinutes() : template.getResetIntervalMinutes();
            if (!mine.isResetting() && intervalMinutes > 0
                    && System.currentTimeMillis() - mine.getLastReset() >= intervalMinutes * 60_000L) {
                DebugLogger.log("Reset de mina particular '%s': intervalo %d min.",
                        mine.getTemplateId(), intervalMinutes);
                // teleporta quem esta DENTRO do volume mineravel pro topo (senao o player
                // morreria sufocado quando o reset preencher a mina).
                teleportPlayersOut(mine);
                // bloqueia quebra (MineBreakListener) enquanto o fill agendado nao roda -
                // liberado no proprio runTask, depois do fill() terminar.
                mine.setResetting(true);
                // main thread (runTask): o FAWE ja opera async internamente; rodar no pool
                // async do Bukkit causava race/ghost blocks no cliente.
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        fill(mine, template);
                    } catch (Throwable t) {
                        // sem isso, uma excecao aqui (regiao mal formada, erro interno do
                        // FAWE) deixava a mina travada em "resetando" pra sempre - todo bloco
                        // dela ficaria indestrutivel de vez (MineBreakListener bloqueia
                        // enquanto isResetting() for true).
                        Logger.getLogger("AlkaMines").log(Level.SEVERE,
                                "Fill da mina particular '" + mine.getTemplateId() + "' falhou - liberando a guarda mesmo assim.", t);
                    } finally {
                        mine.setResetting(false);
                    }
                });
                mine.setLastReset(System.currentTimeMillis());
                mine.setBlocksRemaining((int) Math.min(mine.volume(), Integer.MAX_VALUE));
            }
            if (updateHolograms) {
                refreshHologram(mine);
            }
        }
    }

    /** Cria o holograma de status acima da mina particular (se o DH estiver presente). */
    private void createHologram(PrivateMine mine) {
        if (hologramManager != null) {
            hologramManager.createPrivate(mineKey(mine), getHomeLocation(mine), mine, templates.get(mine.getTemplateId()));
        }
    }

    /** Teleporta pra cima da mina (centro X/Z, maxY+1) quem estiver dentro do volume
     * mineravel - chamado na main thread antes do reset. */
    private void teleportPlayersOut(PrivateMine mine) {
        World world = Bukkit.getWorld(mine.getWorldName());
        if (world == null) {
            return;
        }
        int centerX = (mine.getMinX() + mine.getMaxX()) / 2;
        int centerZ = (mine.getMinZ() + mine.getMaxZ()) / 2;
        Location safe = new Location(world, centerX + 0.5, mine.getMaxY() + 1.0, centerZ + 0.5);
        int count = 0;
        for (Player player : world.getPlayers()) {
            if (mine.containsFull(player.getLocation())) {
                player.teleport(safe);
                count++;
            }
        }
        if (count > 0) {
            DebugLogger.log("Reset: %d jogador(es) teleportado(s) pro topo da mina %s.", count, mine.getTemplateId());
        }
    }

    /** Teleporta o jogador pra cima da mina recem-criada (centro X/Z do volume mineravel,
     * maxY+1) - chamado na main thread logo apos a ativacao (schematic ou height-fill). */
    private void teleportAboveMine(Player player, PrivateMine mine) {
        if (!player.isOnline()) {
            return;
        }
        World world = Bukkit.getWorld(mine.getWorldName());
        if (world == null) {
            return;
        }
        int centerX = (mine.getMinX() + mine.getMaxX()) / 2;
        int centerZ = (mine.getMinZ() + mine.getMaxZ()) / 2;
        player.teleport(new Location(world, centerX + 0.5, mine.getMaxY() + 1.0, centerZ + 0.5));
    }

    /** Teleporta o jogador pro chao da plot (centro X/Z, primeiro bloco solido de cima pra
     * baixo +1) - chamado apos deletar a mina, quando o terreno ja foi restaurado (clear do
     * PlotSquared ou paste do backup/AIR). */
    private void teleportToPlotFloor(Player player, PrivateMine mine) {
        if (!player.isOnline()) {
            return;
        }
        World world = Bukkit.getWorld(mine.getWorldName());
        if (world == null) {
            return;
        }
        int centerX = (mine.getPlotMinX() + mine.getPlotMaxX()) / 2;
        int centerZ = (mine.getPlotMinZ() + mine.getPlotMaxZ()) / 2;
        int surfaceY = mine.getPlotMinY();
        for (int y = mine.getPlotMaxY(); y >= mine.getPlotMinY(); y--) {
            if (!world.getBlockAt(centerX, y, centerZ).getType().isAir()) {
                surfaceY = y;
                break;
            }
        }
        player.teleport(new Location(world, centerX + 0.5, surfaceY + 1.0, centerZ + 0.5));
    }

    private void fill(PrivateMine mine, MineTemplate template) {
        MineRegion region = new MineRegion(mine.getWorldName(),
                mine.getMinX(), mine.getMinY(), mine.getMinZ(),
                mine.getMaxX(), mine.getMaxY(), mine.getMaxZ());
        if (template.getComposition().isEmpty()) {
            // fallback: nunca deixar a mina "quebrada" (quadrado de blocos originais) no
            // reset/expand se a composicao do template estiver vazia.
            DebugLogger.warn("Mina particular '%s': composicao VAZIA - preenchendo com stone.",
                    mine.getTemplateId());
            BlockFillHook.fillRegion(region, List.of(new MineBlock(Material.STONE, 1.0)));
            return;
        }
        if (template.getSchematic() != null) {
            // mina com schematic (tem paredes): so substitui ar + blocos da composicao,
            // preservando as paredes/decoracao que nao estao na composicao.
            DebugLogger.log("Reset preservando paredes da mina particular '%s' (%d,%d,%d -> %d,%d,%d).",
                    mine.getTemplateId(), region.getX1(), region.getY1(), region.getZ1(),
                    region.getX2(), region.getY2(), region.getZ2());
            BlockFillHook.fillRegionPreserving(region, template.getComposition());
            return;
        }
        DebugLogger.log("Preenchendo mina particular '%s' (%d,%d,%d -> %d,%d,%d) com %d bloco(s) de composicao.",
                mine.getTemplateId(), region.getX1(), region.getY1(), region.getZ1(),
                region.getX2(), region.getY2(), region.getZ2(), template.getComposition().size());
        BlockFillHook.fillRegion(region, template.getComposition());
    }

    /** Preenche apenas a "casca" nova da expansao (regiao nova - regiao antiga), sem
     * tocar no que ja existia dentro da regiao antiga (paredes e minerio).
     * Minas COM schematic (PT1 fixa) usam a variante preservando: dentro da casca nova,
     * so AR/minerio sao substituidos - parede/decor do schematic que caia ali fica intacta,
     * a mesma regra do reset via {@link #fill}. Minas SEM schematic (preenchimento por
     * altura) nao tem PT1 pra preservar - a casca inteira vira composicao. */
    private void fillExpand(PrivateMine mine, MineTemplate template,
                            int oldMinX, int oldMaxX, int oldMinZ, int oldMaxZ) {
        MineRegion region = new MineRegion(mine.getWorldName(),
                mine.getMinX(), mine.getMinY(), mine.getMinZ(),
                mine.getMaxX(), mine.getMaxY(), mine.getMaxZ());
        List<MineBlock> composition = template.getComposition().isEmpty()
                ? List.of(new MineBlock(Material.STONE, 1.0))
                : template.getComposition();
        if (template.getComposition().isEmpty()) {
            DebugLogger.warn("Mina particular '%s': composicao VAZIA no expand - preenchendo a casca com stone.",
                    mine.getTemplateId());
        }
        if (template.getSchematic() != null) {
            DebugLogger.log("Expand preservando a PT1 da mina particular '%s' (casca fora de X %d-%d, Z %d-%d).",
                    mine.getTemplateId(), oldMinX, oldMaxX, oldMinZ, oldMaxZ);
            BlockFillHook.fillRegionOuterPreserving(region, oldMinX, oldMaxX, oldMinZ, oldMaxZ, composition);
            return;
        }
        DebugLogger.log("Expand da mina particular '%s': preenchendo a casca fora de X %d-%d, Z %d-%d.",
                mine.getTemplateId(), oldMinX, oldMaxX, oldMinZ, oldMaxZ);
        BlockFillHook.fillRegionOuter(region, oldMinX, oldMaxX, oldMinZ, oldMaxZ, composition);
    }

    /** AIR + qualquer Material da composicao do template - usado por {@link #expand} pra
     * decidir se um bloco no caminho da expansao pode ser atravessado (vira parte da nova
     * PT2) ou se e uma parede/estrutura de verdade (para o crescimento naquele lado). */
    private Set<Material> passableMaterials(MineTemplate template) {
        Set<Material> set = new HashSet<>();
        set.add(Material.AIR);
        set.add(Material.CAVE_AIR);
        set.add(Material.VOID_AIR);
        if (template != null) {
            for (MineBlock block : template.getComposition()) {
                set.add(block.getMaterial());
            }
        }
        return set;
    }

    /** true se o plano X=fixedX (Z de z1 a z2, Y de y1 a y2 inteiro) so tem blocos passaveis -
     * usado pelo expand() pra travar o crescimento em X no primeiro bloco solido real. */
    private boolean isPlaneClearX(World world, int fixedX, int z1, int z2, int y1, int y2, Set<Material> passable) {
        for (int z = z1; z <= z2; z++) {
            for (int y = y1; y <= y2; y++) {
                if (!passable.contains(world.getBlockAt(fixedX, y, z).getType())) {
                    return false;
                }
            }
        }
        return true;
    }

    /** true se o plano Z=fixedZ (X de x1 a x2, Y de y1 a y2 inteiro) so tem blocos passaveis -
     * usado pelo expand() pra travar o crescimento em Z no primeiro bloco solido real. */
    private boolean isPlaneClearZ(World world, int fixedZ, int x1, int x2, int y1, int y2, Set<Material> passable) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                if (!passable.contains(world.getBlockAt(x, y, fixedZ).getType())) {
                    return false;
                }
            }
        }
        return true;
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

    private void syncMsg(Player player, String message) {
        if (player.isOnline()) {
            Bukkit.getScheduler().runTask(plugin, () -> ChatUtil.send(player, message));
        }
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
                // bounds da plot (pra backup/restauracao) - fallback pra regiao da mina
                // em saves antigos que nao gravavam a plot.
                mine.setPlotBounds(
                        entry.containsKey("plot-min-x") ? ((Number) entry.get("plot-min-x")).intValue() : mine.getMinX(),
                        entry.containsKey("plot-min-y") ? ((Number) entry.get("plot-min-y")).intValue() : mine.getMinY(),
                        entry.containsKey("plot-min-z") ? ((Number) entry.get("plot-min-z")).intValue() : mine.getMinZ(),
                        entry.containsKey("plot-max-x") ? ((Number) entry.get("plot-max-x")).intValue() : mine.getMaxX(),
                        entry.containsKey("plot-max-y") ? ((Number) entry.get("plot-max-y")).intValue() : mine.getMaxY(),
                        entry.containsKey("plot-max-z") ? ((Number) entry.get("plot-max-z")).intValue() : mine.getMaxZ());
                mine.setResetIntervalMinutes(entry.containsKey("reset-interval-minutes")
                        ? ((Number) entry.get("reset-interval-minutes")).intValue() : 0);
                mine.setUpgradeLevel(entry.containsKey("upgrade-level")
                        ? ((Number) entry.get("upgrade-level")).intValue() : 0);
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
            entry.put("plot-min-x", mine.getPlotMinX());
            entry.put("plot-min-y", mine.getPlotMinY());
            entry.put("plot-min-z", mine.getPlotMinZ());
            entry.put("plot-max-x", mine.getPlotMaxX());
            entry.put("plot-max-y", mine.getPlotMaxY());
            entry.put("plot-max-z", mine.getPlotMaxZ());
            entry.put("reset-interval-minutes", mine.getResetIntervalMinutes());
            entry.put("upgrade-level", mine.getUpgradeLevel());
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
