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
                val isSourceCompile =
                    task.name.startsWith("compile") &&
                        (task.name.contains("Kotlin") || task.name.endsWith("Java"))
                // Wasm の実行バイナリを作るリンクと最適化は重く、コンパイルの確認には要らない
                val isWasmExecutableLink = task.name.contains("Executable")
                (isSourceCompile && !isWasmExecutableLink) || task.name == "compileAndroidMain"
            }
        },
    )
}
