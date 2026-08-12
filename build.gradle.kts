import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
    `maven-publish`
}

group = "com.alkacode"
version = "1.0.72"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    // mcMMO nao publica no Maven Central/jitpack - so no proprio Nexus deles.
    maven("https://nexus.neetgames.com/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    // depend hard no plugin.yml - o AlkaMines usa o AlkaCore de verdade: AlkaPlugin
    // (classe base + AlkaAPI), MessageProvider (mensagens), BaseGui (menus) e
    // AbstractRepository/DatabaseProvider (dados de jogador em SQL).
    compileOnly("com.alkacode:AlkaCore:1.0.0")
    // AlkaEconomy e AlkaShop NAO sao dependencia de compilacao - os hooks falam com
    // eles 100% via reflexao (ver comentario nas classes: um import direto de
    // AlkaEconomyPlugin/AlkaShopAPI aqui causava NoClassDefFoundError sem o plugin
    // instalado, mesmo sendo softdepend).

    // FAWE-Bukkit ja embute o WorldEdit inteiro (mesmas classes com.sk89q.worldedit.*,
    // com fastMode/RandomPattern a mais) - cobre selecao (//wand) e reset em massa.
    // NUNCA declarar worldedit-bukkit junto: sao as MESMAS classes, geram colisao de
    // classpath e o javac silenciosamente resolve contra a versao errada (descoberto
    // quando fastMode() "sumiu" do EditSessionBuilder por causa disso).
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Bukkit:2.15.3")
    // hologramas por mina
    // exclui os modulos NMS de snapshots futuros (26.1/26.2) - exigem JVM 25, nos so
    // rodamos ate 1.21.8 (Java 21) e so usamos a DHAPI publica, nunca as classes NMS.
    compileOnly("com.github.decentsoftware-eu:decentholograms:2.10.1") {
        exclude(group = "com.github.decentsoftware-eu.decentholograms", module = "nms-v26_1")
        exclude(group = "com.github.decentsoftware-eu.decentholograms", module = "nms-v26_2")
    }
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    // integracao soft - so da XP de Mineracao se estiver instalado (ver McMMOHook).
    // exclui WorldGuard: e dependencia transitiva do mcMMO (deteccao de regiao pra
    // XP), mas o pom dele aponta pra um snapshot (worldguard-legacy 7.0.0-SNAPSHOT)
    // que nao existe em nenhum repo publico configurado - so usamos ExperienceAPI,
    // que nao precisa disso pra compilar.
    compileOnly("com.gmail.nossr50.mcMMO:mcMMO:2.2.054") {
        exclude(group = "com.sk89q.worldguard")
    }
    // integracao soft - blocos/itens custom na composicao de mina (ver ItemsAdderHook).
    // servido via jitpack (ja declarado acima), nao pelo Nexus do mcMMO. Nao existe tag
    // 3.6.4 no jitpack - 3.6.1 e a release estavel mais proxima disponivel.
    compileOnly("com.github.LoneDev6:API-ItemsAdder:3.6.1")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    // sem isso, o Gradle nao percebe que so `version` mudou e reusa o plugin.yml
    // antigo do cache (processResources fica UP-TO-DATE incorretamente).
    inputs.property("version", project.version)
    expand("version" to project.version)
}

// publica o jar "puro" (sem FAWE/DecentHolograms/etc relocados) no repositorio Maven
// local, para o AlkaDrop consumir MineManager#getMineAt via compileOnly.
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = "AlkaMines"
            version = project.version.toString()
            from(components["java"])
        }
    }
}
