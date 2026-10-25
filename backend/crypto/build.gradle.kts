plugins {
    alias(libs.plugins.kotlin.jvm)
    id("kmp-activitypub.native-image")
}

dependencies {
    api(libs.spring.security.crypto)
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
    mainClass.set("net.matsudamper.kmp.activitypub.crypto.PasswordHashCli")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
}

tasks.register<JavaExec>("adminPasswordHash") {
    group = "application"
    description = "標準入力のパスワードから ADMIN_PASSWORD_HASH に入れる値を作る"
    mainClass.set("net.matsudamper.kmp.activitypub.crypto.AdminPasswordHashCli")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
}

graalvmNative {
    binaries {
        // Argon2 が native-image で動くかは JVM のテストでは分からない
        named("test") {
            buildArgs.add("--no-fallback")
        }
    }
}
