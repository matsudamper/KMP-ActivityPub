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
    implementation(project(":backend:session"))
    implementation(project(":backend:telemetry"))
    implementation(project(":shared"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.opentelemetry.ktor3)
    implementation(libs.opentelemetry.extension.kotlin)
    implementation(libs.opentelemetry.graphql.java)

    compileOnly(libs.graalvm.nativeimage)

    runtimeOnly(libs.slf4j.simple)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test)
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
        }
    }
}
