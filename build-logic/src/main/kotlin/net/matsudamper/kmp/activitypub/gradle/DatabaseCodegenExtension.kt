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
}
