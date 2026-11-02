plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":backend:repository"))
    api(libs.kotpub.core)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)
    implementation(project(":backend:telemetry"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.opentelemetry.sdk)
    testImplementation(libs.opentelemetry.sdk.testing)
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}
