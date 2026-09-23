import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.Exec
import com.xuncorp.spw.workshop.gradle.PluginPermission

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
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
val winUiProject = layout.projectDirectory.file("winui/SpwLyrics.WinUI/SpwLyrics.WinUI.csproj")
val winUiPublishDirectory = layout.buildDirectory.dir("winui-publish")

val publishWinUi by tasks.registering(Exec::class) {
    group = "build"
    description = "Publishes the unpackaged WinUI manual search companion."
    inputs.files(fileTree("winui/SpwLyrics.WinUI") { exclude("bin/**", "obj/**", "AppPackages/**") })
    outputs.dir(winUiPublishDirectory)
    doFirst { delete(winUiPublishDirectory) }
    commandLine(
        "dotnet", "publish", winUiProject.asFile.absolutePath,
        "-c", "Release", "-r", "win-x64", "--self-contained", "true",
        "-p:Platform=x64", "-p:WindowsAppSDKSelfContained=true",
        "-p:Version=${project.version}", "-p:AssemblyVersion=${project.version}.0",
        "-p:FileVersion=${project.version}.0", "-p:InformationalVersion=${project.version}",
        "-p:IncludeSourceRevisionInInformationalVersion=false",
        "-o", winUiPublishDirectory.get().asFile.absolutePath,
    )
}

tasks.named<Zip>("plugin") {
    into("ui") { from(winUiPublishDirectory) }
    // SPW provides these; avoid bundling an older transitive Kotlin runtime.
    exclude("**/kotlin-stdlib-*.jar", "**/annotations-*.jar")
    dependsOn(publishWinUi)
}
