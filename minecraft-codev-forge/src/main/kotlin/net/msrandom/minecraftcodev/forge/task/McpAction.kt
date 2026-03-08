package net.msrandom.minecraftcodev.forge.task

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.msrandom.minecraftcodev.core.utils.zipFileSystem
import net.msrandom.minecraftcodev.forge.McpConfig
import net.msrandom.minecraftcodev.forge.McpConfigFile
import net.msrandom.minecraftcodev.forge.PatchLibrary
import net.msrandom.minecraftcodev.forge.UserdevConfig
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger
import org.gradle.process.ExecOperations
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.text.startsWith
import kotlin.text.substring

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

sealed interface Step {
    val dependencies: List<LibraryStep>

    data class PreExecutedStep(val output: Path) : Step {
        override val dependencies get() = emptyList<LibraryStep>()
    }

    data class LibraryStep(
        val name: String,
        val resolvedArguments: List<StepArgument>,
        val library: PatchLibrary,
        val output: Path,
    ) : Step {
        override val dependencies = resolvedArguments.flatMap {
            if (it is StepArgument.StepOutput) {
                listOf(it.step)
            } else {
                emptyList()
            }
        }
    }
}

sealed interface StepArgument {
    @JvmInline
    value class Literal(val value: String) : StepArgument

    @JvmInline
    value class StepOutput(val step: Step.LibraryStep) : StepArgument
}

internal data class McpActionContext(
    val javaExecutable: File,
    val execOperations: ExecOperations,
    val logger: Logger,
    val logFile: (name: String) -> Path,
)

internal data class PatchResult(
    val patchedJar: Path,
    val nonPatchedJar: Path,
)

private fun executeStep(
    name: String,
    library: PatchLibrary,
    args: List<String>,
    fixedPatches: FileCollection,
    context: McpActionContext,
) {
    val jarFile = dependencyFile(fixedPatches, library.version ?: library.classpath[0])
    val stdOut = context.logFile("$name-stdout")
    val stdErr = context.logFile("$name-stderr")

    context.logger.info("Executing $name via $jarFile")

    val executionResult = stdOut.outputStream().use { stdOutStream ->
        stdErr.outputStream().use { stdErrStream ->
            context.execOperations.javaexec {
                executable(context.javaExecutable)

                val mainClass =
                    jarFile.let(::JarFile)
                        .use { jar ->
                            jar
                                .getInputStream(jar.getJarEntry(JarFile.MANIFEST_NAME))
                                .use(::Manifest)
                                .mainAttributes
                                .getValue(Attributes.Name.MAIN_CLASS)
                        }

                classpath(jarFile, fixedPatches)
                this.mainClass.set(mainClass)

                args(args)

                standardOutput = stdOutStream
                errorOutput = stdErrStream
            }
        }
    }

    try {
        executionResult.rethrowFailure()
        executionResult.assertNormalExitValue()
    } catch (e: Exception) {
        context.logger.error(stdErr.readText())

        throw e
    }
}

private fun patchStep(input: Step.LibraryStep, userdev: UserdevConfig, userdevFile: File): Step.LibraryStep {
    val output = Files.createTempFile("patched", ".jar")

    return Step.LibraryStep(
        "patch",
        userdev.binpatcher.args.map {
            if (it.startsWith("{")) {
                when (it.substring(1, it.length - 1)) {
                    "patch" -> {
                        val patches = Files.createTempFile("patches", ".lzma")

                        zipFileSystem(userdevFile.toPath()).use {
                            it.getPath(userdev.binpatches).copyTo(patches, StandardCopyOption.REPLACE_EXISTING)
                        }

                        StepArgument.Literal(patches.absolutePathString())
                    }

                    "clean" -> StepArgument.StepOutput(input)
                    "output" -> StepArgument.Literal(output.absolutePathString())
                    else -> throw UnsupportedOperationException("Unknown patch template $it")
                }
            } else {
                StepArgument.Literal(it)
            }
        },
        userdev.binpatcher,
        output,
    )
}

internal fun runBinaryPatcher(
    userdev: UserdevConfig,
    userdevFile: File,
    mcpConfigFile: McpConfigFile,
    patchStepPlacement: String?,
    preExecutedSteps: Map<String, () -> Path>,
    minecraftVersion: String,
    dependencyHandler: DependencyHandler,
    configurationContainer: ConfigurationContainer,
    context: McpActionContext,
): PatchResult {
    val (mcpConfig, mcpConfigPath) = mcpConfigFile

    val decompileStep = mcpConfig.steps.joined.first { it["type"] == "decompile" }
    val decompileFunction = mcpConfig.functions.getValue("decompile")
    val decompilerInputArg = decompileFunction.args[decompileFunction.args.size - 2]
    val executionSteps = hashMapOf<String, Step>()
    val argumentSteps = hashMapOf<String, Step>()

    zipFileSystem(mcpConfigPath).use { fileSystem ->
        for (step in mcpConfig.steps.joined) {
            val type = step.getValue("type")
            val stepName = step["name"] ?: type

            if (type == "decompile") {
                break
            }

            val preExecutedStep = preExecutedSteps[stepName]

            val step = if (preExecutedStep != null) {
                Step.PreExecutedStep(preExecutedStep())
            } else {
                val outputPath = Files.createTempFile("mcp-step", ".out")

                val library = mcpConfig.functions[type] ?: continue

                val resolvedArguments = library.args.map {
                    if (it.startsWith("{")) {
                        var name = it.substring(1, it.length - 1)

                        val alias = step[name]

                        if (alias != null) {
                            if (alias.startsWith("{")) {
                                name = alias.substring(1, alias.length - 1)
                            } else {
                                return@map StepArgument.Literal(alias)
                            }
                        }

                        val step = argumentSteps[name]

                        if (step != null) {
                            if (step is Step.LibraryStep) {
                                if (step.name == patchStepPlacement) {
                                    StepArgument.StepOutput(patchStep(step, userdev, userdevFile))
                                } else {
                                    StepArgument.StepOutput(step)
                                }
                            } else {
                                StepArgument.Literal((step as Step.PreExecutedStep).output.absolutePathString())
                            }
                        } else if (name == "version") {
                            StepArgument.Literal(minecraftVersion)
                        } else if (name == "output") {
                            StepArgument.Literal(outputPath.absolutePathString())
                        } else if (name == "log") {
                            StepArgument.Literal(context.logFile(stepName).absolutePathString())
                        } else {
                            val dataPath = mcpConfig.data[name]

                            if (dataPath != null) {
                                val dataOutput = Files.createTempFile("mcp-data", name)

                                fileSystem.getPath(
                                    dataPath,
                                ).copyTo(
                                    dataOutput,
                                    StandardCopyOption.REPLACE_EXISTING,
                                    StandardCopyOption.COPY_ATTRIBUTES,
                                )

                                StepArgument.Literal(dataOutput.absolutePathString())
                            } else {
                                throw UnsupportedOperationException("Unknown argument template $name")
                            }
                        }
                    } else {
                        StepArgument.Literal(it)
                    }
                }

                Step.LibraryStep(stepName, resolvedArguments, library, outputPath)
            }

            executionSteps[stepName] = step
            argumentSteps["${stepName}Output"] = step
        }
    }

    val patchDependencyNames =
        executionSteps.keys.asSequence().mapNotNull { mcpConfig.functions[it] } + listOf(userdev.binpatcher)

    val patchDependencies =
        patchDependencyNames.flatMap { listOfNotNull(it.version) + it.classpath }.map(dependencyHandler::create)

    val fixedPatches =
        configurationContainer.detachedConfiguration(*patchDependencies.toList().toTypedArray()).apply {
            isTransitive = false
        }

    fixedPatches.resolve()

    val decompilerArgName = decompilerInputArg.substring(1, decompilerInputArg.length - 1)
    val decompilerArgAlias = decompileStep[decompilerArgName]

    val decompilerStepInput = decompilerArgAlias?.substring(1, decompilerArgAlias.length - 1) ?: decompilerArgName
    val finalStep = argumentSteps.getValue(decompilerStepInput) as Step.LibraryStep

    val (stepToExecute, nonPatchedStep) = if (patchStepPlacement == null) {
        patchStep(finalStep, userdev, userdevFile) to finalStep
    } else {
        finalStep to executionSteps.getValue(patchStepPlacement) as Step.LibraryStep
    }

    runBlocking {
        val stepExecution = ConcurrentHashMap<Step.LibraryStep, Deferred<Unit>>()

        fun prepareAndExecute(step: Step.LibraryStep): Deferred<Unit> = stepExecution.computeIfAbsent(step) {
            async {
                step.dependencies.map {
                    prepareAndExecute(it)
                }.awaitAll()

                withContext(Dispatchers.IO) {
                    executeStep(step.name, step.library, step.resolvedArguments.map {
                        when (it) {
                            is StepArgument.Literal -> it.value
                            is StepArgument.StepOutput -> it.step.output.absolutePathString()
                        }
                    }, fixedPatches, context)
                }
            }
        }

        prepareAndExecute(stepToExecute).await()
    }

    return PatchResult(stepToExecute.output, nonPatchedStep.output)
}
