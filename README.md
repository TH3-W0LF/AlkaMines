<div align="center">

# AlkaMines

### Sistema de minas/prison para a rede AlkaStudio

Minas geradas via WorldEdit, reset em massa assíncrono, drops reais e
progressão por picareta — construído sobre o AlkaCore.

![Java](https://img.shields.io/badge/Java-21-orange)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.8-green)
![Version](https://img.shields.io/badge/Version-1.0.83-blue)
![License](https://img.shields.io/badge/License-Proprietary-red)

</div>

---

## 📋 Sobre o Projeto

O **AlkaMines** é o sistema de minas estilo *prison* da rede AlkaStudio.
Regiões são definidas com uma simples seleção do WorldEdit, resetam em massa
sem travar o servidor, e cada bloco minerado dropa de verdade — respeitando
Fortune, Silk Touch e qualquer outro encantamento do jogador.

## ✨ Funcionalidades Principais

| Módulo | Descrição |
| --- | --- |
| ⛏️ **Criação via WorldEdit** | `/alkamines criar` usa a seleção atual (`//pos1`/`//pos2`), sem sistema de posição próprio. |
| 🔄 **Reset em massa assíncrono** | Reset de mina via FastAsyncWorldEdit, sem travar a main thread. |
| ⚖️ **Composição por peso** | Cada mina tem uma mistura de blocos configurável por peso relativo, editável em GUI. |
| 💎 **Drops reais** | Respeita Fortune/Silk Touch e demais encantamentos do jogador — não é um item genérico. |
| 📈 **Nível de picareta** | Progressão por jogador conforme blocos minerados, com anúncio de level-up. |
| 🧭 **Hologramas por mina** | Nome, blocos restantes e status exibidos via holograma (DecentHolograms). |
| 🏆 **Ranking de mineração** | `/mina ranking` — top 10 jogadores por total de blocos quebrados. |
| 🏠 **Minas particulares** | Cada jogador pode ter sua própria mina expansível, com home e compartilhamento. |
| 🛒 **Auto-venda integrada** | Drops podem ser vendidos automaticamente via AlkaShop, respeitando a preferência do jogador por material. |
| 📦 **Auto-smelt/condensar** | Integração com AlkaDrop para processar e condensar o que é minerado. |
| 🖥️ **GUIs administrativas completas** | Menu principal, composição de blocos e configuração de reset, tudo sem comando equivalente. |

## 🎮 Comandos

| Comando | Descrição | Permissão |
| --- | --- | --- |
| `/mina [ir <id>\|sair\|lista\|ranking\|particular ...]` | Menu de minas, teleporte e minas particulares | `alkaminas.ir` |
| `/alkamines <criar\|deletar\|editar\|resetar\|...>` | Administração completa das minas do servidor | `alkaminas.admin.*` |

Aliases: `/minas` (para `/mina`), `/minaadmin`, `/minadmin`, `/ma` (para
`/alkamines`).

## 🔗 Integrações

- **AlkaCore** e **FastAsyncWorldEdit** (obrigatórias) — infraestrutura de
  banco/GUI e reset em massa.
- **AlkaShop** — auto-venda de drops por material/categoria.
- **AlkaDrop** — auto-smelt, condensação e preferência de coleta do jogador.
- **DecentHolograms** — hologramas por mina.
- **AdvancedEnchantments** — efeitos de encantamento customizado ao minerar.
- **mcMMO** — XP de Mineração.
- **PlaceholderAPI**, **ItemsAdder** — placeholders e ícones cosméticos.

## 🔧 Tecnologias Utilizadas

- **Java 21** · **Gradle** (com `shadow`)
- **Paper API 1.21.8**
- **FastAsyncWorldEdit** para seleção e reset em massa
- **Adventure/MiniMessage** para mensagens e GUI

## ⚙️ Instalação

1. Instale **AlkaCore** e **FastAsyncWorldEdit** primeiro.
2. Coloque `AlkaMines.jar` na pasta `plugins/` do servidor (Paper **1.21.8+**).
3. Reinicie o servidor.
4. Crie uma mina com `/alkamines criar` após selecionar a área com WorldEdit.

## 🔐 Permissões

| Permissão | Padrão | Descrição |
| --- | --- | --- |
| `alkaminas.ir` | true | Usar `/mina ir` para teleportar até uma mina |
| `alkaminas.admin.criar` | op | Criar uma mina a partir da seleção do WorldEdit |
| `alkaminas.admin.deletar` | op | Deletar uma mina |
| `alkaminas.admin.editar` | op | Abrir o menu administrativo de uma mina |
| `alkaminas.admin.resetar` | op | Forçar o reset imediato de uma mina |
| `alkaminas.admin.setspawn` | op | Definir o spawn de uma mina |
| `alkaminas.admin.reload` | op | Recarregar configuração das minas |
| `alkaminas.admin.build` | op | Colocar blocos dentro da área de uma mina |
| `alkaminas.admin.bypass.commands` | op | Ignorar o bloqueio de comandos dentro de uma mina |
| `alkaminas.mina.vip` / `alkaminas.mina.supervip` | false | Acesso a minas restritas por permissão |

## 📝 Licença

> ⚠️ **Projeto proprietário da AlkaStudio.**
>
> Código fonte destinado exclusivamente ao uso interno da rede `Alka*`.
> Reprodução, distribuição ou uso não autorizado não são permitidos.

## 🎯 Créditos

- **Desenvolvido por**: MestreDEV — AlkaStudio
- **Parte de**: todo o ecossistema `Alka*`

---

<div align="center">

**Desenvolvido com ❤️ pela AlkaStudio**

[![AlkaStudio](https://img.shields.io/badge/AlkaStudio-JLob0-blue)](https://github.com/JLob0)

</div>
