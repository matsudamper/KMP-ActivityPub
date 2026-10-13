import net.matsudamper.kmp.activitypub.gradle.WebpackBundleHashPlugin
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose)
    id("kmp-activitypub.webpack-bundle-hash")
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = WebpackBundleHashPlugin.BUNDLE_FILE_NAME
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain {
            dependencies {
                implementation(project(":frontend:api"))
                implementation(project(":frontend:navigation"))
                implementation(project(":frontend:feature:home"))
                // material3 の直接座標はまだ alpha
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.ui)
                implementation(compose.material3)
                implementation(libs.navigation3.ui)
            }
        }
    }
}
