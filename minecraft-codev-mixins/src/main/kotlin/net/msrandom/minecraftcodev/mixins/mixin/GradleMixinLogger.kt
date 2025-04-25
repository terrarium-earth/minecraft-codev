package net.msrandom.minecraftcodev.mixins.mixin

import org.slf4j.LoggerFactory
import org.spongepowered.asm.logging.Level
import org.spongepowered.asm.logging.LoggerAdapterAbstract

class GradleMixinLogger(val name: String) : LoggerAdapterAbstract(name) {
    companion object {
        private const val PREFIX = "[Mixin] "
    }

    private val logger = LoggerFactory.getLogger(name)

    override fun getType() = "Gradle Logger"

    override fun catching(level: Level, t: Throwable) =
        logger.info("${PREFIX}Catching {}: {}", t.javaClass.getName(), t.message, t)

    override fun log(level: Level, message: String, vararg params: Any) {
        val message = PREFIX + message
        when (level) {
            Level.TRACE -> logger.trace(message, *params)
            Level.DEBUG -> logger.debug(message, *params)
            Level.INFO -> logger.info(message, *params)
            Level.WARN -> logger.warn(message, *params)
            Level.ERROR -> logger.error(message, *params)
            Level.FATAL -> logger.error("[FATAL] $message", *params)
        }
    }

    override fun log(level: Level, message: String, t: Throwable) {
        log(level, message, t as Any)
    }

    override fun <T : Throwable> throwing(t: T): T {
        logger.warn("${PREFIX}Throwing {}: {}", t.javaClass.getName(), t.message, t)
        return t
    }
}