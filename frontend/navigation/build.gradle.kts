import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose)
}

// Web の画面のパスと、画面から遷移を頼む口。どう遷移するかは :frontend が決める
kotlin {
    android {
        namespace = "net.matsudamper.kmp.activitypub.frontend.navigation"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = 23
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(compose.runtime)
                // Screen が NavKey を実装する
                api(libs.navigation3.ui)
            }
        }
    }
}
