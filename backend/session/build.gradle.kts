plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":shared"))

    implementation(libs.lettuce.core)
    implementation(project(":backend:telemetry"))
    implementation(libs.opentelemetry.lettuce)
}

kotlin {
    jvmToolchain(25)
}
