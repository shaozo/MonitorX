pluginManagement {
    includeBuild("gradle-plugin")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "MonitorX"

// Keep the same project coordinates when this directory is included in a host build.
include(":monitorX")
project(":monitorX").projectDir = file(".namespace")
listOf("api", "core", "source-okhttp", "okhttp", "runtime-android", "tools", "ui").forEach { name ->
    val path = ":monitorX:$name"
    include(path)
    project(path).projectDir = file(name)
}
