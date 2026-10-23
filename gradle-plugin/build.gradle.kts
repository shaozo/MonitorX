plugins {
    `kotlin-dsl`
    id("com.vanniktech.maven.publish") version "0.37.0"
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

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
            id = "io.github.shaozo.monitorx"
            implementationClass = "com.shaovi.monitorx.gradle.MonitorOkHttpPlugin"
        }
    }
}
