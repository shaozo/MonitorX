plugins {
    `kotlin-dsl`
    `maven-publish`
}

group = "com.hkjustar.monitorx"
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
            id = "com.hkjustar.monitorx"
            implementationClass = "MonitorOkHttpPlugin"
        }
        register("legacyMonitorOkHttpPlugin") {
            id = "com.hkjustar.monitor.okhttp"
            implementationClass = "MonitorOkHttpPlugin"
        }
    }
}

publishing {
    repositories {
        maven { url = uri(layout.buildDirectory.dir("repo")) }
        val repositoryUrl = providers.gradleProperty("monitorx.repo.url")
            .orElse(providers.environmentVariable("MONITORX_REPO_URL"))
        if (repositoryUrl.isPresent) {
            maven {
                name = "monitorX"
                url = uri(repositoryUrl.get())
                credentials {
                    username = providers.gradleProperty("monitorx.repo.username")
                        .orElse(providers.environmentVariable("MONITORX_REPO_USERNAME")).orNull
                    password = providers.gradleProperty("monitorx.repo.password")
                        .orElse(providers.environmentVariable("MONITORX_REPO_PASSWORD")).orNull
                }
            }
        }
    }
}
