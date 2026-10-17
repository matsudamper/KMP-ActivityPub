package net.matsudamper.kmp.activitypub.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

/**
 * `src/main/resources/graphql` の `.graphqls` の一覧を `graphql/schema-list.txt` としてリソースに入れる
 */
class GraphqlSchemaListPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val generatedResourcesDirectory = target.layout.buildDirectory.dir("generated/graphql-schema-list")
        val generateSchemaList =
            target.tasks.register<GenerateGraphqlSchemaListTask>("generateGraphqlSchemaList") {
                schemaDirectory.set(target.layout.projectDirectory.dir("src/main/resources/graphql"))
                outputFile.set(generatedResourcesDirectory.map { it.file("graphql/schema-list.txt") })
            }

        target.pluginManager.withPlugin("java") {
            target.extensions
                .getByType<JavaPluginExtension>()
                .sourceSets
                .named("main") {
                    resources.srcDir(generateSchemaList.map { generatedResourcesDirectory })
                }
        }
    }
}
