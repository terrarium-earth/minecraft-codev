package net.msrandom.minecraftcodev.forge.task

import net.msrandom.minecraftcodev.core.utils.walk
import net.msrandom.minecraftcodev.core.utils.zipFileSystem
import net.msrandom.minecraftcodev.forge.McpConfig
import net.msrandom.minecraftcodev.forge.PatchLibrary
import net.msrandom.minecraftcodev.forge.UserdevConfig
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.process.ExecOperations
import java.io.File
import java.io.OutputStream
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.isRegularFile
import kotlin.io.path.notExists

internal fun dependencyFile(patches: FileCollection, notation: String): File {
    val name = notation.substringAfter(':').substringBefore(':')
    val files = patches.files

    val filtered = files.filter { name in it.name }

    if (filtered.isEmpty()) {
        throw UnsupportedOperationException("Could not find a matching jar for $notation in ${files.joinToString()}")
    }

    if (filtered.size > 1) {
        throw UnsupportedOperationException("Could not find file matching $notation, in ${filtered.joinToString()}")
    }

    return filtered.first()
}

open class McpAction(
    private val execOperations: ExecOperations,
    private val javaExecutable: File,
    protected val patches: FileCollection,
    private val library: PatchLibrary,
    private val mcpConfig: McpConfig,
    private val argumentTemplates: Map<String, Any>,
    private val stdout: OutputStream?,
) {
    open val inputName = "input"

    fun execute(fileSystem: FileSystem, input: Path) = execute(fileSystem, mapOf(inputName to input))

    fun execute(fileSystem: FileSystem, vararg inputs: Pair<String, Path>) = execute(fileSystem, mapOf(*inputs))

    protected open fun execute(fileSystem: FileSystem, inputs: Map<String, Path> = emptyMap()): Path {
        val jarFile = dependencyFile(patches, library.version ?: library.classpath[0])

        val output = Files.createTempFile("mcp-step", ".out")

        val executionResult =
            execOperations.javaexec {
                executable(javaExecutable)

                val mainClass =
                    jarFile.let(::JarFile)
                        .use { jar ->
                            jar
                                .getInputStream(jar.getJarEntry(JarFile.MANIFEST_NAME))
                                .use(::Manifest)
                                .mainAttributes
                                .getValue(Attributes.Name.MAIN_CLASS)
                        }

                classpath(jarFile, patches)
                this.mainClass.set(mainClass)

                val args =
                    library.args.map { arg ->
                        if (!arg.startsWith('{')) {
                            return@map arg
                        }

                        val template = arg.subSequence(1, arg.length - 1)

                        val templateReplacement =
                            output.takeIf { template == "output" }
                                ?: inputs[template]
                                ?: argumentTemplates[template]
                                ?: mcpConfig.data[template]?.let {
                                    val dataOutput = Files.createTempFile("mcp-data", template.toString())

                                    fileSystem.getPath(
                                        it,
                                    ).copyTo(
                                        dataOutput,
                                        StandardCopyOption.REPLACE_EXISTING,
                                        StandardCopyOption.COPY_ATTRIBUTES,
                                    )

                                    dataOutput
                                }
                                ?: throw UnsupportedOperationException("Unknown argument for MCP function ${library.args}: $template")

                        when (templateReplacement) {
                            is RegularFile -> templateReplacement.toString()
                            is Path -> templateReplacement.toAbsolutePath().toString()
                            is File -> templateReplacement.absolutePath
                            else -> templateReplacement.toString()
                        }
                    }

                this.args = args

                if (stdout != null) {
                    standardOutput = stdout
                }
            }

        executionResult.rethrowFailure()
        executionResult.assertNormalExitValue()

        return output
    }
}

class PatchMcpAction(
    execOperations: ExecOperations,
    javaExecutable: File,
    patches: FileCollection,
    mcpConfig: McpConfig,
    private val userdevPath: Path,
    private val userdevConfig: UserdevConfig,
    private val universal: File,
    private val unobfuscatedNeoforge: Boolean,
    logFile: OutputStream,
) : McpAction(
    execOperations,
    javaExecutable,
    patches,
    userdevConfig.binpatcher,
    mcpConfig,
    emptyMap(),
    logFile,
) {
    override val inputName
        get() = "clean"

    override fun execute(fileSystem: FileSystem, inputs: Map<String, Path>): Path {
        val patched =
            run {
                val patches = Files.createTempFile("patches", "lzma")

                zipFileSystem(userdevPath).use {
                    it.getPath(userdevConfig.binpatches).copyTo(patches, StandardCopyOption.REPLACE_EXISTING)
                }

                super.execute(fileSystem, inputs + mapOf("patch" to patches))
            }

        val input = inputs.getValue("clean")

        zipFileSystem(patched).use { patchedZip ->
            // Add missing non-patched files
            zipFileSystem(input).use { inputZip ->
                inputZip.getPath("/").walk {
                    for (path in filter(Path::isRegularFile)) {
                        val output = patchedZip.getPath(path.toString())

                        if (output.notExists()) {
                            output.parent?.createDirectories()
                            path.copyTo(output, StandardCopyOption.COPY_ATTRIBUTES)
                        }
                    }
                }
            }

            val filters = userdevConfig.universalFilters.map(::Regex)

            if (!unobfuscatedNeoforge) {
                zipFileSystem(universal.toPath()).use { universalZip ->
                    val root = universalZip.getPath("/")

                    root.walk {
                        for (path in filter(Path::isRegularFile)) {
                            val name = root.relativize(path).toString()

                            if (!filters.all { name matches it }) {
                                continue
                            }

                            val output = patchedZip.getPath(name)

                            output.parent?.createDirectories()
                            path.copyTo(
                                output,
                                StandardCopyOption.COPY_ATTRIBUTES,
                                StandardCopyOption.REPLACE_EXISTING
                            )
                        }
                    }
                }
            }

            // Add userdev injects
            val inject = userdevConfig.inject

            if (inject == null) {
                return patched
            }

            zipFileSystem(userdevPath).use userdev@{ userdevZip ->
                val injectPath = userdevZip.getPath("/$inject")

                if (injectPath.notExists()) {
                    return@userdev
                }

                injectPath.walk {
                    for (path in filter(Path::isRegularFile)) {
                        val output = patchedZip.getPath(injectPath.relativize(path).toString())

                        output.parent?.createDirectories()
                        path.copyTo(output, StandardCopyOption.COPY_ATTRIBUTES)
                    }
                }
            }
        }

        return patched
    }
}
