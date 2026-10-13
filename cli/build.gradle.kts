plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":backend:crypto"))
    implementation(project(":backend:repository"))
    // jOOQ が接続時に方言の対応状況を INFO で出す
    runtimeOnly(libs.slf4j.nop)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.sqlite.jdbc)
}

kotlin {
    jvmToolchain(25)
}

// sqlite-jdbc がネイティブライブラリを load する。JDK 25 では許可が要る。
// jOOQ は起動時にロゴと tip を標準エラーへ出す
val cliJvmArgs =
    listOf(
        "--enable-native-access=ALL-UNNAMED",
        "-Dorg.jooq.no-logo=true",
        "-Dorg.jooq.no-tips=true",
    )

tasks.test {
    useJUnitPlatform()
    jvmArgs(cliJvmArgs)
    systemProperty(
        "account.schema",
        rootProject.layout.projectDirectory.file("backend/repository/src/main/resources/db/schema.sql").asFile.absolutePath,
    )
}

tasks.register<JavaExec>("addAccount") {
    group = "application"
    description = "標準入力のパスワードで accounts にユーザーを追加する"
    mainClass.set("net.matsudamper.kmp.activitypub.cli.AccountRegistrationCli")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
    workingDir = rootProject.layout.projectDirectory.asFile
    jvmArgs(cliJvmArgs)
}
