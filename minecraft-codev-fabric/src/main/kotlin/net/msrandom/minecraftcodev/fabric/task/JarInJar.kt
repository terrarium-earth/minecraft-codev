package net.msrandom.minecraftcodev.fabric.task

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.jvm.tasks.Jar
import org.gradle.language.base.plugins.LifecycleBasePlugin

abstract class JarInJar : Jar() {
    abstract val includedFiles: ConfigurableFileCollection
        @InputFiles get

    abstract val input: RegularFileProperty
        @InputFile get


    init {
        group = LifecycleBasePlugin.BUILD_GROUP

        from(project.zipTree(input))

        from(includedFiles) {
            it.into("META-INF/jars")
        }
    }
}
