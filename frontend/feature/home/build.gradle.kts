import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.koin.compiler)
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
                implementation(project(":frontend:api"))
                implementation(project(":frontend:navigation"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.androidx.lifecycle.viewmodel.compose)
                implementation(libs.androidx.lifecycle.viewmodel.navigation3)
                implementation(libs.koin.core)
                implementation(libs.koin.annotations)
                implementation(libs.koin.compose.viewmodel)
                implementation(libs.coil.compose)
            }
        }

        wasmJsMain {
            dependencies {
                implementation(project(":frontend:common-component"))
                implementation(libs.kotlinx.browser)
                implementation(libs.coil.network.ktor3)
                implementation(libs.ktor.client.js)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.androidx.compose.ui.tooling.preview)
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.exifinterface)
                implementation(libs.coil.network.okhttp)
            }
        }
    }
}
