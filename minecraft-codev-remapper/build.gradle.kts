plugins {
    `kotlin-dsl`
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
    api(group = "net.fabricmc", name = "mapping-io", version = "0.8.0")
    api(group = "net.fabricmc", name = "tiny-remapper", version = "0.12.2")

    implementation(group = "net.fabricmc", name = "mercury", version = "0.6.0")

    implementation(group = "org.jetbrains.kotlin", name = "kotlin-metadata-jvm", version = "2.3.0")

    // TODO Remap should be downloaded in a configuration and used with the exec operations service and potentially the workers API
    // implementation(group = "com.github.replaymod", name = "remap", "0299ac15")

    api(projects.minecraftCodevCore)
    api(projects.minecraftCodevIncludes)
}

tasks.test {
    dependsOn(tasks.pluginUnderTestMetadata)
}
