import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.xuncorp.spw.workshop.gradle.PluginPermission

plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.2"
    id("com.xuncorp.spw.workshop") version "0.1.0-dev23"
}

group = "dev.gaboron.spwlyrics"
version = "3.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

kotlin {
    jvmToolchain(25)

    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
        freeCompilerArgs.add("-jvm-default=no-compatibility")
    }
}

dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly("com.github.Moriafly.spw-workshop-api:spw-workshop-api:0.1.0-dev23") {
        isTransitive = false
    }
    compileOnly("org.pf4j:pf4j:3.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.github.houbb:opencc4j:1.14.0")
    implementation("net.jthink:jaudiotagger:3.0.1")
    // Native decoding is leased from the separately installed SPW Media Runtime.
    implementation("io.github.gaboron.spwmedia:spw-media-sdk:0.2.0")
    // The host renders plugin pages with its Compose classes and platform-native Skiko.
    compileOnly("org.jetbrains.compose.desktop:desktop:1.12.2")
    // Use the host's native bridge for protocol activation, without bundling JNA.
    compileOnly("net.java.dev.jna:jna-platform:5.18.1")
    implementation("io.github.moriafly:salt-ui:3.0.0-beta03")
}

// UI components are packaged; shared runtimes and the native dispatcher belong to SPW.
configurations.runtimeClasspath {
    exclude(group = "org.jetbrains.kotlin")
    exclude(group = "org.jetbrains.skiko")
    // JNA's native resource cache keeps a plugin-owned JAR locked after unload on Windows.
    exclude(group = "net.java.dev.jna")
    exclude(group = "androidx.lifecycle")
    exclude(group = "org.jetbrains.androidx.lifecycle")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
    listOf("animation", "foundation", "material", "material3", "runtime", "ui", "desktop", "components")
        .forEach { exclude(group = "org.jetbrains.compose.$it") }
    listOf("animation", "foundation", "material", "material3", "runtime", "ui")
        .forEach { exclude(group = "androidx.compose.$it") }
}

tasks.jar {
    // Ignore Windows-only resources left in the output directory by earlier builds.
    exclude("native/compose/**")
}

spmod {
    PluginClass = "dev.gaboron.spwlyrics.integration.SpwLyricsPlugin"
    PluginId = "spw-lyrics"
    PluginName = "SPW Lyrics"
    PluginProvider = "GaBoron"
    PluginVersion = project.version.toString()
    PluginDescription = "为 Salt Player for Windows 自动搜索、匹配并加载多来源歌词。"
    PluginOpenSourceUrl = "https://github.com/GaBoron/SPW-Lyrics"
    PluginHasConfig = true
    PluginPermissions = listOf(PluginPermission.LIBRARY_READ, PluginPermission.CLASS_TRANSFORM)
}
tasks.named<Zip>("plugin") {
    archiveFileName.set("spw-lyrics-${project.version}.spmod")
    // SPW provides these; avoid bundling an older transitive Kotlin runtime.
    exclude("**/kotlin-stdlib-*.jar", "**/annotations-*.jar")
}
