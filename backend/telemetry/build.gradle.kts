plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(libs.opentelemetry.api)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.opentelemetry.extension.kotlin)
    // 計装ライブラリが推移的に持ち込む古い incubator は api と揃わず NoClassDefFoundError になる
    implementation(libs.opentelemetry.api.incubator)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.slf4j.api)
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(libs.opentelemetry.sdk.autoconfigure)
    implementation(libs.opentelemetry.runtime.telemetry)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.opentelemetry.sdk.testing)
}

kotlin {
    jvmToolchain(25)
}
