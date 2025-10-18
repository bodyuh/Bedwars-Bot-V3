plugins {
    kotlin("jvm") version "1.8.10"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "best.bedwars.afk"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.essential.gg/repository/maven-public")
    maven("https://repo.spongepowered.org/repository/maven-public")
    maven("https://jitpack.io")
}

dependencies {
    // Minecraft 1.8.9 dependencies
    implementation("com.mojang:minecraft:1.8.9")
    implementation("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")
    
    // Essential dependencies
    implementation("gg.essential:essential-1.8.9-forge:4955+g395141645")
    implementation("gg.essential:loader-launchwrapper:1.1.3")
    
    // Mixin
    implementation("org.spongepowered:mixin:0.8.5-SNAPSHOT")
    annotationProcessor("org.spongepowered:mixin:0.8.5-SNAPSHOT:processor")
}

tasks {
    jar {
        manifest.attributes(
            mapOf(
                "ModSide" to "CLIENT",
                "TweakClass" to "gg.essential.loader.stage0.EssentialSetupTweaker"
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

