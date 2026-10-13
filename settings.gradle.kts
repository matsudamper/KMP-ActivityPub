rootProject.name = "kmp-activitypub"

pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
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
include(":backend:repository")
include(":cli")
include(":frontend:app-web")
include(":frontend:common-component")
include(":frontend:ui")
include(":frontend:navigation")
include(":frontend:feature:home")
include(":frontend:app-android")
