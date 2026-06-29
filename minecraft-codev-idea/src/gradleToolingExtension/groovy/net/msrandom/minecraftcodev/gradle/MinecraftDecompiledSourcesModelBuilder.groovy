package net.msrandom.minecraftcodev.gradle


import groovy.transform.CompileStatic
import net.msrandom.minecraftcodev.decompiler.task.Decompile
import org.gradle.api.Project
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext

@CompileStatic
class MinecraftDecompiledSourcesModelBuilder extends AbstractModelBuilderService {
    boolean canBuild(String modelName) {
        modelName == MinecraftDecompiledSourcesModel.name
    }

    MinecraftDecompiledSourcesModel buildAll(String modelName, Project project, ModelBuilderContext modelBuilderContext) {
        if (!project.plugins.hasPlugin("minecraft-codev-decompiler")) {
            return null
        }

        def model = new MinecraftDecompiledSourcesModelImpl()

        for (def decompile in project.tasks.withType(Decompile)) {
            def file = decompile.inputFile.asFile.get().toPath()

            model.decompileTasks[file] = new DecompileTaskInfo(decompile.outputFile.asFile.get().toPath(), decompile.path)
        }

        model
    }
}
