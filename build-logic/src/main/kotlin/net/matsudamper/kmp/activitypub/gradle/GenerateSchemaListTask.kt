package net.matsudamper.kmp.activitypub.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * native バイナリではディレクトリの列挙が効かないので、スキーマの一覧をリソースにする
 */
@CacheableTask
abstract class GenerateSchemaListTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val schemaDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val fileNames =
            schemaDirectory
                .get()
                .asFile
                .listFiles()
                .orEmpty()
                .filter { it.isFile && it.extension == SCHEMA_EXTENSION }
                .map { it.name }
                .sorted()

        check(fileNames.isNotEmpty()) {
            "${schemaDirectory.get().asFile} に .$SCHEMA_EXTENSION が 1 つも無い"
        }

        val listFile =
            outputDirectory
                .get()
                .asFile
                .resolve("$SCHEMA_DIRECTORY/$SCHEMA_LIST_NAME")
        listFile.parentFile.mkdirs()
        listFile.writeText(fileNames.joinToString(separator = "\n", postfix = "\n"))
    }

    companion object {
        const val SCHEMA_DIRECTORY = "graphql"
        const val SCHEMA_LIST_NAME = "schema-list.txt"

        private const val SCHEMA_EXTENSION = "graphqls"
    }
}
