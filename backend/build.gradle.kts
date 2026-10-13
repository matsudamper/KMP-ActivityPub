plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
    id("kmp-activitypub.native-image")
}

dependencies {
    implementation(project(":backend:repository"))

    implementation(project(":backend:crypto"))

    implementation(project(":backend:session"))

    implementation(project(":backend:graphql"))
    implementation(project(":shared"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)

    implementation(libs.kotlinx.serialization.json)

    compileOnly(libs.graalvm.nativeimage)

    // logback は native-image で設定ファイルの読み込みに追加対応が要るため、
    // 標準エラーに出すだけの slf4j-simple にする
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

            buildArgs.add("--initialize-at-build-time=kotlin.DeprecationLevel")

            buildArgs.add("--initialize-at-build-time=org.jooq")

            buildArgs.add("--initialize-at-build-time=org.slf4j")

            buildArgs.add("--initialize-at-run-time=org.jooq.impl.Convert${'$'}_JSON")

            buildArgs.add("-Dorg.jooq.no-logo=true")
            buildArgs.add("-Dorg.jooq.no-tips=true")
        }
    }
}
