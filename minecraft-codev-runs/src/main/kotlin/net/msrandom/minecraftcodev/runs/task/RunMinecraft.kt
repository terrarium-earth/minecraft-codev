package net.msrandom.minecraftcodev.runs.task

import kotlinx.serialization.json.decodeFromStream
import net.msrandom.minecraftcodev.core.MinecraftCodevPlugin.Companion.json
import net.msrandom.minecraftcodev.core.utils.getAsPath
import net.msrandom.minecraftcodev.runs.SerializedRunConfig
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.JavaExec
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.io.path.absolutePathString
import kotlin.io.path.bufferedWriter
import kotlin.io.path.inputStream

abstract class RunMinecraft : JavaExec() {
    abstract val configFile: RegularFileProperty
        @InputFile
        get

    abstract val argFile: RegularFileProperty
        @Internal
        get

    override fun exec() {
        val asciiEncoder = StandardCharsets.US_ASCII.newEncoder()

        val config = configFile.getAsPath().inputStream().use {
            json.decodeFromStream<SerializedRunConfig>(it)
        }

        args(config.arguments)
        environment(config.environment)

        jvmArgs(config.jvmArguments)
        jvmArgs(config.properties.entries.map { (key, value) -> "-D$key=$value" })

        if (javaVersion.isJava9Compatible && classpath.all { asciiEncoder.canEncode(it.absolutePath) }) {
            val classpath = this.classpath

            this.classpath = objectFactory.fileCollection()

            val classpathFile = argFile.getAsPath()

            classpathFile.bufferedWriter().use {
                it.append("-classpath")
                it.append("\n")

                var insertSeparator = false

                for (file in classpath) {
                    if (insertSeparator) {
                        it.append(File.pathSeparatorChar)
                    }

                    it.append(quoteArgFileEntry(file.absolutePath))
                    insertSeparator = true
                }
            }

            jvmArgs("@${classpathFile.absolutePathString()}")
        }

        super.exec()
    }

    private fun quoteArgFileEntry(arg: String): String {
        if (!containsIllegalChar(arg)) {
            return arg
        }

        return buildString(arg.length * 2) {
            for (c in arg) {
                when (c) {
                    ' ', '#', '\'' -> append('"').append(c).append('"')
                    '"' -> append("\"\\\"\"")
                    '\n' -> append("\"\\n\"")
                    '\r' -> append("\"\\r\"")
                    '\t' -> append("\"\\t\"")
                    '\u000c' -> append("\"\\f\"")
                    else -> append(c)
                }
            }
        }
    }

    private fun containsIllegalChar(value: String) = if (value.length < ILLEGAL_ARG_FILE_CHARS.length) {
        containsIllegalChar(value, ILLEGAL_ARG_FILE_CHARS, value.length)
    } else {
        containsIllegalChar(ILLEGAL_ARG_FILE_CHARS, value, ILLEGAL_ARG_FILE_CHARS.length)
    }

    private fun containsIllegalChar(value: String, chars: String, end: Int) = (0 until end).any {
        chars.contains(value[it])
    }

    companion object {
        private const val ILLEGAL_ARG_FILE_CHARS = " #'\"\n\r\t\u000c"
    }
}
