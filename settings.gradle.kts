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

// plugins ブロックの評価時点では version catalog が使えない
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
include(":backend:session")
include(":frontend:app-web")
include(":frontend:common-component")
include(":frontend:ui")
include(":frontend:api")
include(":frontend:navigation")
include(":frontend:feature:home")
include(":frontend:app-android")
include(":shared")
