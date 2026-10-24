plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.nimbus.jose.jwt)
    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("generateSigningJwk") {
    group = "application"
    description = "OIDC_JWK_PRIVATE に入れる RSA の秘密鍵を JWK で作る"
    mainClass.set("net.matsudamper.kmp.activitypub.oidc.SigningJwkGeneratorCli")
    classpath = sourceSets["main"].runtimeClasspath
}
