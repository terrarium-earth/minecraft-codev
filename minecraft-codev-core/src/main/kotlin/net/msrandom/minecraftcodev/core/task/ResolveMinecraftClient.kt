package net.msrandom.minecraftcodev.core.task

import net.msrandom.minecraftcodev.core.resolve.setupClient
import net.msrandom.minecraftcodev.core.utils.getAsPath
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class ResolveMinecraftClient : CachedMinecraftTask(), MinecraftVersioned {
    abstract val output: RegularFileProperty
        @OutputFile get

    init {
        output.convention(
            project.layout.file(
                minecraftVersion.map {
                    temporaryDir.resolve("minecraft-client-$it.jar")
                },
            ),
        )
    }

    @TaskAction
    fun extract() {
        val versionList = cacheParameters.versionList()

        val version = versionList.version(minecraftVersion.get())

        setupClient(
            cacheParameters.directory.asFile
                .get()
                .toPath(),
            output.getAsPath(),
            version,
            cacheParameters.getIsOffline().get(),
        )
    }
}
