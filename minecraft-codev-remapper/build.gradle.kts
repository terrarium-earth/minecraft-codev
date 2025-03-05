plugins {
    `java-gradle-plugin`
}

gradlePlugin {
    plugins.create("minecraftCodevRemapper") {
        id = project.name
        description = "A Minecraft Codev module that allows remapping dependencies to different mapping namespaces."
        implementationClass = "net.msrandom.minecraftcodev.remapper.MinecraftCodevRemapperPlugin"
    }
}

dependencies {
    api(group = "net.fabricmc", name = "mapping-io", version = "0.7.1")
    api(group = "net.fabricmc", name = "tiny-remapper", version = "0.11.0")

    implementation(group = "net.fabricmc", name = "mercury", version = "0.4.2")

    // TODO Remap should be downloaded in a configuration and used with the exec operations service and potentially the workers API
    // implementation(group = "com.github.replaymod", name = "remap", "5134612")

    api(projects.minecraftCodevCore)
}

tasks.test {
    dependsOn(tasks.pluginUnderTestMetadata)
}
