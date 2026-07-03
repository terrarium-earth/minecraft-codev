plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

gradlePlugin {
    plugins.create("minecraftCodevForge") {
        id = project.name
        description = "A Minecraft Codev module that allows providing Forge patched versions of Minecraft."
        implementationClass = "net.msrandom.minecraftcodev.forge.MinecraftCodevForgePlugin"
    }
}

dependencies {
    implementation(group = "io.arrow-kt", name = "arrow-core", version = "2.2.1.1")
    implementation(group = "io.arrow-kt", name = "arrow-core-serialization", version = "2.2.1.1")

    implementation("net.neoforged.accesstransformers:at-cli:13.0.3")

    implementation(group = "org.cadixdev", name = "lorenz", version = "0.5.8")

    implementation(group = "de.siegmar", name = "fastcsv", version = "4.1.0")
    implementation(group = "org.apache.logging.log4j", name = "log4j-core", version = "2.25.3")

    implementation(group = "com.electronwill.night-config", name = "toml", version = "3.8.3")

    implementation(projects.minecraftCodevAccessWidener)
    implementation(projects.minecraftCodevRemapper)
    implementation(projects.minecraftCodevRuns)
    implementation(projects.minecraftCodevMixins)
    implementation(projects.minecraftCodevIncludes)
}

tasks.test {
    dependsOn(tasks.pluginUnderTestMetadata)
}
