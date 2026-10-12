rootProject.name = "kmp-activitypub"

pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal {
            // Apollo 5.x はポータルに実体が無く Maven Central へ 303 で飛ばされる。
            // このリダイレクトの扱いには環境差があり、CI では解決できなかったのでportalはexcludeする
            content {
                excludeGroup("com.apollographql.apollo")
            }
        }
        mavenCentral()
        google()
    }
}

// バージョンを version catalog に置けないのは、settings.gradle.kts の plugins ブロックが
// 評価される時点で catalog のアクセサがまだ無いため
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

include(":backend")
include(":backend:crypto")
include(":backend:graphql")
include(":backend:repository")
include(":frontend:app-web")
include(":frontend:common-component")
include(":frontend:ui")
include(":frontend:api")
include(":frontend:navigation")
include(":frontend:feature:home")
include(":frontend:app-android")
include(":shared")
