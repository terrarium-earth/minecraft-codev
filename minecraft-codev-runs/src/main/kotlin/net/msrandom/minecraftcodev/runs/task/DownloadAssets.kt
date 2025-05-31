package net.msrandom.minecraftcodev.runs.task

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import net.msrandom.minecraftcodev.core.AssetsIndex
import net.msrandom.minecraftcodev.core.task.CachedMinecraftTask
import net.msrandom.minecraftcodev.core.task.MinecraftVersioned
import net.msrandom.minecraftcodev.core.task.versionList
import net.msrandom.minecraftcodev.core.utils.checkHashSha1
import net.msrandom.minecraftcodev.core.utils.download
import net.msrandom.minecraftcodev.core.utils.extension
import net.msrandom.minecraftcodev.core.utils.toPath
import net.msrandom.minecraftcodev.runs.RunsContainer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import settingdust.lazyyyyy.util.collect
import settingdust.lazyyyyy.util.concurrent
import settingdust.lazyyyyy.util.filter
import settingdust.lazyyyyy.util.map
import java.net.URI
import kotlin.io.path.inputStream

abstract class DownloadAssets : CachedMinecraftTask(), MinecraftVersioned {
    abstract val assetsDirectory: DirectoryProperty
        @Internal get

    abstract val resourcesDirectory: DirectoryProperty
        @Internal get

    init {
        assetsDirectory.convention(project.extension<RunsContainer>().assetsDirectory)
        resourcesDirectory.convention(project.extension<RunsContainer>().resourcesDirectory)
    }

    @TaskAction
    fun download() {
        val resourcesDirectory = resourcesDirectory.get()
        val indexesDirectory = assetsDirectory.dir("indexes").get()
        val objectsDirectory = assetsDirectory.dir("objects").get()

        runBlocking(Dispatchers.IO + CoroutineName("Download Minecraft assets")) {
            val metadata = cacheParameters.versionList().version(minecraftVersion.get())
            val assetIndex = metadata.assetIndex
            val assetIndexJson = indexesDirectory.file("${assetIndex.id}.json").toPath()

            download(
                assetIndex.url,
                assetIndex.sha1,
                assetIndexJson,
                cacheParameters.getIsOffline().get(),
            )

            val index = assetIndexJson.inputStream().use { Json.decodeFromStream<AssetsIndex>(it) }

            index.objects.asSequence().asFlow().concurrent()
                .map { (name, asset) ->
                    val hash = asset.hash
                    val section = hash.substring(0, 2)
                    val file =
                        if (index.mapToResources) {
                            resourcesDirectory.file(name)
                        } else {
                            objectsDirectory.dir(section).file(asset.hash)
                        }
                    Triple(hash, file, section)
                }
                .filter { (hash, file) ->
                    if (file.asFile.exists()) {
                        if (checkHashSha1(file.toPath(), hash)) {
                            return@filter false
                        } else {
                            file.asFile.delete()
                        }
                    }
                    return@filter true
                }
                .collect { (hash, file, section) ->
                    download(
                        URI("https", "resources.download.minecraft.net", "/$section/$hash", null),
                        hash,
                        file.toPath(),
                        cacheParameters.getIsOffline().get(),
                    )
                }
        }
    }
}
