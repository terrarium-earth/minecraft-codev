package net.msrandom.minecraftcodev

import com.intellij.codeInsight.AttachSourcesProvider
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.jarFinder.AbstractAttachSourceProvider
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.externalSystem.util.task.TaskExecutionSpec
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
import net.msrandom.minecraftcodev.gradle.DecompileTaskInfo
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleLog
import java.io.IOException
import java.lang.Exception
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CompletableFuture
import kotlin.io.path.inputStream

fun isValidJar(path: Path): Boolean {
    try {
        return path.inputStream(StandardOpenOption.READ).use {
            val head = it.readNBytes(2)
            if (head.size < 2) {
                return false
            }
            return@use head[0] == 0x50.toByte() && head[1] == 0x4b.toByte()
        }
    } catch (e: Exception) {
        throw IllegalArgumentException("Unable to verify file $path: ${e.message}")
    }
}

private fun executeDecompile(
    settings: ExternalSystemTaskExecutionSettings,
    externalProjectPath: String,
    input: Path,
    task: DecompileTaskInfo,
    project: Project,
): CompletableFuture<Path> {
    val resultWrapper = CompletableFuture<Path>()

    val spec = TaskExecutionSpec.create(project, GradleConstants.SYSTEM_ID, DefaultRunExecutor.EXECUTOR_ID, settings)
        .withProgressExecutionMode(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
        .withListener(object : ExternalSystemTaskNotificationListener {
            override fun onSuccess(id: ExternalSystemTaskId) {
                try {
                    if (!isValidJar(task.output)) {
                        GradleLog.LOG.warn("Incorrect file header: ${task.output}. Unable to process decompiled file as a JAR file")
                        FileUtil.delete(task.output)
                        resultWrapper.completeExceptionally(IllegalStateException("Incorrect file header: ${task.output}."))
                        return
                    }

                    FileUtil.delete(task.output)
                    resultWrapper.complete(task.output)
                } catch (e: IOException) {
                    GradleLog.LOG.warn(e)
                    resultWrapper.completeExceptionally(e)
                    return
                }
            }

            override fun onFailure(id: ExternalSystemTaskId, exception: Exception) {
                FileUtil.delete(task.output)
                DecompileErrorHandler.handle(
                    project = project,
                    externalProjectPath = externalProjectPath,
                    input = input,
                )
                resultWrapper.completeExceptionally(IllegalStateException("Unable to download artifact."))
            }
        })
        .withActivateToolWindowBeforeRun(false)
        .withActivateToolWindowOnFailure(false)
        .build()

    ExternalSystemUtil.runTask(spec)

    return resultWrapper
}

private fun maybeDecompile(
    orderEntriesContainingFile: List<LibraryOrderEntry>,
    input: Path,
    task: DecompileTaskInfo,
    project: Project,
): Path? {
    if (isValidJar(task.output)) {
        return task.output
    }

    val module = orderEntriesContainingFile
        .filter { !it.isModuleLevel }
        .map { it.ownerModule }
        .find { ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, it) }
        ?: return null

    val gradleModuleData = CachedModuleDataFinder.getGradleModuleData(module) ?: return null
    val externalProjectPath = gradleModuleData.directoryToRunTask

    val settings = ExternalSystemTaskExecutionSettings().also {
        it.executionName = "codev.action.decompile"
        it.externalProjectPath = externalProjectPath
        it.taskNames = listOf(task.path)
        it.vmOptions = GradleSettings.getInstance(project).gradleVmOptions
        it.externalSystemIdString = GradleConstants.SYSTEM_ID.id
    }

    val future = executeDecompile(
        settings,
        externalProjectPath,
        input,
        task,
        project,
    )

    return future.get()
}

class DecompileMinecraftAttachSourcesProvider : AbstractAttachSourceProvider() {
    override fun getActions(
        orderEntries: List<LibraryOrderEntry>,
        psiFile: PsiFile
    ): Collection<AttachSourcesProvider.AttachSourcesAction> {
        val jar = getJarByPsiFile(psiFile) ?: return emptySet()

        if (orderEntries.none {
                !it.isModuleLevel && ExternalSystemApiUtil.isExternalSystemAwareModule(
                    GradleConstants.SYSTEM_ID,
                    it.ownerModule
                )
            }) {
            return emptySet()
        }

        return setOf(object : AttachSourcesProvider.AttachSourcesAction {
            override fun getName() = CodevBundle.message("codev.action.decompile")
            override fun getBusyText() = CodevBundle.message("codev.action.decompile.busy.text")

            override fun perform(orderEntriesContainingFile: List<LibraryOrderEntry>): ActionCallback {
                val library =
                    getLibraryFromOrderEntriesList(orderEntriesContainingFile) ?: return ActionCallback.REJECTED

                val taskName: String = TODO()

/*                val sources = maybeDecompile(orderEntriesContainingFile, jar, TODO(), psiFile.project)

                addSourceFile(sources, library)*/

                val callback = ActionCallback()

                return callback
            }
        })
    }
}
