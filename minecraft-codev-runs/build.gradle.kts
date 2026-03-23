import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

gradlePlugin {
    plugins.create("minecraftCodevRuns") {
        id = project.name
        description = "A Minecraft Codev module that provides ways of running Minecraft in a development environment."
        implementationClass = "net.msrandom.minecraftcodev.runs.MinecraftCodevRunsPlugin"
    }
}

dependencies {
    implementation(group = "org.apache.commons", name = "commons-lang3", version = "3.12.0")
    api(group = "gradle.plugin.org.jetbrains.gradle.plugin.idea-ext", name = "gradle-idea-ext", version = "1.3")

    implementation(projects.minecraftCodevCore)
}

tasks.compileKotlin {
    compilerOptions {
        jvmDefault = JvmDefaultMode.ENABLE
    }
}

tasks.test {
    dependsOn(tasks.pluginUnderTestMetadata)
}
