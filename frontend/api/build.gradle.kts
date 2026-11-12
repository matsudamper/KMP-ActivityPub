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
        minSdk = 29
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain {
            dependencies {
                // Android が ApolloClient を渡せるように api にする
                api(libs.apollo.runtime)
                api(libs.apollo.normalized.cache)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

apollo {
    service("app") {
        packageName.set("net.matsudamper.kmp.activitypub.frontend.graphql")
        schemaFiles.from(
            rootProject.fileTree("backend/graphql/src/main/resources/graphql") { include("*.graphqls") },
        )
        mapScalarToKotlinString("UserId")
        mapScalarToKotlinString("PasskeyCredentialId")
        mapScalarToKotlinString("NoteImageId")
        mapScalarToKotlinString("PublicNoteId")
        mapScalarToKotlinString("FollowingGroupId")
        mapScalarToKotlinString("TimelinePostId")
        mapScalarToUpload("Upload")
        plugin(libs.apollo.normalized.cache.compiler.plugin)
        pluginArgument("com.apollographql.cache.packageName", packageName.get())
    }
}
