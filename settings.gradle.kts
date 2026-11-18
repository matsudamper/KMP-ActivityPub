import com.github.burrunan.s3cache.AwsS3BuildCache

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
        google()
        mavenCentral()
    }
}

// バージョンを version catalog に置けないのは、settings.gradle.kts の plugins ブロックが
// 評価される時点で catalog のアクセサがまだ無いため
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.github.burrunan.s3-build-cache") version "1.9.11"
}

val remoteBuildCacheAccessKeyId: String? = System.getenv("S3_BUILD_CACHE_ACCESS_KEY_ID")
buildCache {
    remote<AwsS3BuildCache> {
        isEnabled = !remoteBuildCacheAccessKeyId.isNullOrBlank()
        isPush = System.getenv("CI") == "true"
        endpoint = System.getenv("S3_BUILD_CACHE_ENDPOINT").orEmpty().ifBlank { "s3.matsudamper.net" }
        forcePathStyle = true
        region = "us-east-1"
        bucket = "gradle-remote-cache"
        prefix = "matsudamper/KMP-ActivityPub/"
        // 自前の S3 互換サーバーなので AWS 固有のストレージクラスを指定しない
        isReducedRedundancy = false
    }
}

dependencyResolutionManagement {
    repositories {
        // kotpub の正式版は Maven Central に出ないことがあるので、ここからだけ取得する
        exclusiveContent {
            forRepository {
                maven { url = uri("https://maven.matsudamper.net/releases") }
            }
            filter {
                includeGroup("net.matsudamper.kotpub")
            }
        }
        google()
        mavenCentral()
    }
}

include(":backend")
include(":backend:graphql")
include(":backend:crypto")
include(":backend:delivery")
include(":backend:repository")
include(":backend:session")
include(":backend:passkey")
include(":backend:telemetry")
include(":backend:oidc")
include(":backend:image-storage")
include(":cli")
include(":frontend:app-web")
include(":frontend:common-component")
include(":frontend:ui")
include(":frontend:api")
include(":frontend:navigation")
include(":frontend:core")
include(":frontend:feature:home")
include(":frontend:app-android")
include(":shared")
