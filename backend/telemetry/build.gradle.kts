plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(libs.opentelemetry.sdk.autoconfigure)
    implementation(libs.opentelemetry.runtime.telemetry)
}

kotlin {
    jvmToolchain(25)
}
