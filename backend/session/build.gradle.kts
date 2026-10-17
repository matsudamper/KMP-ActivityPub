plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":shared"))

    implementation(libs.lettuce.core)
    api(libs.opentelemetry.api)
    implementation(libs.opentelemetry.lettuce)
}

kotlin {
    jvmToolchain(25)
}
