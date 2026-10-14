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
    implementation(project(":shared"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.kotlinx.serialization.json)

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
        }
    }
}
