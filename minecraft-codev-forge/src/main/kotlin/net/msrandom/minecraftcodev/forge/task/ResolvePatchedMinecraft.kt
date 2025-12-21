package net.msrandom.minecraftcodev.forge.task

import arrow.core.Either
import net.msrandom.minecraftcodev.core.resolve.*
import net.msrandom.minecraftcodev.core.task.CachedMinecraftTask
import net.msrandom.minecraftcodev.core.task.MinecraftVersioned
import net.msrandom.minecraftcodev.core.task.versionList
import net.msrandom.minecraftcodev.core.utils.cacheExpensiveOperation
import net.msrandom.minecraftcodev.core.utils.getAsPath
import net.msrandom.minecraftcodev.core.utils.walk
import net.msrandom.minecraftcodev.core.utils.zipFileSystem
import net.msrandom.minecraftcodev.forge.McpConfigFile
import net.msrandom.minecraftcodev.forge.UserdevConfig
import net.neoforged.accesstransformer.cli.TransformerProcessor
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.process.ExecOperations
import java.io.Closeable
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest
import javax.inject.Inject
import kotlin.io.path.*

const val PATCH_OPERATION_VERSION = 2

abstract class ResolvePatchedMinecraft : CachedMinecraftTask(), MinecraftVersioned {
    abstract val libraries: ConfigurableFileCollection
        @InputFiles
        @PathSensitive(PathSensitivity.ABSOLUTE)
        get

    abstract val patches: ConfigurableFileCollection
        @InputFiles
        @PathSensitive(PathSensitivity.ABSOLUTE)
        get

    abstract val universal: ConfigurableFileCollection
        @InputFiles
        @PathSensitive(PathSensitivity.ABSOLUTE)
        get

    abstract val neoforge: Property<Boolean>
        @Optional
        @Input
        get

    abstract val output: RegularFileProperty
        @OutputFile get

    abstract val clientExtra: RegularFileProperty
        @OutputFile get

    abstract val configurationContainer: ConfigurationContainer
        @Inject get

    abstract val dependencyHandler: DependencyHandler
        @Inject get

    abstract val javaToolchainService: JavaToolchainService
        @Inject get

    abstract val execOperations: ExecOperations
        @Inject get

    init {
        output.convention(
            project.layout.file(
                minecraftVersion.map {
                    temporaryDir.resolve("minecraft-$it-patched.jar")
                },
            ),
        )

        clientExtra.convention(
            project.layout.file(
                minecraftVersion.map {
                    temporaryDir.resolve("minecraft-$it-patched-client-extra.jar")
                },
            ),
        )
    }

    private fun resolve(cacheDirectory: Path, outputPath: Path, clientExtra: Path) {
        val isOffline = cacheParameters.getIsOffline().get()

        val metadata = cacheParameters.versionList().version(minecraftVersion.get())

        val clientJar = downloadFullMinecraftClient(cacheDirectory, metadata, isOffline)

        val extractionState = getServerExtractionState(cacheDirectory, metadata, isOffline)!!

        val serverJar = extractionState.result

        val userdevFile = patches.filter { "userdev" in it.name }.singleFile

        val userdev = when (val result = UserdevConfig.fromFile(userdevFile)) {
            is Either.Left -> throw (result.value
                ?: UnsupportedOperationException("Could not find userdev to generate patched minecraft with"))

            is Either.Right -> result.value
        }

        val mcpConfigPath = dependencyFile(patches, userdev.mcp)

        val mcpConfig = when (val result = McpConfigFile.configEntry(mcpConfigPath)) {
            is Either.Left -> throw (result.value
                ?: UnsupportedOperationException("Could not find ${userdev.mcp} to generate patched minecraft with"))

            is Either.Right -> result.value
        }

        val functionNames = listOf("rename", "mcinject", "merge", "mergeMappings", "preProcessJar")

        val patchDependencyNames =
            functionNames.asSequence().mapNotNull { mcpConfig.functions[it] } + listOf(userdev.binpatcher)

        val patchDependencies = patchDependencyNames.flatMap { it.version?.let(::listOf) ?: it.classpath }.map(dependencyHandler::create)

        val fixedPatches =
            configurationContainer.detachedConfiguration(*patchDependencies.toList().toTypedArray()).apply {
                isTransitive = false
            }

        val javaExecutable =
            metadata.javaVersion
                .executable(javaToolchainService)
                .get()
                .asFile

        fun mcpAction(
            name: String,
            template: Map<String, Any>,
            stdout: OutputStream?,
        ): McpAction? {
            val function = mcpConfig.functions[name]

            return function?.let {
                McpAction(
                    execOperations,
                    javaExecutable,
                    fixedPatches,
                    it,
                    mcpConfig,
                    template,
                    stdout,
                )
            }
        }

        val librariesFile = Files.createTempFile("libraries", ".txt")

        librariesFile.writeLines(libraries.flatMap { listOf("-e", it.absolutePath) })

        val patchLog = temporaryDir.resolve("patch.log").outputStream()
        val renameLog = temporaryDir.resolve("rename.log").outputStream()

        val logFiles = listOf(patchLog, renameLog)

        val patched =
            try {
                val merge =
                    mcpAction(
                        "merge",
                        mapOf(
                            "client" to clientJar,
                            "server" to serverJar,
                            "version" to minecraftVersion.get(),
                        ),
                        null,
                    )

                val rename =
                    mcpAction(
                        "rename",
                        mapOf(
                            "libraries" to librariesFile,
                        ),
                        renameLog,
                    )

                val patch =
                    PatchMcpAction(
                        execOperations,
                        javaExecutable,
                        fixedPatches,
                        mcpConfig,
                        userdevFile.toPath(),
                        userdev,
                        universal.singleFile,
                        patchLog,
                    )

                val official = mcpConfig.official
                val notchObf = userdev.notchObf

                zipFileSystem(mcpConfigPath.toPath()).use { fs ->
                    if (merge == null && rename == null) {
                        // Likely unobf, treat as such

                        val preProcess = mcpAction(
                            "preProcessJar",
                            mapOf(
                                "downloadClientOutput" to clientJar,
                                "downloadServerOutput" to serverJar,
                                "version" to minecraftVersion.get(),
                            ),
                            null,
                        )!!

                        return@use patch.execute(fs, preProcess.execute(fs))
                    }

                    merge!!
                    rename!!

                    if (official) {
                        val clientMappings = downloadMinecraftFile(
                            cacheDirectory,
                            metadata,
                            MinecraftDownloadVariant.ClientMappings,
                            isOffline,
                        )!!

                        temporaryDir.resolve("patch.log").outputStream().use {
                            val mergeMappings =
                                mcpAction(
                                    "mergeMappings",
                                    mapOf(
                                        "official" to clientMappings,
                                    ),
                                    it,
                                )!!

                            merge
                                .execute(fs)
                                .let { merged ->
                                    mergeMappings.execute(fs).let { mappings ->
                                        rename.execute(fs, "input" to merged, "mappings" to mappings)
                                    }
                                }.let { patch.execute(fs, it) }
                        }
                    } else {
                        val inject =
                            mcpAction(
                                "mcinject",
                                mapOf(
                                    "log" to temporaryDir.resolve("mcinject.log"),
                                ),
                                null,
                            )!!

                        val base =
                            if (notchObf) {
                                merge
                                    .execute(fs)
                                    .let { patch.execute(fs, it) }
                                    .let { rename.execute(fs, it) }
                            } else {
                                merge
                                    .execute(fs)
                                    .let { rename.execute(fs, it) }
                                    .let { patch.execute(fs, it) }
                            }

                        inject.execute(fs, base)
                    }
                }
            } finally {
                logFiles.forEach(Closeable::close)
            }

        val atFiles = zipFileSystem(userdevFile.toPath()).use { fs ->
            userdev.ats.flatMap {
                val path = fs.getPath(it)

                val paths =
                    if (path.isDirectory()) {
                        path.listDirectoryEntries()
                    } else {
                        listOf(path)
                    }

                paths.map {
                    val temp = Files.createTempFile("at-", ".tmp.cfg")

                    it.copyTo(temp, StandardCopyOption.REPLACE_EXISTING)

                    temp
                }
            }
        }

        val err = System.err

        try {
            temporaryDir.resolve("at.log").outputStream().use {
                System.setErr(PrintStream(it))

                TransformerProcessor.main(
                    "--inJar",
                    patched.toAbsolutePath().toString(),
                    "--outJar",
                    outputPath.toString(),
                    *atFiles
                        .flatMap { at ->
                            listOf("--atFile", at.toAbsolutePath().toString())
                        }.toTypedArray(),
                )
            }
        } finally {
            System.setErr(err)
        }

        clientJar.copyTo(clientExtra, StandardCopyOption.REPLACE_EXISTING)

        val isNeoforge = neoforge.getOrElse(false)

        if (isNeoforge) {
            DistAttributePostProcessor.postProcess(
                outputPath,
                clientJar,
                serverJar,
                cacheDirectory,
                metadata,
                isOffline,
            )
        }

        zipFileSystem(clientExtra).use { clientFs ->
            clientFs.getPath("/").walk {
                for (path in filter(Path::isRegularFile)) {
                    if (path.toString().endsWith(".class") || path.startsWith("/META-INF")) {
                        path.deleteExisting()
                    }
                }
            }

            if (isNeoforge) {
                val manifest = Manifest().apply {
                    mainAttributes.putValue(Attributes.Name.MANIFEST_VERSION.toString(), "1.0")
                    mainAttributes.putValue(DistAttributePostProcessor.NEOFORGE_DISTS_ATTRIBUTE_NAME, "client")
                }

                clientFs.getPath(JarFile.MANIFEST_NAME).outputStream().use(manifest::write)
            }
        }

        addMinecraftMarker(outputPath)
    }

    @TaskAction
    fun resolve() {
        val cacheDirectory = cacheParameters.directory.getAsPath()

        cacheExpensiveOperation(
            cacheDirectory,
            "patch-$PATCH_OPERATION_VERSION",
            patches.map { it.toPath() },
            output.getAsPath(),
            clientExtra.getAsPath(),
        ) { (output, clientExtra) ->
            resolve(cacheDirectory, output, clientExtra)
        }
    }
}
