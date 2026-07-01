@file:Suppress("UnstableApiUsage")

plugins {
    id("mod-plugin")
    id("maven-publish")
    id("net.fabricmc.fabric-loom-remap")
    id("com.replaymod.preprocess")
    id("me.fallenbreath.yamlang")
}

version = fullProjectVersion
group = modMavenGroup

base {
    archivesName = "${modArchivesBaseName}-mc${mcVersion}"
}

repositories {
    maven("https://maven.fabricmc.net") { name = "FabricMC" }
    maven("https://maven.fallenbreath.me/releases") { name = "FallenBreath" }
    maven("https://api.modrinth.com/maven") { name = "Modrinth" }
    maven("https://www.cursemaven.com") { name = "CurseMaven" }
    maven("https://maven.parchmentmc.org") {
        name = "ParchmentMC"
        content { includeGroup("org.parchmentmc.data") }
    }
    maven("https://jitpack.io") { name = "Jitpack" }
    mavenLocal()
}

// fabric-loader version enforcement
configurations.all {
    resolutionStrategy {
        force("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
}

loom {
    val commonVmArgs = listOf("-Dmixin.debug.export=true", "-Dmixin.debug.verbose=true", "-Dmixin.env.remapRefMap=true")
    runs {
        named("server") {
            generateRunConfig.set(true)
            jvmArguments.set(commonVmArgs)
            runDirectory.set(file("../../run/server"))
        }
    }
}

yamlang {
    targetSourceSets = listOf(sourceSets.getByName("main"))
    inputDir = "assets/${modId}/lang"
}

tasks {
    register<Copy>("buildAndCollect") {
        description = "Build and collect the remapped jar to the root project build directory"
        group = "build"
        from(remapJar.map { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod_version")}"))
        dependsOn("build")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = modId
            version = "${modVersion}+${mcVersion}"
        }
    }
    repositories {
        mavenLocal()
        if (System.getenv("GITHUB_ACTIONS") == "true") {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/BiliXWhite/remote-inventory-next")
                credentials {
                    username = System.getenv("GITHUB_ACTOR") ?: ""
                    password = System.getenv("GITHUB_TOKEN") ?: ""
                }
            }
        }
    }
}
