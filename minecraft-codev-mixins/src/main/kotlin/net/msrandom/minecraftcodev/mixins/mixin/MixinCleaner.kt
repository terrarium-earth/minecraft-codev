package net.msrandom.minecraftcodev.mixins.mixin

import org.spongepowered.asm.mixin.MixinEnvironment
import org.spongepowered.asm.mixin.Mixins
import org.spongepowered.asm.mixin.extensibility.IMixinConfig
import org.spongepowered.asm.mixin.transformer.Config
import org.spongepowered.asm.mixin.transformer.IMixinTransformer

object MixinCleaner {
    private val registeredConfigsField = Mixins::class.java.getDeclaredField("registeredConfigs").apply { isAccessible = true }
    private val allConfigsField = Config::class.java.getDeclaredField("allConfigs").apply { isAccessible = true }

    private val mixinTransformerClass = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer")
    private val processorField = mixinTransformerClass.getDeclaredField("processor").apply { isAccessible = true }

    private val mixinProcessorClass = Class.forName("org.spongepowered.asm.mixin.transformer.MixinProcessor")
    private val configsField = mixinProcessorClass.getDeclaredField("configs").apply { isAccessible = true }

    @Suppress("DEPRECATION")
    fun run(transformer: IMixinTransformer) {
        MixinEnvironment.getCurrentEnvironment().mixinConfigs.clear()
        Mixins.getConfigs().clear()
        (allConfigsField[null] as MutableMap<String, Config>).clear()
        (registeredConfigsField[null] as MutableCollection<*>).clear()
        (configsField[processorField[transformer]] as MutableList<IMixinConfig>).clear()
    }
}