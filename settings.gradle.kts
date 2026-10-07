pluginManagement {
    repositories {
        maven("https://jitpack.io")
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.xuncorp.spw.workshop") {
                useModule("com.github.Moriafly.spw-workshop-api:spw-workshop-gradle-plugin:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google { content { includeGroupByRegex("androidx\\..*") } }
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "spw-lyrics"
