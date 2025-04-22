package net.msrandom.minecraftcodev.mixins.mixin

import com.google.common.collect.MultimapBuilder
import com.google.common.collect.SetMultimap
import org.objectweb.asm.tree.ClassNode
import org.spongepowered.asm.mixin.MixinEnvironment
import org.spongepowered.asm.mixin.extensibility.IMixinInfo
import org.spongepowered.asm.mixin.transformer.ext.IExtension
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext
import java.util.*

class GradleMixinRecorderExtension : IExtension {
    companion object {
        val RECORDED: SetMultimap<String, String> =
            MultimapBuilder.SetMultimapBuilder.hashKeys().hashSetValues().build()

        private val targetClassContextClass =
            Class.forName("org.spongepowered.asm.mixin.transformer.TargetClassContext")
        private val classNameField = targetClassContextClass.getDeclaredField("className")
        private val mixinsField = targetClassContextClass.getDeclaredField("mixins")

        init {
            classNameField.isAccessible = true
            mixinsField.isAccessible = true
        }

    }

    override fun checkActive(environment: MixinEnvironment?) = true

    override fun preApply(context: ITargetClassContext) {}

    override fun postApply(context: ITargetClassContext) {
        val className = classNameField.get(context) as String
        val mixins = mixinsField.get(context) as SortedSet<IMixinInfo>
        if (mixins.isNotEmpty()) {
            RECORDED.putAll(className, mixins.map { it.className })
        }
    }

    override fun export(
        env: MixinEnvironment?,
        name: String?,
        force: Boolean,
        classNode: ClassNode?
    ) {
    }
}