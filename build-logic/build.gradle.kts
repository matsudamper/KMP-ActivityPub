import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
}

// Java のツールチェーンは指定しない。ここで作るのはビルド中に Gradle 自身が
// 読み込むプラグインなので、デーモンが動いている JVM で動く必要がある。
// 25 を指定すると、それより古い JVM でデーモンが動いている環境で読めなくなる

configure<KtlintExtension> {
    // 本体と同じバージョンで揃える。別々に動くビルドなので個別に設定が要る
    version.set(libs.versions.ktlint.get())
}

dependencies {
    implementation(libs.graalvm.native.plugin)

    implementation(libs.jooq.codegen.gradle)
    implementation(libs.jooq.codegen)
}

gradlePlugin {
    plugins {
        create("databaseCodegen") {
            id = "kmp-activitypub.database-codegen"
            implementationClass = "net.matsudamper.kmp.activitypub.gradle.DatabaseCodegenPlugin"
        }

        create("graphQlSchemaList") {
            id = "kmp-activitypub.graphql-schema-list"
            implementationClass = "net.matsudamper.kmp.activitypub.gradle.GraphQlSchemaListPlugin"
        }

        create("webpackBundleHash") {
            id = "kmp-activitypub.webpack-bundle-hash"
            implementationClass = "net.matsudamper.kmp.activitypub.gradle.WebpackBundleHashPlugin"
        }

        create("nativeImage") {
            id = "kmp-activitypub.native-image"
            implementationClass = "net.matsudamper.kmp.activitypub.gradle.NativeImagePlugin"
        }
    }
}
