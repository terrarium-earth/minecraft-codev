package net.msrandom.minecraftcodev.core.utils

import org.apache.commons.lang3.StringUtils
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer

private val WORD_SEPARATOR = Regex("\\W+")

val String.asNamePart
    get() = takeIf { it != SourceSet.MAIN_SOURCE_SET_NAME }.orEmpty()

fun SourceSet.disambiguateName(elementName: String) = lowerCamelCaseGradleName(name.asNamePart, elementName)

fun lowerCamelCaseGradleName(vararg nameParts: String?): String {
    val nonEmptyParts = nameParts.flatMap {
        it?.takeIf(String::isNotEmpty)?.split(WORD_SEPARATOR) ?: emptyList()
    }

    return nonEmptyParts.drop(1).joinToString(
        separator = "",
        prefix = StringUtils.uncapitalize(nonEmptyParts.firstOrNull().orEmpty()),
        transform = StringUtils::capitalize,
    )
}

fun Project.createSourceSetElements(sourceSetHandler: (sourceSet: SourceSet) -> Unit) {
    extension<SourceSetContainer>().all(sourceSetHandler)
}

fun Project.createSourceSetConfigurations(name: String) {
    fun createConfiguration(name: String) =
        configurations.maybeCreate(name).apply {
            isCanBeConsumed = false
        }

    createSourceSetElements {
        createConfiguration(it.disambiguateName(name))
    }
}
