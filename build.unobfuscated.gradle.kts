plugins {
    id("maven-publish")
    id("mod-plugin")
    id("net.fabricmc.fabric-loom")
    id("com.replaymod.preprocess")
}

version = fullProjectVersionName
group = modMavenGroup

repositories {
    fun strictMaven(url: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) }
        filter {
            groups.forEach {
                includeGroupAndSubgroups(it)
                includeGroupAndSubgroups("$it.*")
            }
        }
    }
    strictMaven("https://maven.fallenbreath.me/releases")
    strictMaven("https://maven.fabricmc.net")
    strictMaven("https://cursemaven.com", "curse.maven")
    strictMaven("https://api.modrinth.com/maven", "maven.modrinth")
    strictMaven("https://jitpack.io")
}

dependencies {
    minecraft("com.mojang:minecraft:${prop("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${prop("loader_version")}")

    // Implementation Mods
    implementation("net.fabricmc.fabric-api:fabric-api:${prop("fabric_version")}")
}

if (System.getenv("JITPACK") == "true") {
    base.archivesName.set("$modArchivesBaseName-mc$mcVersion")
} else {
    base.archivesName.set(modArchivesBaseName)
}

loom {
    val commonVmArgs = listOf("-Dmixin.debug.export=true", "-Dmixin.debug.verbose=true", "-Dmixin.env.remapRefMap=true")
    val commonProgramArgs = listOf("--width", "1280", "--height", "720", "--username", "dev")

    runs {
        named("client") {
            ideConfigGenerated(true)
            vmArgs(commonVmArgs)
            programArgs(commonProgramArgs)
            runDir= "../../run/client"
        }
        named("server") {
            runDir = "../../run/server"
        }
    }
}

publishing {
    publications {
        register("mavenJava", MavenPublication::class) {
            from(components["java"])
            artifactId = "${prop("mod_id")}-${prop("minecraft_version")}"
            version = modVersion
        }
    }
    repositories {
        mavenLocal()
        if (System.getenv("GITHUB_ACTIONS") == "true") {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/BiliXWhite/remote-inventory-server")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}
