import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.Exec

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
}

group = "dev.gaboron.spwlyrics"
version = "0.3.0"

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
    compileOnly("com.github.Moriafly:spw-workshop-api:0.1.0-dev20") {
        isTransitive = false
    }
    compileOnly("org.pf4j:pf4j:3.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.github.houbb:opencc4j:1.14.0")

    testImplementation(kotlin("test-junit5"))
    testImplementation("com.github.Moriafly:spw-workshop-api:0.1.0-dev20") {
        isTransitive = false
    }
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}

tasks.test {
    useJUnitPlatform()
}

val pluginClass = "dev.gaboron.spwlyrics.integration.SpwLyricsPlugin"
val pluginId = "spw-lyrics"
val pluginName = "SPW Lyrics"
val pluginProvider = "GaBoron"
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
        "-o", winUiPublishDirectory.get().asFile.absolutePath,
    )
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Plugin-Class" to pluginClass,
            "Plugin-Id" to pluginId,
            "Plugin-Name" to pluginName,
            "Plugin-Version" to project.version.toString(),
            "Plugin-Provider" to pluginProvider,
            "Plugin-Description" to "为 Salt Player for Windows 自动搜索、匹配并加载多来源歌词。",
            "Plugin-Open-Source-Url" to "https://github.com/GaBoron/SPW-Lyrics",
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
    into("ui") { from(winUiPublishDirectory) }
    dependsOn(tasks.named("jar"), publishWinUi)
}
