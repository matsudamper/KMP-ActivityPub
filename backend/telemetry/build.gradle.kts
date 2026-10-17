plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(libs.opentelemetry.api)
    // 計装ライブラリが推移的に持ち込む古い incubator は api と揃わず NoClassDefFoundError になる
    implementation("io.opentelemetry:opentelemetry-api-incubator:${libs.versions.opentelemetry.get()}-alpha")
    implementation(libs.opentelemetry.sdk)
    implementation(libs.slf4j.api)
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(libs.opentelemetry.sdk.autoconfigure)
    implementation(libs.opentelemetry.runtime.telemetry)
}

kotlin {
    jvmToolchain(25)
}
