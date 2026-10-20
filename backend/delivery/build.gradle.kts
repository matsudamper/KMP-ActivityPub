plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":backend:repository"))
    api(libs.kotpub.core)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)

    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}
