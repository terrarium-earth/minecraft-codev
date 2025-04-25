package net.msrandom.minecraftcodev.mixins.mixin

import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual
import org.spongepowered.asm.launch.platform.container.IContainerHandle
import org.spongepowered.asm.logging.ILogger
import org.spongepowered.asm.mixin.MixinEnvironment
import org.spongepowered.asm.mixin.MixinEnvironment.Phase
import org.spongepowered.asm.mixin.MixinEnvironment.Side
import org.spongepowered.asm.mixin.Mixins
import org.spongepowered.asm.mixin.transformer.Config
import org.spongepowered.asm.mixin.transformer.IMixinTransformer
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory
import org.spongepowered.asm.mixin.transformer.ext.Extensions
import org.spongepowered.asm.service.IClassBytecodeProvider
import org.spongepowered.asm.service.IClassProvider
import org.spongepowered.asm.service.IClassTracker
import org.spongepowered.asm.service.MixinServiceAbstract
import org.spongepowered.asm.util.IConsumer
import java.io.File
import java.io.FileNotFoundException
import java.net.URLClassLoader
import java.nio.file.Path
import javax.annotation.concurrent.NotThreadSafe
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream

@NotThreadSafe
class GradleMixinService : MixinServiceAbstract() {
    lateinit var phaseConsumer: IConsumer<Phase>
        private set

    private lateinit var classpath: URLClassLoader

    val transformer: IMixinTransformer by lazy {
        getInternal(IMixinTransformerFactory::class.java).createTransformer()
    }

    val recorderExtension = GradleMixinRecorderExtension()

    override fun init() {
        (transformer.extensions as Extensions).add(recorderExtension)
        super.init()
    }

    /**
     * Thread safe accessor
     */
    fun <R> use(
        classpath: Iterable<File>,
        side: Side,
        action: GradleMixinService.() -> R,
    ) = synchronized(this) {
        this.classpath = URLClassLoader(classpath.map { it.toURI().toURL() }.toTypedArray(), javaClass.classLoader)

        (registeredConfigsField[null] as MutableCollection<*>).clear()

        sideField[MixinEnvironment.getCurrentEnvironment()] = Side.UNKNOWN
        MixinEnvironment.getCurrentEnvironment().side = side

        @Suppress("DEPRECATION")
        MixinEnvironment.getCurrentEnvironment().mixinConfigs.clear()
        Mixins.getConfigs().clear()
        (allConfigsField[null] as MutableMap<String, Config>).clear()

        recorderExtension.appliedMixins = null

        this.action()
    }

    override fun getName() = "Gradle"

    override fun isValid() = true

    override fun getClassProvider() =
        object : IClassProvider {
            @Deprecated("Deprecated in Java", ReplaceWith("emptyArray<URL>()", "java.net.URL"))
            override fun getClassPath() =
                if (this@GradleMixinService::classpath.isInitialized) classpath.urLs else emptyArray()

            override fun findClass(name: String) =
                if (this@GradleMixinService::classpath.isInitialized) classpath.loadClass(name) else Class.forName(name)

            override fun findClass(
                name: String,
                initialize: Boolean,
            ): Class<*>? {
                return try {
                    Class.forName(name, initialize, javaClass.classLoader)
                } catch (e: ClassNotFoundException) {
                    if (this@GradleMixinService::classpath.isInitialized)
                        Class.forName(name, initialize, classpath)
                    else throw e
                }
            }

            override fun findAgentClass(
                name: String,
                initialize: Boolean,
            ) = findClass(name, initialize)
        }

    override fun getBytecodeProvider() =
        object : IClassBytecodeProvider {
            override fun getClassNode(name: String) = getClassNode(name, true)

            override fun getClassNode(
                name: String,
                runTransformers: Boolean,
            ) = getClassNode(name, runTransformers, 0)

            override fun getClassNode(
                name: String,
                runTransformers: Boolean,
                readerFlags: Int,
            ) = getResourceAsStream(name.replace('.', '/') + ".class")
                ?.use(::ClassReader)
                ?.let { reader -> ClassNode().also { reader.accept(it, readerFlags) } }
                ?: throw FileNotFoundException(name)
        }

    override fun getTransformerProvider() = null

    override fun getClassTracker() =
        object : IClassTracker {
            override fun registerInvalidClass(className: String?) = Unit

            override fun isClassLoaded(className: String?) = false

            override fun getClassRestrictions(className: String?) = ""
        }

    override fun getAuditTrail() = null

    override fun getPlatformAgents() = listOf("org.spongepowered.asm.launch.platform.MixinPlatformAgentDefault")

    override fun getPrimaryContainer() =
        object : ContainerHandleVirtual("codev") {
            override fun getDescription() = "Minecraft Codev Dummy Mixin Container"
        }

    override fun getResourceAsStream(name: String) =
        if (this@GradleMixinService::classpath.isInitialized) classpath.getResourceAsStream(name)
        else Path(name).takeIf(Path::exists)?.inputStream()

    @Deprecated("Deprecated in Java")
    override fun wire(
        phase: Phase,
        phaseConsumer: IConsumer<Phase>,
    ) {
        @Suppress("DEPRECATION")
        super.wire(phase, phaseConsumer)
        this.phaseConsumer = phaseConsumer
    }

    override fun createLogger(name: String): ILogger {
        return GradleMixinLogger(name)
    }

    companion object {
        private val registeredConfigsField =
            Mixins::class.java.getDeclaredField("registeredConfigs").apply { isAccessible = true }
        private val sideField = MixinEnvironment::class.java.getDeclaredField("side").apply { isAccessible = true }
        private val allConfigsField = Config::class.java.getDeclaredField("allConfigs").apply { isAccessible = true }
    }
}
