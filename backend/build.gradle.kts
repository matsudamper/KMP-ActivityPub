plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
    id("kmp-activitypub.native-image")
}

dependencies {
    implementation(project(":backend:graphql"))
    implementation(project(":backend:repository"))
    implementation(project(":backend:crypto"))
    implementation(project(":backend:delivery"))
    implementation(project(":backend:session"))
    implementation(project(":backend:passkey"))
    implementation(project(":backend:telemetry"))
    implementation(project(":backend:oidc"))
    implementation(project(":backend:image-storage"))
    implementation(project(":shared"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.status.pages)
    implementation(kotlin("reflect"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotpub.core)
    implementation(libs.okhttp)
    implementation(libs.opentelemetry.ktor3)
    implementation(libs.opentelemetry.extension.kotlin)
    implementation(libs.opentelemetry.graphql.java)

    compileOnly(libs.graalvm.nativeimage)

    runtimeOnly(libs.slf4j.simple)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.nimbus.jose.jwt)
    testImplementation(testFixtures(project(":backend:passkey")))
}

application {
    mainClass.set("net.matsudamper.kmp.activitypub.MainKt")
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

graalvmNative {
    // ライブラリ向けの設定
    metadataRepository {
        enabled.set(false)
    }

    binaries {
        named("main") {
            imageName.set("kmp-activitypub")
            mainClass.set("net.matsudamper.kmp.activitypub.MainKt")
            buildArgs.add("--no-fallback")
            buildArgs.add("--features=net.matsudamper.kmp.activitypub.graalvm.GraphQlReflectionFeature")
            buildArgs.add("--features=net.matsudamper.kmp.activitypub.passkey.graalvm.WebAuthn4jReflectionFeature")
            // native-image が解析中に @Deprecated の既定値を読み、ビルド時初期化で落ちる
            buildArgs.add("--initialize-at-build-time=kotlin.DeprecationLevel")
            // jOOQ の組み込み型登録が Class.arrayType() を使い、native-image では欠けた配列型で NPE になる
            buildArgs.add("--initialize-at-build-time=org.jooq")
            // jOOQ 初期化で SimpleLogger がイメージヒープに載る。slf4j-simple は書き込みごとに System.err を引き直す
            buildArgs.add("--initialize-at-build-time=org.slf4j")
            // kickstart 経由の Jackson ObjectMapper がビルド時初期化だとイメージヒープに載って失敗する（JSON 型は未使用）
            buildArgs.add("--initialize-at-run-time=org.jooq.impl.Convert${'$'}_JSON")
            // ロゴと tip の出力がイメージ生成中の JVM で走る
            buildArgs.add("-Dorg.jooq.no-logo=true")
            buildArgs.add("-Dorg.jooq.no-tips=true")
            buildArgs.add("--initialize-at-run-time=io.opentelemetry.sdk")
            buildArgs.add("--initialize-at-run-time=io.opentelemetry.exporter")
            buildArgs.add("--initialize-at-run-time=io.opentelemetry.instrumentation")
            // native-image は既定で Arena.ofShared を使えない。Lettuce が使う Netty は Java 25 で直接バッファの解放にこれを使うため、
            // 無効のままだと終了時にイベントループのスレッドが UnsupportedFeatureError で落ち、Lettuce の shutdown が返らずプロセスが終わらない
            buildArgs.add("-H:+SharedArenaSupport")
            // AWS SDK 経由の netty-codec-http(2) 4.1 が io.netty 全体をビルド時初期化にする。Netty 4.2 で基準時刻が SystemTicker に移ったが
            // netty-common の設定は旧来の ScheduledFutureTask しか実行時初期化にしておらず、ビルドしたマシンの nanoTime が焼き込まれる。
            // 実行するホストの稼働時間がそれより短いと、DNS の問い合わせなどのタイムアウトが即座に発火する
            buildArgs.add("--initialize-at-run-time=io.netty.util.concurrent.SystemTicker")
        }
    }
}
