plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

group = "com.hkjustar.monitorx"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":monitorX:core"))
    api(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit4)
}

tasks.test { useJUnit() }

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["java"])
            artifactId = "monitor-source-okhttp"
        }
    }
}
