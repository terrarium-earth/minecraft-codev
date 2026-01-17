package net.msrandom.minecraftcodev.runs.task

import net.msrandom.minecraftcodev.core.utils.getAsPath
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.JavaExec
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.io.path.absolutePathString
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories

abstract class RunMinecraft : JavaExec() {
    abstract val configFile: RegularFileProperty
        @InputFile
        get

    abstract val envFile: RegularFileProperty
        @InputFile
        get

    override fun exec() {
        val asciiEncoder = StandardCharsets.US_ASCII.newEncoder()

        if (javaVersion.isJava9Compatible && classpath.all { asciiEncoder.canEncode(it.absolutePath) }) {
            val classpath = this.classpath

            this.classpath = objectFactory.fileCollection()

            val classpathFile = temporaryDir.resolve("classpath.txt").toPath()

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

        for (line in envFile.get().asFile.readLines()) {
            val separator = line.indexOf('=')

            require(separator > 0) {
                "Env entry $line did not contain ="
            }

            val name = line.substring(0, separator)
            val value = line.substring(separator + 1)

            environment(name, value)
        }

        args(configFile.getAsPath().absolutePathString())

        workingDir.toPath().createDirectories()

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
