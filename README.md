# AlkaMines

Sistema de minas/prison para a rede Alka* (Paper 1.21.8 / Java 21). Regiões
definidas via WorldEdit/FAWE, reset em massa assíncrono, drops reais (respeitam
Fortune/Silk Touch) e nível de picareta por jogador — construído sobre o
AlkaCore (banco/GUI compartilhados).

## O que faz

- **Criação de mina via seleção do WorldEdit** (`//pos1`, `//pos2`, `//wand`) —
  `/minaadmin criar` usa a seleção atual, sem sistema de posição próprio.
- **Reset em massa via FastAsyncWorldEdit** (`EditSession` + `RandomPattern`,
  assíncrono) — nunca trava a main thread, mas por isso o FAWE é **hard
  dependency**, sem fallback puro-Bukkit.
- **Composição por peso**: cada mina tem uma lista de materiais com peso
  relativo (editável via GUI, `BlockCompositionMenu`), define a mistura de
  blocos gerada no reset.
- **Drop real do bloco** (`Block#getDrops(tool)`, respeita Fortune/Silk
  Touch/encantamentos) — não é um `ItemStack` cru do `Material`. Cancela e
  remove o bloco manualmente (`setType(AIR, false)` + broadcast pros jogadores
  próximos) para evitar ghost block.
- **Nível de picareta por jogador**: sobe conforme blocos minerados, com
  anúncio de level-up; thresholds configuráveis.
- **XP por bloco**: valor normal configurável por bloco da composição, mais XP
  de Mineração via mcMMO (se instalado).
- **Hologramas por mina** (DecentHolograms) — nome, blocos restantes, etc.
- **GUIs administrativas**: menu principal, composição de blocos, configuração
  de reset (intervalo/porcentagem) — tudo via GUI, sem comando equivalente de
  propósito.
- **Bloqueio de comandos dentro da mina** (`mine-protection.blocked-commands`,
  com permissão de bypass pra staff).
- **Ranking de blocos minerados** (`/mina ranking`, alias `top`) — top 10
  jogadores por total de blocos quebrados (todas as minas).
- **Integração com AlkaShop** (auto-venda: se o jogador tiver ativo, o drop é
  vendido em vez de ir pro inventário — a mina nunca sabe preço, só pergunta
  "vendável?").
- **Integração com AlkaDrop** (auto-smelt, auto-condensar e a preferência de
  coleta do jogador — inventário ou chão — também valem minerando dentro da
  mina, via `AlkaDropAPI`/`ServicesManager`). Sem AlkaDrop, o drop cai no chão
  normal (vanilla) e o jogador pega andando em cima.
- Hooks opcionais de **PlaceholderAPI**, **AdvancedEnchantments** (efeitos de
  encantamento ao minerar), **mcMMO** (XP de Mineração) e **ItemsAdder**
  (ícone cosmético de mina nos menus — blocos custom na composição foram
  tentados e removidos, travavam o servidor em reset).

## Dependências

- **AlkaCore** e **FastAsyncWorldEdit** (hard dependency, ambos).
- PlaceholderAPI, DecentHolograms, AlkaEconomy, AlkaShop, AlkaDrop,
  AdvancedEnchantments, mcMMO, ItemsAdder e BossesPro são soft-dependencies
  opcionais — todas via reflexão (nenhuma delas é `compileOnly` no
  `build.gradle.kts`, um import direto já causou `NoClassDefFoundError`/
  `LinkageError` real em produção, ver histórico de commits).
- Publica um artefato "puro" no Maven local para o **AlkaDrop** consumir
  `MineManager#getMineAt` via `compileOnly`.

## Integração com AlkaDrop — cuidado com ordem de carregamento

`softdepend` **não garante** ordem estrita de `onEnable` num servidor com
muitos plugins/dependências cruzadas (confirmado em produção: o AlkaMines
habilitava antes do AlkaDrop apesar do softdepend). Por isso o hook do
AlkaDrop não é resolvido direto no `onEnable` — é adiado 1 tick
(`Bukkit.getScheduler().runTask`), depois que **todos** os plugins já
terminaram de habilitar, independente da ordem entre os dois.

## Débitos conhecidos

- `BossesProHook` é só detecção de presença — nenhuma integração real ainda.
- Comandos `/minaadmin setresetpercentage`/`setresetinterval` mencionados em
  versões antigas não existem — configuração de reset é só via GUI.
- Perdeu, na reescrita de 2026-07-29, a integração com AlkaRankUp (gate de
  rank) e AlkaEconomy (recompensa direta por bloco) que a versão anterior
  tinha — precisa ser pedido explicitamente se for reintroduzir.
