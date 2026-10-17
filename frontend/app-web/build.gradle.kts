import net.matsudamper.kmp.activitypub.gradle.WebpackBundleHashPlugin
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.koin.compiler)
    id("kmp-activitypub.webpack-bundle-hash")
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = WebpackBundleHashPlugin.BUNDLE_FILE_NAME
                // 既定は 8080 で backend と衝突するのでずらす
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).copy(port = 8081)
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain {
            dependencies {
                implementation(project(":frontend:ui"))
                implementation(project(":frontend:api"))
                implementation(project(":frontend:navigation"))
                implementation(project(":frontend:feature:home"))
                implementation(project(":frontend:feature:admin"))
                // material3 の直接座標はまだ alpha
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.ui)
                implementation(compose.material3)
                // runtime は推移的に androidx.navigation3 から入る
                implementation(libs.navigation3.ui)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.js)
                // document 等のブラウザ API。Kotlin/Wasm では stdlib から分離されている
                implementation(libs.kotlinx.browser)
                implementation(libs.androidx.lifecycle.viewmodel.navigation3)
                implementation(libs.androidx.lifecycle.viewmodel.compose)
                implementation(libs.kotlinx.browser)
                implementation(libs.koin.core)
                implementation(libs.koin.annotations)
                implementation(libs.koin.compose)
                implementation(libs.koin.compose.viewmodel)
            }
        }
    }
}
