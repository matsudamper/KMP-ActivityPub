package net.matsudamper.kmp.activitypub.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.jooq.codegen.gradle.CodegenPlugin
import org.jooq.codegen.gradle.CodegenPluginExtension
import org.jooq.codegen.gradle.CodegenTask

class DatabaseCodegenPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = target.extensions.create<DatabaseCodegenExtension>(EXTENSION_NAME)
        extension.schemaFile.convention(
            target.layout.projectDirectory.file("src/main/resources/db/schema.sql"),
        )
        extension.excludes.convention("schema_version|sqlite_.*")
        // reflect-config の META-INF/native-image/<path>/ の既定。未設定なら group とモジュール名から作る
        extension.nativeImageReflectConfigOutputPath.convention(
            target.provider { "${target.group}/${target.name}-jooq" },
        )

        target.pluginManager.apply(CodegenPlugin::class.java)

        val codegenConfiguration = target.configurations.getByName(CODEGEN_CONFIGURATION)

        target.pluginManager.withPlugin("java") {
            val schemaDatabaseFile = target.layout.buildDirectory.file("jooq/schema.db")
            val buildJooqSchema =
                target.tasks.register<BuildSchemaDatabaseTask>("buildJooqSchema") {
                    description = "codegen の入力にする一時 SQLite を作る"
                    schemaSql.set(extension.schemaFile)
                    driverClasspath.from(codegenConfiguration)
                    databaseFile.set(schemaDatabaseFile)
                }

            val generatedDirectory = target.layout.buildDirectory.dir("generated/jooq")

            // jooq {} は即時評価されるので、使う側の packageName などが入るまで待つ
            target.afterEvaluate {
                val jooq = target.extensions.getByType<CodegenPluginExtension>()
                jooq.configuration {
                    jdbc {
                        driver = "org.sqlite.JDBC"
                        url = "jdbc:sqlite:${schemaDatabaseFile.get().asFile.absolutePath}"
                    }
                    generator {
                        name = "org.jooq.codegen.KotlinGenerator"
                        database {
                            name = "org.jooq.meta.sqlite.SQLiteDatabase"
                            includes = ".*"
                            excludes = extension.excludes.get()
                            forcedTypes {
                                forcedType {
                                    // SQLite の INTEGER は 8 バイト。jOOQ は 4 バイトとして読む
                                    name = "BIGINT"
                                    includeTypes = "INTEGER"
                                }
                            }
                        }
                        generate {
                            javaTimeTypes = true
                            // native-image でリフレクションが要るので作らない
                            pojos = false
                            daos = false
                            deprecated = false
                        }
                        target {
                            packageName = extension.packageName.get()
                            directory = generatedDirectory.get().asFile.absolutePath
                            clean = true
                        }
                    }
                }
            }

            val generateJooq =
                target.tasks.named<CodegenTask>("jooqCodegen") {
                    dependsOn(buildJooqSchema)
                    // JDBC URL だけでは schema.sql の変更を検知できない
                    inputs
                        .file(schemaDatabaseFile)
                        .withPropertyName("schemaDatabase")
                        .withPathSensitivity(PathSensitivity.NONE)
                }

            // afterEvaluate で設定しているので、プラグインの自動配線は使えない
            target.extensions.getByType<SourceSetContainer>().named("main") {
                java.srcDir(generateJooq.map { it.outputDirectory })
            }
            target.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
                target.tasks.named("compileKotlin") {
                    dependsOn(generateJooq)
                }
            }

            val generateJooqReflectConfig =
                target.tasks.register<GenerateJooqReflectConfigTask>("generateJooqReflectConfig") {
                    description = "生成コードの native-image 向けリフレクション設定を作る"
                    dependsOn(generateJooq)
                    generatedSources.set(generatedDirectory)
                    nativeImageReflectConfigOutputPath.set(extension.nativeImageReflectConfigOutputPath)
                    outputDirectory.set(target.layout.buildDirectory.dir("generated/jooqNativeImage"))
                }

            target.extensions.getByType<SourceSetContainer>().named("main") {
                resources.srcDir(generateJooqReflectConfig.flatMap { it.outputDirectory })
            }
        }
    }

    private companion object {
        const val EXTENSION_NAME = "databaseCodegen"
        const val CODEGEN_CONFIGURATION = "jooqCodegen"
    }
}
