import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.xuncorp.spw.workshop.gradle.PluginPermission

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
    id("org.jetbrains.compose") version "1.12.0"
    id("com.xuncorp.spw.workshop") version "0.1.0-dev21"
}

group = "dev.gaboron.spwlyrics"
version = "0.4.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        freeCompilerArgs.add("-jvm-default=no-compatibility")
    }
}

dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly("com.github.Moriafly.spw-workshop-api:spw-workshop-api:0.1.0-dev21") {
        isTransitive = false
    }
    compileOnly("org.pf4j:pf4j:3.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.github.houbb:opencc4j:1.14.0")
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation(compose.desktop.currentOs)
}

tasks.processResources {
    // Load our Skiko build explicitly instead of the renderer bundled with SPW.
    from({ zipTree(configurations.runtimeClasspath.get().single {
        it.name.startsWith("skiko-awt-runtime-windows-x64-")
    }) }) {
        into("native/compose")
        include("skiko-windows-x64.dll", "icudtl.dat")
    }
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
    // Compose's JetBrains modules and AndroidX modules can have identical JAR names.
    val libraryNames by lazy {
        val artifacts = configurations.runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts
        val duplicates = artifacts.groupBy { it.file.name }.filterValues { it.size > 1 }.keys
        artifacts.associate { artifact -> artifact.file.canonicalPath to
            if (artifact.file.name in duplicates) "${artifact.moduleVersion.id.group}-${artifact.file.name}" else artifact.file.name
        }
    }
    eachFile { name = libraryNames[file.canonicalPath] ?: name }
    // SPW provides these; avoid bundling an older transitive Kotlin runtime.
    exclude("**/kotlin-stdlib-*.jar", "**/annotations-*.jar")
}
