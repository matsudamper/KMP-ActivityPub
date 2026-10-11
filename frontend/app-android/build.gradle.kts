plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose)
}

// Android アプリ。画面は :frontend:feature:home を使い、ここには Android にしか無いもの
// （接続先のサーバーの入力、Cookie の保存）だけを置く。管理画面は入れない
android {
    namespace = "net.matsudamper.kmp.activitypub.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "net.matsudamper.kmp.activitypub"
        minSdk = 23
        targetSdk = libs.versions.androidCompileSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":frontend:ui"))
    implementation(project(":frontend:api"))
    implementation(project(":frontend:feature:home"))

    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.core)
    // Apollo に渡す OkHttp に、ログインの Cookie を残す CookieJar を付ける
    implementation(libs.okhttp)
    implementation(libs.androidx.core.ktx)

    debugImplementation(libs.compose.ui.tooling)
}
