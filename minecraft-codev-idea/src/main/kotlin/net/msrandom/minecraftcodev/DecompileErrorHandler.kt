package net.msrandom.minecraftcodev

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.refreshAndFindVirtualFileOrDirectory
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.plugins.gradle.service.project.GradleNotification
import java.nio.file.Path
import kotlin.io.path.exists

internal object DecompileErrorHandler {
  private const val BUILD_GRADLE = "build.gradle"
  private const val BUILD_GRADLE_KTS = "$BUILD_GRADLE.kts"

  private const val ARTIFACT_NOT_FOUND_IN_REPOSITORY_NOTIFICATION_ID = "gradle.notifications.sources.download.from.repository.failed"

  fun handle(project: Project, externalProjectPath: String, input: Path) {
    showNotificationForRepository(project, externalProjectPath, input)
  }

  private fun showNotificationForRepository(project: Project,
                                            externalProjectPath: String,
                                            input: Path) {
    val actions = mutableListOf<AnAction>()
    actions.addIfNotNull(getAction(project, externalProjectPath))
    GradleNotification.gradleNotificationGroup
      .createNotification(
        title = CodevBundle.message("gradle.notifications.sources.download.failed.title"),
        content = CodevBundle.message("gradle.notifications.sources.download.from.repository.failed.content", input),
        NotificationType.WARNING
      )
      .addActions(actions as Collection<AnAction>)
      .setDisplayId(ARTIFACT_NOT_FOUND_IN_REPOSITORY_NOTIFICATION_ID)
      .notify(project)
  }

  private fun getAction(project: Project, externalProjectPath: String): AnAction? {
    val projectRoot = externalProjectPath.toNioPathOrNull() ?: return null
    val buildScriptPath: Path = getFilePathIfFileExist(projectRoot, BUILD_GRADLE)
                                ?: getFilePathIfFileExist(projectRoot, BUILD_GRADLE_KTS)
                                ?: return null
    val buildScriptVirtualFile = buildScriptPath.refreshAndFindVirtualFileOrDirectory() ?: return null
    return NotificationAction.createSimple(CodevBundle.message("gradle.notifications.sources.download.action.title", buildScriptVirtualFile.name)) {
      if (buildScriptVirtualFile.isValid) {
        FileEditorManager.getInstance(project).openFile(buildScriptVirtualFile)
      }
    }
  }

  private fun getFilePathIfFileExist(root: Path, fileName: String): Path? {
    val path = root.resolve(fileName)
    if (path.exists()) {
      return path
    }
    return null
  }
}
