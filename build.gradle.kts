plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.compose) apply false
}

allprojects {
    group = "net.matsudamper"
    version = "0.1.0"
}

tasks.register("compileAll") {
    group = "build"
    description = "frontend・backendを含む全モジュールのプロダクションコードおよびテストコードをコンパイルする"
    dependsOn(
        allprojects.map { project ->
            project.tasks.matching { task ->
                task.name.startsWith("compileKotlin") ||
                    task.name.startsWith("compileTestKotlin") ||
                    task.name == "compileAndroidMain" ||
                    task.name == "compileDebugKotlin" ||
                    task.name in listOf("compileJava", "compileTestJava")
            }
        },
    )
}
