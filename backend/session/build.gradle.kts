plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":shared"))
}

kotlin {
    jvmToolchain(25)
}
