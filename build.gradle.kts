import dev.architectury.pack200.java.Pack200Adapter

plugins {
    kotlin("jvm")
    id("gg.essential.loom")
    id("io.github.juuxel.loom-quiltflower")
    id("dev.architectury.architectury-pack200")
    id("com.github.johnrengelman.shadow")
}

group = "best.bedwars.afk"
version = "1.0.0"

loom {
    runConfigs {
        named("client") {
            ideConfigGenerated(true)
        }
    }

    launchConfigs {
        getByName("client") {
            // Essential Loader removed - not compatible with Forge 1.8.9
        }
    }

    forge {
        pack200Provider.set(Pack200Adapter())
    }
}

val embed: Configuration by configurations.creating
configurations.implementation.get().extendsFrom(embed)

dependencies {
    minecraft("com.mojang:minecraft:1.8.9")
    mappings("de.oceanlabs.mcp:mcp_stable:22-1.8.9")
    forge("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")

    // Essential removed - not used in code and incompatible with runtime
    // compileOnly("gg.essential:essential-1.8.9-forge:4955+g395141645")
    // embed("gg.essential:loader-launchwrapper:1.1.3")

    // Kotlin standard library - required for Kotlin mods to run
    embed("org.jetbrains.kotlin:kotlin-stdlib:1.7.10")

    compileOnly("org.spongepowered:mixin:0.8.5-SNAPSHOT")
    annotationProcessor("org.spongepowered:mixin:0.8.5-SNAPSHOT:processor")
    
    // Baritone for pathfinding - using local JAR
    // Uncomment when you have the JAR file in libs/ directory
    // See libs/README_BARITONE.md for instructions
    // implementation(files("libs/baritone-standalone-forge-1.8.9.jar"))
}

repositories {
    maven("https://repo.essential.gg/repository/maven-public")
    maven("https://repo.spongepowered.org/repository/maven-public")
    maven("https://jitpack.io") // For Baritone
}

tasks {
    jar {
        from(embed.files.map { zipTree(it) })

        // Essential Loader tweak class removed - not compatible with runtime
        manifest.attributes(
            mapOf(
                "ModSide" to "CLIENT"
            )
        )
    }

    processResources {
        inputs.property("version", project.version)
        inputs.property("mcversion", "1.8.9")
        filesMatching("mcmod.info") {
            expand("version" to project.version, "mcversion" to "1.8.9")
        }
    }

    withType<JavaCompile> {
        options.release.set(8)
    }
}


