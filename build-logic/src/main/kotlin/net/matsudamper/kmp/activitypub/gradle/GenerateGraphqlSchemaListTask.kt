package net.matsudamper.kmp.activitypub.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * native バイナリではリソースのディレクトリを列挙できないので、ファイル名の一覧をビルド時に作る
 */
@CacheableTask
abstract class GenerateGraphqlSchemaListTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val schemaDirectory: DirectoryProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val fileNames =
            schemaDirectory
                .get()
                .asFile
                .listFiles()
                .orEmpty()
                .filter { it.extension == "graphqls" }
                .map { it.name }
                .sorted()
        outputFile.get().asFile.writeText(fileNames.joinToString("\n"))
    }
}
