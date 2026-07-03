plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

gradlePlugin {
    plugins.create("minecraftCodev") {
        id = rootProject.name
        description = "A Gradle plugin that allows using Minecraft as a dependency that participates in variant selection and resolution."
        implementationClass = "net.msrandom.minecraftcodev.core.MinecraftCodevPlugin"
    }
}

dependencies {
    implementation(projects.minecraftCodevCore.sideAnnotations)

    api(group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version = "1.10.0-RC")
    api(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version = "1.10.2")

    api(group = "net.minecraftforge", name = "srgutils", version = "latest.release")

    api(group = "org.ow2.asm", name = "asm-tree", version = "9.9.1")

    api(group = "com.google.guava", name = "guava", version = "33.5.0-jre")
    api(group = "org.apache.commons", name = "commons-lang3", version = "3.20.0")
    implementation(group = "commons-io", name = "commons-io", version = "2.21.0")
}

tasks.test {
    dependsOn(tasks.pluginUnderTestMetadata)
}

publishing {
    publications {
        create<MavenPublication>("pluginMaven") {
            suppressAllPomMetadataWarnings()
        }
    }
}
