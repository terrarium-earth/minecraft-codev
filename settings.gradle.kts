enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "minecraft-codev"

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)

    repositories {
        mavenCentral()
        gradlePluginPortal()

        maven(url = "https://maven.msrandom.net/repository/root/")
        maven(url = "https://maven.minecraftforge.net/")
        maven(url = "https://maven.fabricmc.net/")
    }
}

include("minecraft-codev-core", "minecraft-codev-core:side-annotations")
include("minecraft-codev-decompiler")
include("minecraft-codev-remapper")
include("minecraft-codev-forge", "minecraft-codev-forge:forge-runtime")
include("minecraft-codev-fabric")
include("minecraft-codev-includes")
include("minecraft-codev-intersections")
include("minecraft-codev-runs")
include("minecraft-codev-access-widener")
include("minecraft-codev-mixins")

include("minecraft-codev-idea")
