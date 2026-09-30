import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.apollo)
}

// GraphQL の口を叩いて、画面が扱う形に直すところまで。画面は知らない
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
                // UserApi が ApolloClient を受け取るので api にする。
                // Android はここに OkHttp を渡して Cookie を持たせる
                api(libs.apollo.runtime)
                implementation(libs.apollo.normalized.cache)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

// 問い合わせ（src/commonMain/graphql/*.graphql）はこのモジュールが持つ。
// スキーマは :backend:graphql のものをファイルとして読むだけで、依存はしない。
// 写しを持たないので、片方にだけフィールドがある状態にはならない
apollo {
    service("app") {
        packageName.set("net.matsudamper.kmp.activitypub.frontend.graphql")
        schemaFiles.from(
            rootProject.fileTree("backend/graphql/src/main/resources/graphql") { include("*.graphqls") },
        )

        mapScalarToKotlinLong("UnixTime")
        mapScalarToKotlinString("PublicNoteId")
        mapScalarToKotlinLong("AccountId")

        plugin("com.apollographql.cache:normalized-cache-apollo-compiler-plugin:${libs.versions.apollo.cache.get()}")
        pluginArgument("com.apollographql.cache.packageName", packageName.get())
    }
}
