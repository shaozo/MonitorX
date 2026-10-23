import java.util.Properties

plugins {
    `kotlin-dsl`
    `maven-publish`
}

group = "com.shaovi.monitorx"
version = providers.gradleProperty("monitorx.version").getOrElse("0.1.0-SNAPSHOT")

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation("com.android.tools.build:gradle:9.3.2")
    implementation("org.ow2.asm:asm:9.7.1")
    implementation("org.ow2.asm:asm-util:9.7.1")
    implementation("org.ow2.asm:asm-commons:9.7.1")
}

gradlePlugin {
    plugins {
        register("monitorXPlugin") {
            id = "com.shaovi.monitorx"
            implementationClass = "com.shaovi.monitorx.gradle.MonitorOkHttpPlugin"
        }
    }
}

val deployProps: Properties by lazy {
    Properties().apply {
        val candidates = listOf(
            file("deploy.properties"),
            file("../deploy.properties"),
            file("../../deploy.properties")
        )
        candidates.firstOrNull { it.exists() }?.inputStream()?.use { load(it) }
    }
}

fun findDeployProp(key: String): String? =
    deployProps.getProperty(key)?.trim()?.removeSurrounding("\"")

publishing {
    repositories {
        maven { url = uri(layout.buildDirectory.dir("repo")) }
        val repositoryUrl = providers.gradleProperty("monitorx.repo.url")
            .orElse(providers.environmentVariable("MONITORX_REPO_URL"))
            .orNull ?: findDeployProp("maven.url")
        if (!repositoryUrl.isNullOrBlank()) {
            maven {
                name = "monitorX"
                url = uri(repositoryUrl)
                credentials {
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
