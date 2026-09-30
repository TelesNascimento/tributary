plugins {
    java
}

repositories {
    mavenCentral()
}

val ibmPlugins = providers.gradleProperty("ibmPlugins")
val ibmJars = file("classpath.txt").readLines().filter { it.isNotBlank() }

dependencies {
    compileOnly(files(ibmJars.map { File(ibmPlugins.get(), it) }))
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(8) }
}

tasks {
    withType<JavaCompile> { options.encoding = "UTF-8" }
    test { useJUnitPlatform() }
    jar {
        archiveBaseName = "tributary-bridge"
        archiveVersion = ""
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        manifest { attributes("Main-Class" to "dev.tributary.bridge.Main") }
        from(configurations.runtimeClasspath.map { files -> files.map { if (it.isDirectory) it else zipTree(it) } })
    }
}
