import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.xuncorp.spw.workshop.gradle.PluginPermission

plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.1"
    id("com.xuncorp.spw.workshop") version "0.1.0-dev23"
}

group = "dev.gaboron.spwlyrics"
version = "1.1.2"

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
    // Both tool windows share the host's Compose classes and platform-native Skiko.
    compileOnly("org.jetbrains.compose.desktop:desktop:1.12.1")
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
    PluginPermissions = listOf(PluginPermission.LIBRARY_READ, PluginPermission.KEY_BINDINGS)
}
tasks.named<Zip>("plugin") {
    archiveFileName.set("spw-lyrics-${project.version}.spmod")
    // SPW provides these; avoid bundling an older transitive Kotlin runtime.
    exclude("**/kotlin-stdlib-*.jar", "**/annotations-*.jar")
}
