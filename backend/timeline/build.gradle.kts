plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":backend:repository"))

    implementation(libs.lettuce.core)
    implementation(project(":backend:telemetry"))
    implementation(libs.opentelemetry.lettuce)

    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}
