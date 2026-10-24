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

// -Pstories.integrated=true builds a jar that also loads in a client's built-in
// singleplayer server, for quick local testing. Releases stay server-only.
val integratedDev = providers.gradleProperty("stories.integrated").orElse("false").get().toBoolean()

tasks.processResources {
    exclude("assets/cobblemon_dimensions_stories/textures/**")
    inputs.property("version", project.version)
    inputs.property("environment", if (integratedDev) "*" else "server")
    filesMatching("fabric.mod.json") {
        expand("version" to project.version, "environment" to if (integratedDev) "*" else "server")
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

// The resource pack is built by a small tool that reads the same sprite catalogue
// as the server, so the pack and the mod always agree about which glyph is which.
val packTool by sourceSets.creating {
    java.srcDir("src/pack/java")
    resources.srcDir("src/pack/resources")
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += output + sourceSets.main.get().output
}

val packArt = layout.projectDirectory.dir("src/main/resources/assets/cobblemon_dimensions_stories/textures/gui")
val packDirectory = layout.buildDirectory.dir("resourcepack")
val packZip = layout.buildDirectory.file("distributions/${property("archives_base_name")}-resourcepack-${project.version}.zip")

val buildResourcePack by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Builds the client resource pack that draws the Stories menus."
    dependsOn(tasks.classes, tasks.named("packToolClasses"))
    classpath = packTool.runtimeClasspath
    mainClass.set("com.cobblemondimensions.stories.pack.ResourcePackBuilder")
    inputs.dir(packArt)
    inputs.files(sourceSets.main.get().output.classesDirs)
    inputs.files(packTool.output)
    outputs.file(packZip)
    outputs.dir(packDirectory)
    doFirst {
        args("--art", packArt.asFile.absolutePath,
            "--out", packDirectory.get().asFile.absolutePath,
            "--zip", packZip.get().asFile.absolutePath)
    }
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
    description = "Rejects client code, client entrypoints and client mixins in the server distribution."
    dependsOn(releaseJar)
    doLast {
        for (archive in listOf(tasks.jar.get().archiveFile.get().asFile,
                               releaseJar.get().archiveFile.get().asFile)) {
            ZipFile(archive).use { zip ->
                val metadata = zip.getInputStream(zip.getEntry("fabric.mod.json")).bufferedReader().readText()
                check(integratedDev || "\"environment\"\\s*:\\s*\"server\"".toRegex().containsMatchIn(metadata)) {
                    "Stories must be a server-only Fabric mod."
                }
                check(!"\"client\"\\s*:".toRegex().containsMatchIn(metadata)) {
                    "The server artifact still has client entrypoints."
                }
                // Server mixins are fine; a client section would never load on a dedicated server.
                zip.getEntry("cobblemon_dimensions_stories.mixins.json")?.let { entry ->
                    val mixins = zip.getInputStream(entry).bufferedReader().readText()
                    check(!"\"client\"\\s*:".toRegex().containsMatchIn(mixins)) {
                        "The server artifact still has client mixins."
                    }
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
    dependsOn(buildResourcePack)
}
