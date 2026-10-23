import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.publish.PublishingExtension
import java.util.Properties

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

val deployProps: Properties by lazy {
    Properties().apply {
        val candidates = listOf(
            rootProject.file("deploy.properties"),
            rootProject.file("../deploy.properties"),
            projectDir.resolve("deploy.properties")
        )
        candidates.firstOrNull { it.exists() }?.inputStream()?.use { load(it) }
    }
}

fun findDeployProp(key: String): String? =
    deployProps.getProperty(key)?.trim()?.removeSurrounding("\"")

allprojects {
    group = "com.shaovi.monitorx"
    version = providers.gradleProperty("monitorx.version").getOrElse("0.1.0-SNAPSHOT")
}

subprojects {
    pluginManager.withPlugin("maven-publish") {
        extensions.configure<PublishingExtension> {
            repositories {
                val repositoryUrl = providers.gradleProperty("monitorx.repo.url")
                    .orElse(providers.environmentVariable("MONITORX_REPO_URL"))
                    .orNull ?: findDeployProp("maven.url")
                if (!repositoryUrl.isNullOrBlank()) {
                    maven {
                        name = "monitorX"
                        url = uri(repositoryUrl)
                        credentials(PasswordCredentials::class) {
                            username = providers.gradleProperty("monitorx.repo.username")
                                .orElse(providers.environmentVariable("MONITORX_REPO_USERNAME"))
                                .orNull ?: findDeployProp("maven.username")
                            password = providers.gradleProperty("monitorx.repo.password")
                                .orElse(providers.environmentVariable("MONITORX_REPO_PASSWORD"))
                                .orNull ?: findDeployProp("maven.password")
                        }
                    }
                }
            }
        }
    }
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
