plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

group = "com.shaovi.monitorx"
version = providers.gradleProperty("monitorx.version").getOrElse("0.1.0-SNAPSHOT")

android {
    namespace = "com.shaovi.monitorx.runtime"
    compileSdk = 36
    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = JavaVersion.VERSION_11.toString() }
    publishing { singleVariant("release") { withSourcesJar() } }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "monitor-runtime-android"
            }
        }
    }
}

dependencies {
    api(project(":monitorX:okhttp"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.startup.runtime)
}
