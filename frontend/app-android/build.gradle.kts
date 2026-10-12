plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.paparazzi)
    alias(libs.plugins.protobuf)
}

// Android アプリ。画面は :frontend:feature:home を使い、ここには Android にしか無いもの
// （接続先のサーバーの入力、Cookie の保存）だけを置く。管理画面は入れない
android {
    namespace = "net.matsudamper.kmp.activitypub.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "net.matsudamper.kmp.activitypub"
        minSdk = 23
        targetSdk = libs.versions.androidCompileSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        val isPaparazziRequested =
            gradle.startParameter.taskNames.any { taskName ->
                taskName.contains("Paparazzi", ignoreCase = true)
            }
        unitTests.all { test ->
            test.useJUnit {
                if (isPaparazziRequested) {
                    includeCategories("net.matsudamper.kmp.activitypub.android.PaparazziTestCategory")
                } else {
                    excludeCategories("net.matsudamper.kmp.activitypub.android.PaparazziTestCategory")
                }
            }
        }
    }
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":frontend:ui"))
    implementation(project(":frontend:api"))
    implementation(project(":frontend:navigation"))
    implementation(project(":frontend:feature:home"))

    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.androidx.core.ktx)

    implementation(libs.navigation3.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.datastore)
    implementation(libs.protobuf.javalite)
    implementation(libs.kotlinx.coroutines.core)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.paparazzi)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.composable.preview.scanner.android)
}
