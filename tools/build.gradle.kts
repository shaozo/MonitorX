plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("maven-publish")
}

group = "com.hkjustar.monitorx"
version = providers.gradleProperty("monitorx.version").getOrElse("0.1.0-SNAPSHOT")

android {
    namespace = "com.hkjustar.monitor.tools"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

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
                artifactId = "monitor-tools"
            }
        }
    }
}

dependencies {
    api(project(":monitorX:runtime-android"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.okhttp)
    implementation(libs.gson)

    val alsVersion = "0.0.1"
    implementation("io.github.lygttpod.android-local-service:core:$alsVersion")
    implementation("io.github.lygttpod.android-local-service:annotation:$alsVersion")
    kapt("io.github.lygttpod.android-local-service:processor:$alsVersion")
}
