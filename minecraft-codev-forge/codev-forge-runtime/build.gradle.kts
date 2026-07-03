plugins {
    `maven-publish`
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation(group = "com.google.guava", name = "guava", version = "33.5.0-jre")
    implementation(group = "cpw.mods", name = "modlauncher", version = "10.2.4")
    implementation(group = "net.fabricmc", name = "mapping-io", version = "0.8.0")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }

    repositories {
        mavenLocal()

        maven("https://maven.msrandom.net/repository/root/") {
            credentials {
                val mavenUsername: String? by project
                val mavenPassword: String? by project

                username = mavenUsername
                password = mavenPassword
            }
        }
    }
}
