plugins {
    `kotlin-dsl`
}

// Java のツールチェーンは指定しない。ここで作るのはビルド中に Gradle 自身が
// 読み込むプラグインなので、デーモンが動いている JVM で動く必要がある

dependencies {
    // native-image の設定（どの GraalVM で作るか）をプラグイン側で書くために要る
    implementation(libs.graalvm.native.plugin)
}

gradlePlugin {
    plugins {
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
