plugins {
    `kotlin-dsl`
}

// Java のツールチェーンは指定しない。ここで作るのはビルド中に Gradle 自身が
// 読み込むプラグインなので、デーモンが動いている JVM で動く必要がある

gradlePlugin {
    plugins {
        create("webpackBundleHash") {
            id = "kmp-activitypub.webpack-bundle-hash"
            implementationClass = "net.matsudamper.kmp.activitypub.gradle.WebpackBundleHashPlugin"
        }
    }
}
