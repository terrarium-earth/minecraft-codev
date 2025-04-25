package net.msrandom.minecraftcodev.mixins.mixin

import com.google.common.collect.SetMultimap
import org.objectweb.asm.tree.ClassNode
import org.spongepowered.asm.mixin.MixinEnvironment
import org.spongepowered.asm.mixin.extensibility.IMixinInfo
import org.spongepowered.asm.mixin.transformer.ext.IExtension
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext
import java.util.*

class GradleMixinRecorderExtension : IExtension {
    companion object {
        private val targetClassContextClass =
            Class.forName("org.spongepowered.asm.mixin.transformer.TargetClassContext")
        private val mixinsField = targetClassContextClass.getDeclaredField("mixins")

        init {
            mixinsField.isAccessible = true
        }
    }

    var appliedMixins: SetMultimap<String, String>? = null

    override fun checkActive(environment: MixinEnvironment?) = true

    override fun preApply(context: ITargetClassContext) {}

    override fun postApply(context: ITargetClassContext) {
        val mixins = mixinsField.get(context) as SortedSet<IMixinInfo>
        if (mixins.isNotEmpty()) {
            for (info in mixins) {
                appliedMixins!!.put(info.config.name, info.name)
            }
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