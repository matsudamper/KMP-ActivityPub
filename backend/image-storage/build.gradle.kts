plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":shared"))
    implementation(project(":backend:oidc"))
    implementation(libs.aws.s3)
    implementation(libs.aws.sts)
}

kotlin {
    jvmToolchain(25)
}
