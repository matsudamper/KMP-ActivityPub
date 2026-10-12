plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    id("kmp-activitypub.native-image")
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)

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
    binaries {
        named("main") {
            imageName.set("kmp-activitypub")
            mainClass.set("net.matsudamper.kmp.activitypub.MainKt")
            buildArgs.add("--no-fallback")
        }
    }
}
