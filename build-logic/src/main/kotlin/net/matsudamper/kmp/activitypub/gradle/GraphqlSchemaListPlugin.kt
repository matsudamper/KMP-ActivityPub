package net.matsudamper.kmp.activitypub.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

class GraphqlSchemaListPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.tasks.register<GenerateGraphqlSchemaListTask>(TASK_NAME)
    }

    companion object {
        const val TASK_NAME = "generateGraphqlSchemaList"
    }
}
