package net.matsudamper.kmp.activitypub.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

interface DatabaseCodegenExtension {
    val schemaFile: RegularFileProperty

    val packageName: Property<String>

    /**
     * 既定では SQLite の内部表と、旧マイグレーションの `schema_version` を外す
     */
    val excludes: Property<String>

    /**
     * 生成する `reflect-config.json` の出力先。
     * `META-INF/native-image/<ここ>/reflect-config.json` の `<ここ>` 部分（モジュールごとに一意）
     */
    val nativeImageReflectConfigOutputPath: Property<String>
}
