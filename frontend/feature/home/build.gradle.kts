import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose)
}

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
                implementation(project(":frontend:navigation"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.androidx.lifecycle.viewmodel.compose)
            }
        }

        wasmJsMain {
            dependencies {
                implementation(project(":frontend:common-component"))
            }
        }

        androidMain {
            dependencies {
                implementation(libs.androidx.compose.ui.tooling.preview)
            }
        }
    }
}
