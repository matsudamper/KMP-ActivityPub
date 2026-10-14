package net.matsudamper.kmp.activitypub.gradle

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * jOOQ は `Record` をリフレクションで作るので、native-image 向けに登録する。
 * テーブルを足したときの更新忘れを防ぐため、生成物から作る
 */
@CacheableTask
abstract class GenerateJooqReflectConfigTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedSources: DirectoryProperty

    /**
     * `META-INF/native-image/<path>/reflect-config.json` の `<path>`
     */
    @get:Input
    abstract val nativeImageReflectConfigOutputPath: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val sourceRoot = generatedSources.get().asFile

        val recordClasses =
            sourceRoot
                .walkTopDown()
                .filter { it.isFile && it.name.endsWith("Record.java") }
                .map { file ->
                    file
                        .relativeTo(sourceRoot)
                        .path
                        .removeSuffix(".java")
                        .replace(File.separatorChar, '.')
                }.sorted()
                .toList()

        check(recordClasses.isNotEmpty()) {
            "jOOQ の生成物に Record が 1 つも無い。codegen が空振りしている可能性がある"
        }

        val entries =
            recordClasses.joinToString(separator = ",\n") { className ->
                """  { "name": "$className", "allDeclaredConstructors": true }"""
            }

        val configFile =
            outputDirectory
                .get()
                .asFile
                .resolve(
                    "META-INF/native-image/${nativeImageReflectConfigOutputPath.get()}/reflect-config.json",
                )
        configFile.parentFile.mkdirs()
        configFile.writeText("[\n$entries\n]\n")
    }
}
