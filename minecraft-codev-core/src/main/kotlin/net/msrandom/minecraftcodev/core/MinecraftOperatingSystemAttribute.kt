package net.msrandom.minecraftcodev.core

import org.apache.commons.lang3.SystemUtils
import org.gradle.api.Named
import org.gradle.api.attributes.Attribute

interface MinecraftOperatingSystemAttribute : Named {
    companion object {
        val attribute: Attribute<MinecraftOperatingSystemAttribute> = Attribute.of("net.msrandom.codev.operatingSystem", MinecraftOperatingSystemAttribute::class.java)
    }
}

fun operatingSystemName() = if (SystemUtils.IS_OS_WINDOWS) {
    "windows"
} else if (SystemUtils.IS_OS_MAC_OSX) {
    "osx"
} else if (SystemUtils.IS_OS_LINUX) {
    "linux"
} else {
    throw UnsupportedOperationException("Unknown operating system ${SystemUtils.OS_NAME}")
}
