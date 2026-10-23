plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    id("com.vanniktech.maven.publish") version "0.37.0" apply false
}

allprojects {
    group = providers.gradleProperty("GROUP").get()
    version = providers.gradleProperty("VERSION_NAME").get()
}

tasks.register("publish") {
    group = "publishing"
    description = "Publishes all MonitorX library publications to configured repositories."
    dependsOn(subprojects.map { subproject ->
        subproject.tasks.matching { it.name == "publish" }
    })
}

tasks.register("publishToMavenLocal") {
    group = "publishing"
    description = "Publishes all MonitorX library publications to the local Maven repository."
    dependsOn(subprojects.map { subproject ->
        subproject.tasks.matching { it.name == "publishToMavenLocal" }
    })
}

tasks.register("publishToMavenCentralPortal") {
    group = "publishing"
    description = "Publishes and releases all MonitorX libraries through the Maven Central Portal."
    dependsOn(subprojects.map { subproject ->
        subproject.tasks.matching { it.name == "publishAndReleaseToMavenCentral" }
    })
}
