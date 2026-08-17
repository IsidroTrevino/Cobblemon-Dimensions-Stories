plugins {
    id("fabric-loom") version "1.13-SNAPSHOT"
    `maven-publish`
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
}

tasks.processResources {
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
