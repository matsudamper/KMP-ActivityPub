plugins {
    alias(libs.plugins.kotlin.jvm)
    id("kmp-activitypub.database-codegen")
}

dependencies {
    implementation(libs.sqlite.jdbc)
    implementation(libs.jooq)

    jooqCodegen(libs.sqlite.jdbc)
}

databaseCodegen {
    packageName.set("net.matsudamper.kmp.activitypub.repository.jooq")
    nativeImageReflectConfigOutputPath.set("net.matsudamper/kmp-activitypub-repository-jooq")
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}
