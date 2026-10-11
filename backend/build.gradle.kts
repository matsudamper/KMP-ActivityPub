plugins {
    alias(libs.plugins.kotlin.jvm)
    application
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

// Dockerfile がバージョンを知らずに展開できるよう、配布物の名前から外す
tasks.distTar {
    archiveVersion.set("")
}
