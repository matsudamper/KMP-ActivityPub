plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":shared"))
    implementation(project(":backend:oidc"))

    // 同期クライアントしか使わないので、Apache と Netty の HTTP クライアントは入れず JDK の HttpURLConnection で送る
    implementation(libs.aws.s3) {
        exclude(group = "software.amazon.awssdk", module = "apache-client")
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
    }
    implementation(libs.aws.sts) {
        exclude(group = "software.amazon.awssdk", module = "apache-client")
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
    }
    implementation(libs.aws.url.connection.client)
}

kotlin {
    jvmToolchain(25)
}
