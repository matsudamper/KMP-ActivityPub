plugins {
    alias(libs.plugins.kotlin.jvm)
    id("kmp-activitypub.native-image")
}

dependencies {
    api(libs.spring.security.crypto)
    implementation(libs.sqlite.jdbc)
    runtimeOnly(libs.bouncycastle.bcprov)
    runtimeOnly(libs.spring.core)
    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
    // sqlite-jdbc がネイティブライブラリを load する。JDK 25 では許可が要る
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty(
        "account.schema",
        rootProject.layout.projectDirectory.file("backend/repository/src/main/resources/db/schema.sql").asFile.absolutePath,
    )
}

tasks.register<JavaExec>("addAccount") {
    group = "application"
    description = "標準入力のパスワードで accounts にユーザーを追加する"
    mainClass.set("net.matsudamper.kmp.activitypub.crypto.AccountRegistrationCli")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
    workingDir = rootProject.layout.projectDirectory.asFile
    // sqlite-jdbc がネイティブライブラリを load する。JDK 25 では許可が要る
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.register<JavaExec>("passwordHash") {
    group = "application"
    description = "標準入力のパスワードから accounts.password_hash に入れる値を作る"
    mainClass.set("net.matsudamper.kmp.activitypub.crypto.PasswordHashCli")
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
