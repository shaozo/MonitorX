plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.vanniktech.maven.publish")
}

android {
    namespace = "com.shaovi.monitorx.tools"
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
