import java.util.zip.ZipFile

plugins {
    id("fabric-loom") version "1.13-SNAPSHOT"
    `maven-publish`
}

loom {
    serverOnlyMinecraftJar()
}

group = property("maven_group") as String
version = property("mod_version") as String

base {
    archivesName.set(property("archives_base_name") as String)
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
}

tasks.processResources {
    exclude("assets/cobblemon_dimensions_stories/textures/**")
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

val serverRegressionTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs server gameplay and interface regressions without a Minecraft client."
    dependsOn(tasks.testClasses)
    // These tests exercise server classes directly; Fabric's game launcher and
    // development logging injector are not needed in this JVM.
    classpath = sourceSets.test.get().output + sourceSets.main.get().output +
        configurations.getByName("testCompileClasspath")
    mainClass.set(providers.gradleProperty("regressionMain")
        .orElse("com.cobblemondimensions.stories.ServerRegressionTests"))
    workingDir(layout.buildDirectory.dir("server-regression"))
    systemProperty("stories.resources", layout.projectDirectory.dir("src/main/resources").asFile.absolutePath)
    doFirst {
        workingDir.mkdirs()
    }
}

tasks.check {
    dependsOn(serverRegressionTest)
}

tasks.test {
    // The project uses an executable regression suite, without a JUnit dependency.
    enabled = false
    dependsOn(serverRegressionTest)
}

val releaseJar = tasks.named<AbstractArchiveTask>("remapJar")
val verifyServerArtifact by tasks.registering {
    group = "verification"
    description = "Rejects client code and client entrypoints in the server distribution."
    dependsOn(releaseJar)
    doLast {
        for (archive in listOf(tasks.jar.get().archiveFile.get().asFile,
                               releaseJar.get().archiveFile.get().asFile)) {
            ZipFile(archive).use { zip ->
                val metadata = zip.getInputStream(zip.getEntry("fabric.mod.json")).bufferedReader().readText()
                check("\"environment\"\\s*:\\s*\"server\"".toRegex().containsMatchIn(metadata)) {
                    "Stories must be a server-only Fabric mod."
                }
                check(!"\"client\"\\s*:".toRegex().containsMatchIn(metadata) && !metadata.contains("\"mixins\"")) {
                    "The server artifact still has client entrypoints or mixins."
                }
                for (entry in zip.entries().asSequence().filter { it.name.endsWith(".class") }) {
                    check(!entry.name.contains("/client/") && !entry.name.contains("/stories/net/")) {
                        "Client or Stories payload class in the server artifact: ${entry.name}"
                    }
                    val constants = zip.getInputStream(entry).readBytes().toString(Charsets.ISO_8859_1)
                    check(!constants.contains("net/minecraft/client/") && !constants.contains("api/client/")) {
                        "Client API referenced by ${entry.name}"
                    }
                }
            }
        }
    }
}

tasks.check {
    dependsOn(verifyServerArtifact)
}

val datapackZip by tasks.registering(Zip::class) {
    group = "build"
    description = "Packages the server-side Cobblemon NPC/dialogue data as a standalone datapack."
    archiveClassifier.set("datapack")
    from("src/datapack/pack.mcmeta")
    from("src/main/resources/data") {
        into("data")
    }
}

tasks.assemble {
    dependsOn(datapackZip)
}
