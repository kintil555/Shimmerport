plugins {
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
    `java-library`
    `maven-publish`
}

version = "${property("minecraft_version")}-${property("version_major")}.${property("version_patch")}"
group = property("maven_group") as String

base {
    archivesName.set(property("mod_name") as String)
}

repositories {
    maven("https://api.modrinth.com/maven") {
        name = "Modrinth"
        content { includeGroup("maven.modrinth") }
    }
    maven("https://jitpack.io")
}

loom {
    accessWidenerPath.set(file("src/main/resources/${property("mod_id")}.accesswidener"))
    runConfigs.all {
        property("mixin.debug", "true")
        property("mixin.debug.export", "true")
        property("mixin.dumpTargetOnFailure", "true")
        property("mixin.checks.interfaces", "true")
        property("mixin.hotSwap", "true")
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    // 26.2 is unobfuscated: no mappings dependency is declared at all (per FabricMC/fabric-example-mod@26.2).
    // Declaring officialMojangMappings()/parchment() here is what causes
    // "Failed to find official mojang mappings for 26.2" — Loom expects nothing in this block.

    implementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    api("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")

    api("me.shedaniel.cloth:cloth-config-fabric:${property("cloth_config_version")}")
    include("me.shedaniel.cloth:cloth-config-fabric:${property("cloth_config_version")}")

    implementation("maven.modrinth:sodium:${property("sodium_version")}") {
        exclude(group = "net.fabricmc.fabric-api")
    }
    implementation("maven.modrinth:iris:${property("iris_version")}") {
        exclude(group = "net.fabricmc.fabric-api")
    }

    implementation("org.anarres:jcpp:1.4.14") { isTransitive = false } // for iris
    implementation("io.github.douira:glsl-transformer:2.0.0-pre13") // for iris
    implementation("org.antlr:antlr4-runtime:4.11.1") // for iris

    implementation("maven.modrinth:modmenu:${property("mod_menu_version")}")

    implementation("com.github.LlamaLad7:MixinExtras:0.1.1")
    annotationProcessor("com.github.LlamaLad7:MixinExtras:0.1.1")
    implementation("org.jetbrains:annotations:24.0.1")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(25)
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("versions" to project.version)
    }
}
