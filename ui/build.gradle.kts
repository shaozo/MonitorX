plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

group = "com.hkjustar.monitorx"
version = providers.gradleProperty("monitorx.version").getOrElse("0.1.0-SNAPSHOT")

android {
    namespace = "com.hkjustar.monitor.okhttp"
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
    buildFeatures { viewBinding = true }
    publishing { singleVariant("release") { withSourcesJar() } }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "monitor-ui"
            }
        }
    }
}

dependencies {
    api(project(":monitorX:tools"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.lifecycle.runtimeKtx)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.gson)
    implementation("com.github.lygttpod:ShapeView:1.0.2")
}
