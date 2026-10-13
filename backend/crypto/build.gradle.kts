plugins {
    alias(libs.plugins.kotlin.jvm)
    // nativeTest のためだけに入れている。このモジュールは実行可能バイナリを作らない
    id("kmp-activitypub.native-image")
}

dependencies {
    api(libs.spring.security.crypto)
    // Argon2PasswordEncoder が実行時に使う。spring-security-crypto は依存として宣言していない
    runtimeOnly(libs.bouncycastle.bcprov)
    runtimeOnly(libs.spring.core)
    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("passwordHash") {
    group = "application"
    description = "標準入力のパスワードから accounts.password_hash に入れる値を作る"
    mainClass.set("net.matsudamper.kmp.activitypub.crypto.PasswordHashCliKt")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
}

graalvmNative {
    binaries {
        // テストを native バイナリにして実行する。
        // BouncyCastle の Argon2 が native-image 上で動くことは、
        // JVM のテストでは分からないので実バイナリで確かめる
        named("test") {
            buildArgs.add("--no-fallback")
        }
    }
}
