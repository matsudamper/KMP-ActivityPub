plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.paparazzi)
}

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

dependencies {
    implementation(project(":frontend:navigation"))
    implementation(project(":frontend:feature:home"))

    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation(compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.navigation3.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)

    testImplementation(libs.paparazzi)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.composable.preview.scanner.android)
}
