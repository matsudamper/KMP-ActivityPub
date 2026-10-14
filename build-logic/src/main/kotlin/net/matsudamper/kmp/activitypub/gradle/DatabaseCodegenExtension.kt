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
     * 生成する `reflect-config.json` を置く `META-INF/native-image/<ここ>/` のパス。
     * GraalVM は group/artifact ごとにメタデータを読むので、モジュール間で衝突しない名前にする
     */
    val nativeImageMetadataPath: Property<String>
}
