plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":shared"))

    implementation(libs.lettuce.core)
}

kotlin {
    jvmToolchain(25)
}
