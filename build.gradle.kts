import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.19.0"
    id("com.diffplug.spotless") version "8.10.3"
}

group = "dev.tributary"
version = "0.1.0"

val localIde = providers.gradleProperty("idePath").map { file(it) }.orNull?.takeIf { it.isDirectory }
val platformVersion = providers.gradleProperty("platformVersion").orElse("2025.2")
val bridge = findProject(":bridge")

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        if (localIde != null) {
            local(localIde)
        } else {
            intellijIdeaCommunity(platformVersion)
        }
        pluginVerifier()
        zipSigner()
    }
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

tasks {
    withType<JavaCompile> {
        options.release = 21
        options.encoding = "UTF-8"
    }
    test { useJUnitPlatform() }
    prepareSandbox {
        if (bridge != null) {
            from(bridge.tasks.named("jar")) { into(pluginName.map { "$it/bridge" }) }
            from(file("bridge/classpath.txt")) { into(pluginName.map { "$it/bridge" }) }
        }
    }
}

spotless {
    java {
        target("src/**/*.java", "bridge/src/**/*.java")
        palantirJavaFormat("2.100.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

intellijPlatform {
    buildSearchableOptions = false
    pluginVerification {
        ides {
            if (localIde != null) {
                local(localIde)
            } else {
                create(IntelliJPlatformType.IntellijIdeaCommunity, platformVersion)
            }
        }
    }
    pluginConfiguration {
        name = "Tributary"
        ideaVersion { sinceBuild = "252" }
    }
}
