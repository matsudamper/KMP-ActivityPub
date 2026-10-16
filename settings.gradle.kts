rootProject.name = "kmp-activitypub"

pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal {
            // Apollo 5.x はポータルから Maven Central へのリダイレクトが CI で解決できない
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
include(":backend:graphql")
include(":backend:crypto")
include(":backend:repository")
include(":backend:session")
include(":cli")
include(":frontend:app-web")
include(":frontend:common-component")
include(":frontend:ui")
include(":frontend:api")
include(":frontend:navigation")
include(":frontend:feature:home")
include(":frontend:feature:admin")
include(":frontend:app-android")
include(":shared")
