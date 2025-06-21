package net.msrandom.minecraftcodev.forge.task

import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import net.msrandom.minecraftcodev.core.MinecraftCodevPlugin.Companion.json
import net.msrandom.minecraftcodev.core.utils.getAsPath
import net.msrandom.minecraftcodev.forge.jarjar.JAR_JAR_DIRECTORY_NAME
import net.msrandom.minecraftcodev.forge.jarjar.JAR_JAR_METADATA_JSON
import net.msrandom.minecraftcodev.includes.IncludedJarInfo
import net.msrandom.minecraftcodev.includes.IncludesJar
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream

private const val JAR_JAR_OUTPUT_BASE = "META-INF/jars"

// TODO This for some reason needs to be ran twice for generateMetadata to work properly
abstract class JarJar : IncludesJar() {
    abstract val metadataOutput: RegularFileProperty
        @Internal get

    init {
        group = LifecycleBasePlugin.BUILD_GROUP

        metadataOutput.set(temporaryDir.resolve(JAR_JAR_METADATA_JSON))

        from(metadataOutput) {
            it.into("META-INF/$JAR_JAR_DIRECTORY_NAME")
        }

        from(project.files(includedJarInfo.map { it.map(IncludedJarInfo::file) })) {
            it.into(JAR_JAR_OUTPUT_BASE)
        }

        from(project.zipTree(input))
    }

    override fun copy() {
        generateMetadata()

        super.copy()
    }

    private fun generateMetadata() {
        val info = includedJarInfo.get()

        if (info.isEmpty()) {
            metadataOutput.getAsPath().deleteIfExists()

            return
        }

        val metadata = buildJsonObject {
            putJsonArray("jars") {
                for (jar in info) {
                    addJsonObject {
                        putJsonObject("identifier") {
                            put("group", jar.group.get())
                            put("artifact", jar.moduleName.get())
                        }

                        putJsonObject("version") {
                            put("range", jar.versionRange.get())
                            put("artifactVersion", jar.artifactVersion.get())
                        }

                        put("path", "$JAR_JAR_OUTPUT_BASE/${jar.file.asFile.get().name}")
                    }
                }
            }
        }

        metadataOutput.getAsPath().outputStream().use {
            json.encodeToStream(metadata, it)
        }
    }
}
