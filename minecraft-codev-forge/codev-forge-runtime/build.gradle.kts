plugins {
    `maven-publish`
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    implementation(group = "com.google.guava", name = "guava", version = "31.1-jre")
    implementation(group = "cpw.mods", name = "modlauncher", version = "8.1.3")
    implementation(group = "net.fabricmc", name = "mapping-io", version = "0.7.1")
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
