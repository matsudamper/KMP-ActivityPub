import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose)
}

// このアプリの画面や API を知らない、画面部品と画面の土台。
// 部品のうち、別の Compose Web アプリからも使えるものは :frontend:common-component に置く
kotlin {
    android {
        namespace = "net.matsudamper.kmp.activitypub.frontend.ui"
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
                api(compose.runtime)
                api(compose.foundation)
                api(compose.ui)
                api(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
            }
        }

        androidMain {
            dependencies {
                // 他のモジュールのプレビューが PreviewsMultiSize を付けるので api にする
                api(libs.compose.ui.tooling.preview)
            }
        }

        wasmJsMain {
            dependencies {
                implementation(project(":frontend:common-component"))
            }
        }
    }
}
