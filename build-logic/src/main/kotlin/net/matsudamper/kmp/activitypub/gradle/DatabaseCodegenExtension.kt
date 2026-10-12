package net.matsudamper.kmp.activitypub.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

interface DatabaseCodegenExtension {
    val schemaFile: RegularFileProperty

    val packageName: Property<String>

    /**
     * 生成の対象から外すテーブルの正規表現。
     *
     * 既定では SQLite 自身の内部表と、旧マイグレーション機構の管理表
     * `schema_version` を外す。後者は、切り替え前の開発用 DB に残っていても
     * dump と生成物に紛れ込まないようにするため。
     */
    val excludes: Property<String>

    val nativeImageMetadataPath: Property<String>
}
