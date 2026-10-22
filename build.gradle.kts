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
