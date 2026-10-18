import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
}

// デーモンの JVM で読み込むので、ツールチェーンはデーモンに合わせる

configure<KtlintExtension> {
    version.set(libs.versions.ktlint.get())
}

dependencies {
    implementation(libs.graalvm.native.plugin)
    implementation(libs.jooq.codegen.gradle)
    implementation(libs.jooq.codegen)
}

gradlePlugin {
    plugins {
        create("databaseCodegen") {
            id = "kmp-activitypub.database-codegen"
            implementationClass = "net.matsudamper.kmp.activitypub.gradle.DatabaseCodegenPlugin"
        }

        create("graphQlSchemaList") {
            id = "kmp-activitypub.graphql-schema-list"
            implementationClass = "net.matsudamper.kmp.activitypub.gradle.GraphQlSchemaListPlugin"
        }

        create("webpackBundleHash") {
            id = "kmp-activitypub.webpack-bundle-hash"
            implementationClass = "net.matsudamper.kmp.activitypub.gradle.WebpackBundleHashPlugin"
        }

        create("nativeImage") {
            id = "kmp-activitypub.native-image"
            implementationClass = "net.matsudamper.kmp.activitypub.gradle.NativeImagePlugin"
        }

        create("graphqlSchemaList") {
            id = "kmp-activitypub.graphql-schema-list"
            implementationClass = "net.matsudamper.kmp.activitypub.gradle.GraphqlSchemaListPlugin"
        }
    }
}
