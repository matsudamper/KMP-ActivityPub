plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.compose) apply false
}

allprojects {
    group = "net.matsudamper"
    version = "0.1.0"
}
