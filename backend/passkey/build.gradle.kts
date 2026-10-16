plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-test-fixtures`
    id("kmp-activitypub.native-image")
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

graalvmNative {
    binaries {
        // 署名検証の鍵の組み立てが native-image で動くかは JVM のテストでは分からない
        named("test") {
            buildArgs.add("--no-fallback")
        }
    }
}
