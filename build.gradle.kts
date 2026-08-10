import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
}

group = "dev.gaboron.spwlyrics"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly("com.github.Moriafly:spw-workshop-api:0.1.0-dev20") {
        isTransitive = false
    }
    compileOnly("org.pf4j:pf4j:3.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}

tasks.test {
    useJUnitPlatform()
}

val pluginClass = "dev.gaboron.spwlyrics.integration.SpwLyricsPlugin"
val pluginId = "spw-lyrics"
val pluginName = "SPW Lyrics"
val pluginProvider = "GaBoron"

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Plugin-Class" to pluginClass,
            "Plugin-Id" to pluginId,
            "Plugin-Name" to pluginName,
            "Plugin-Version" to project.version.toString(),
            "Plugin-Provider" to pluginProvider,
            "Plugin-Description" to "Automatic multi-provider lyrics for Salt Player for Windows.",
            "Plugin-Has-Config" to "true",
        )
    }
}

tasks.register<Zip>("plugin") {
    group = "build"
    description = "Packages the SPW workshop plugin."
    archiveFileName.set("spw-lyrics-${project.version}.zip")
    destinationDirectory.set(layout.buildDirectory.dir("plugin"))

    into("classes") {
        with(tasks.named<Jar>("jar").get())
    }
    into("lib") {
        from(configurations.runtimeClasspath.map { files ->
            files.filter {
                it.extension == "jar" &&
                    !it.name.startsWith("kotlin-stdlib") &&
                    !it.name.startsWith("annotations-")
            }
        })
    }
    dependsOn(tasks.named("jar"))
}

