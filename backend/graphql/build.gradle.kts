import com.kobylynskyi.graphql.codegen.model.ApiInterfaceStrategy
import com.kobylynskyi.graphql.codegen.model.GeneratedLanguage
import io.github.kobylynskyi.graphql.codegen.gradle.GraphQLCodegenGradleTask

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.graphql.codegen)
    id("kmp-activitypub.graphql-schema-list")
}

dependencies {
    api(libs.graphql.kickstart.tools)
    api(libs.graphql.java)
    implementation(project(":shared"))
}

val generatedSourcesDirectory = layout.buildDirectory.dir("generated/codegen")

kotlin {
    jvmToolchain(25)

    sourceSets.named("main") {
        kotlin.srcDir(generatedSourcesDirectory)
    }
}

tasks.named<GraphQLCodegenGradleTask>("graphqlCodegen") {
    graphqlSchemaPaths =
        layout.projectDirectory
            .dir("src/main/resources/graphql")
            .asFile
            .listFiles()
            .orEmpty()
            .filter { it.extension == "graphqls" }
            .map { it.toString() }
            .sorted()

    generatedLanguage = GeneratedLanguage.KOTLIN
    outputDir = generatedSourcesDirectory.get().asFile
    packageName = "net.matsudamper.kmp.activitypub.graphql.model"

    customTypesMapping = mutableMapOf(
        "UnixTime" to "kotlin.Long",
        "PublicNoteId" to "net.matsudamper.kmp.activitypub.shared.PublicNoteId",
        "AccountId" to "net.matsudamper.kmp.activitypub.shared.AccountId",
    )

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
