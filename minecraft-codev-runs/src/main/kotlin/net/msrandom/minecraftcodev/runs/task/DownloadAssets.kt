package net.msrandom.minecraftcodev.runs.task

import kotlinx.coroutines.*
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
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
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

    private fun downloadObject(
        index: AssetsIndex,
        name: String,
        asset: AssetsIndex.AssetObject,
        resourcesDirectory: Directory,
        objectsDirectory: Directory,
    ) {
        val hash = asset.hash
        val section = hash.substring(0, 2)

        val file = if (index.mapToResources) {
            resourcesDirectory.file(name)
        } else {
            objectsDirectory.dir(section).file(asset.hash)
        }

        if (file.asFile.exists() && checkHashSha1(file.toPath(), hash)) {
            return
        }

        download(
            URI("https", "resources.download.minecraft.net", "/$section/$hash", null),
            hash,
            file.toPath(),
            cacheParameters.getIsOffline().get(),
        )
    }


    @TaskAction
    fun download() {
        val resourcesDirectory = resourcesDirectory.get()
        val indexesDirectory = assetsDirectory.dir("indexes").get()
        val objectsDirectory = assetsDirectory.dir("objects").get()

        val minecraftVersion = minecraftVersion.get()
        val metadata = cacheParameters.versionList().version(minecraftVersion)
        val assetIndex = metadata.assetIndex
        val assetIndexJson = indexesDirectory.file("${assetIndex.id}.json").toPath()

        download(
            assetIndex.url,
            assetIndex.sha1,
            assetIndexJson,
            cacheParameters.getIsOffline().get(),
        )

        val index = assetIndexJson.inputStream().use { Json.decodeFromStream<AssetsIndex>(it) }

        runBlocking(Dispatchers.IO + CoroutineName("Download Minecraft $minecraftVersion assets")) {
            coroutineScope {
                index.objects.map { (name, asset) ->
                    async {
                        downloadObject(index, name, asset, resourcesDirectory, objectsDirectory)
                    }
                }.awaitAll()
            }
        }
    }
}
