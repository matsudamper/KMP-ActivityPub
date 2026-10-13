import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
}

// Java のツールチェーンは指定しない。ここで作るのはビルド中に Gradle 自身が
// 読み込むプラグインなので、デーモンが動いている JVM で動く必要がある

configure<KtlintExtension> {
    // 本体と同じバージョンで揃える。別々に動くビルドなので個別に設定が要る
    version.set(libs.versions.ktlint.get())
}

dependencies {
    implementation(libs.graalvm.native.plugin)
}

gradlePlugin {
    plugins {
        create("webpackBundleHash") {
            id = "kmp-activitypub.webpack-bundle-hash"
            implementationClass = "net.matsudamper.kmp.activitypub.gradle.WebpackBundleHashPlugin"
        }

        create("graphQlSchemaList") {
            id = "kmp-activitypub.graphql-schema-list"
            implementationClass = "net.matsudamper.kmp.activitypub.gradle.GraphQlSchemaListPlugin"
        }

        create("nativeImage") {
            id = "kmp-activitypub.native-image"
            implementationClass = "net.matsudamper.kmp.activitypub.gradle.NativeImagePlugin"
        }
    }
}
