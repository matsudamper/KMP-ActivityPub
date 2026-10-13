package net.matsudamper.kmp.activitypub.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * codegen の入力の SQLite を作る。
 *
 * jOOQ の DDLDatabase は自前のパーサで、SQLite の型親和性と一致する保証が無い
 */
abstract class BuildSchemaDatabaseTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val schemaSql: RegularFileProperty

    @get:Classpath
    abstract val driverClasspath: ConfigurableFileCollection

    @get:OutputFile
    abstract val databaseFile: RegularFileProperty

    @TaskAction
    fun build() {
        val target = databaseFile.get().asFile
        target.parentFile.mkdirs()
        target.delete()

        withSqliteConnection(driverClasspath.files, target) { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(schemaSql.get().asFile.readText())
            }
        }
    }
}
