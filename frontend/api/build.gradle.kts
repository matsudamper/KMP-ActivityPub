import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.apollo)
}

kotlin {
    android {
        namespace = "net.matsudamper.kmp.activitypub.frontend.api"
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
                implementation(project(":shared"))
                // Android が OkHttp を渡せるように api にする
                api(libs.apollo.runtime)
            }
        }
    }
}

// スキーマは :backend:graphql のファイルを直接読む
apollo {
    service("app") {
        packageName.set("net.matsudamper.kmp.activitypub.frontend.graphql")
        schemaFiles.from(
            rootProject.fileTree("backend/graphql/src/main/resources/graphql") { include("*.graphqls") },
        )
    }
}
