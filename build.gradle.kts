import org.gradle.api.credentials.PasswordCredentials
import org.gradle.api.publish.PublishingExtension

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

allprojects {
    group = "com.hkjustar.monitorx"
    version = providers.gradleProperty("monitorx.version").getOrElse("0.1.0-SNAPSHOT")
}

subprojects {
    pluginManager.withPlugin("maven-publish") {
        extensions.configure<PublishingExtension> {
            repositories {
                val repositoryUrl = providers.gradleProperty("monitorx.repo.url")
                    .orElse(providers.environmentVariable("MONITORX_REPO_URL"))
                if (repositoryUrl.isPresent) {
                    maven {
                        name = "monitorX"
                        url = uri(repositoryUrl.get())
                        credentials(PasswordCredentials::class) {
                            username = providers.gradleProperty("monitorx.repo.username")
                                .orElse(providers.environmentVariable("MONITORX_REPO_USERNAME")).orNull
                            password = providers.gradleProperty("monitorx.repo.password")
                                .orElse(providers.environmentVariable("MONITORX_REPO_PASSWORD")).orNull
                        }
                    }
                }
            }
        }
    }
}
