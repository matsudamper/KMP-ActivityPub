plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":backend:crypto"))
    implementation(libs.sqlite.jdbc)
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
    mainClass.set("net.matsudamper.kmp.activitypub.cli.AccountRegistrationCli")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
    workingDir = rootProject.layout.projectDirectory.asFile
    // sqlite-jdbc がネイティブライブラリを load する。JDK 25 では許可が要る
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
