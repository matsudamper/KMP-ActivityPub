import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose)
}

// このアプリの画面を知らない、画面部品と画面の土台
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
                // EventSender / EventHandler が coroutines の型を公開している
                api(libs.kotlinx.coroutines.core)
            }
        }
    }
}
