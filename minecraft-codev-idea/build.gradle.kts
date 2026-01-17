import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    java
    groovy

    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

group = "net.msrandom"
version = "1.0-SNAPSHOT"

val gradleToolingExtension: SourceSet by sourceSets.creating

val gradleToolingExtensionJar = tasks.register<Jar>(gradleToolingExtension.jarTaskName) {
    from(gradleToolingExtension.output)

    archiveClassifier.set(gradleToolingExtension.name)
}

tasks.named(gradleToolingExtension.getCompileTaskName("groovy"), GroovyCompile::class) {
    classpath += files(gradleToolingExtension.kotlin.destinationDirectory)
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }

    maven(url = "https://maven.msrandom.net/repository/root/")
}

/*configurations.named(gradleToolingExtension.compileClasspathConfigurationName) {
    resolutionStrategy {
        dependencySubstitution {
            substitute(module("org.jetbrains.intellij.deps:gradle-api")).using(module("dev.gradleplugins:gradle-api:8.8"))
        }
    }
}*/

dependencies {
    compileOnly("com.jetbrains.intellij.platform:eel:latest.release")
    compileOnly("com.jetbrains.intellij.platform:external-system-impl:latest.release")
    compileOnly("com.jetbrains.intellij.platform:eel-provider:latest.release")

    gradleToolingExtension.implementationConfigurationName(kotlin("stdlib"))
    gradleToolingExtension.implementationConfigurationName("org.apache.groovy:groovy:5.0.3")

    gradleToolingExtension.compileOnlyConfigurationName(group = "com.jetbrains.intellij.gradle", name = "gradle-tooling-extension", version = "253.29346.240")

    gradleToolingExtension.implementationConfigurationName(projects.minecraftCodevDecompiler) {
        isTransitive = false
    }

    implementation(files(gradleToolingExtensionJar))

    intellijPlatform {
        intellijIdeaCommunity("2024.3.1")

        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.gradle")
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }

    withType<KotlinCompile> {
        compilerOptions.jvmTarget = JvmTarget.JVM_11
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}

publishing {
    publications {
        create<MavenPublication>("idea") {
            groupId = "com.jetbrains.plugins"
            artifactId = "net.msrandom.minecraft-codev"

            from(components["java"])
        }
    }
}
