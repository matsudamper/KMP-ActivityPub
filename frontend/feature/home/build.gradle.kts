import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose)
}

// Android アプリからも使う。管理画面は :frontend:feature:admin に分けてあるので入らない
kotlin {
    android {
        namespace = "net.matsudamper.kmp.activitypub.frontend.feature.home"
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
                implementation(project(":frontend:ui"))
                implementation(project(":frontend:api"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.compose.ui.tooling)
            }
        }
    }
}
