import com.kobylynskyi.graphql.codegen.model.ApiInterfaceStrategy
import com.kobylynskyi.graphql.codegen.model.GeneratedLanguage
import io.github.kobylynskyi.graphql.codegen.gradle.GraphQLCodegenGradleTask

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.graphql.codegen)
}

dependencies {
    api(libs.graphql.kickstart.tools)
    api(libs.graphql.java)
}

val generatedSourcesDirectory = layout.buildDirectory.dir("generated/codegen")
val generatedResourcesDirectory = layout.buildDirectory.dir("generated/resources")
val schemaDirectory = layout.projectDirectory.dir("src/main/resources/graphql")

abstract class GenerateSchemaListTask : DefaultTask() {
    @get:InputDirectory
    abstract val schemaDir: DirectoryProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val fileNames =
            schemaDir
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

// native バイナリではリソースのディレクトリを列挙できないので、ファイル名の一覧をビルド時に作る
val generateSchemaList =
    tasks.register<GenerateSchemaListTask>("generateSchemaList") {
        schemaDir.set(schemaDirectory)
        outputFile.set(generatedResourcesDirectory.map { it.file("graphql/schema-list.txt") })
    }

kotlin {
    jvmToolchain(25)

    sourceSets.named("main") {
        kotlin.srcDir(generatedSourcesDirectory)
        resources.srcDir(generateSchemaList.map { generatedResourcesDirectory })
    }
}

tasks.named<GraphQLCodegenGradleTask>("graphqlCodegen") {
    graphqlSchemaPaths =
        schemaDirectory.asFile
            .listFiles()
            .orEmpty()
            .filter { it.extension == "graphqls" }
            .map { it.toString() }
            .sorted()

    generatedLanguage = GeneratedLanguage.KOTLIN
    outputDir = generatedSourcesDirectory.get().asFile
    packageName = "net.matsudamper.kmp.activitypub.graphql.model"

    modelNamePrefix = "Ql"
    generateImmutableModels = true
    generateBuilder = false

    // 生成時刻が入るとビルドキャッシュが効かない
    addGeneratedAnnotation = false

    // 付けたフィールドだけリゾルバのインタフェースを作る
    fieldsWithResolvers = setOf("@lazy")
    generateParameterizedFieldsResolvers = true

    // API インタフェースを出すとリゾルバのファイルが二重に作られて落ちる
    apiInterfaceStrategy = ApiInterfaceStrategy.DO_NOT_GENERATE

    apiReturnType = "java.util.concurrent.CompletionStage<graphql.execution.DataFetcherResult<{{TYPE}}>>"
    generateApisWithThrowsException = false
    generateDataFetchingEnvironmentArgumentInApis = true

    parentInterfaces {
        resolver = "graphql.kickstart.tools.GraphQLResolver<{{TYPE}}>"
        queryResolver = "graphql.kickstart.tools.GraphQLQueryResolver"
        mutationResolver = "graphql.kickstart.tools.GraphQLMutationResolver"
        subscriptionResolver = "graphql.kickstart.tools.GraphQLSubscriptionResolver"
    }
}

tasks.named("compileKotlin") {
    dependsOn("graphqlCodegen")
}

// ktlint は生成より後に走らせないと構成の誤りで落ちる
tasks.matching { it.name.startsWith("runKtlint") }.configureEach {
    dependsOn("graphqlCodegen")
}
