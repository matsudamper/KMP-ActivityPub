import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.paparazzi)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.koin.compiler)
}

val localProperties = Properties().also { properties ->
    val propertiesFile = rootDir.resolve("local.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use(properties::load)
    }
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

    signingConfigs {
        val debugKeystorePath = System.getenv("DEBUG_KEYSTORE_PATH") ?: localProperties["DEBUG_KEYSTORE_PATH"] as? String
        if (debugKeystorePath != null) {
            create("sharedDebug") {
                storeFile = file(debugKeystorePath)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        named("debug") {
            signingConfig = signingConfigs.findByName("sharedDebug") ?: signingConfigs.getByName("debug")
        }
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
    implementation(project(":frontend:api"))
    implementation(project(":frontend:navigation"))
    implementation(project(":frontend:feature:home"))

    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation(compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.datastore)
    implementation(libs.protobuf.javalite)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.play.services.auth.blockstore)
    implementation(libs.koin.android)
    implementation(libs.koin.annotations)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)

    testImplementation(libs.paparazzi)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.composable.preview.scanner.android)
}
