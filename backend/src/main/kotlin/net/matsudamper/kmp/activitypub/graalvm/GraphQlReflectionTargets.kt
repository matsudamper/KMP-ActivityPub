package net.matsudamper.kmp.activitypub.graalvm

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * パッケージ内のクラス名を、ディレクトリと jar から集める。
 * 登録は別クラスが行い、ここは GraalVM の API を使わない
 */
object GraphQlReflectionTargets {
    const val MODEL_PACKAGE = "net.matsudamper.kmp.activitypub.graphql.model"
    const val RESOLVER_PACKAGE = "net.matsudamper.kmp.activitypub.graphql.resolver"

    val PACKAGES: List<String> =
        listOf(
            MODEL_PACKAGE,
            RESOLVER_PACKAGE,
        )

    private const val CLASS_EXTENSION = ".class"

    fun classNamesIn(packageName: String): List<String> {
        val packagePath = packageName.replace('.', '/')
        val classLoader = classLoader()

        return classLoader
            .getResources(packagePath)
            .asSequence()
            .flatMap { resource ->
                val uri = resource.toURI()
                when (uri.scheme) {
                    "file" -> classNamesInDirectory(Paths.get(uri), packageName)

                    "jar" ->
                        FileSystems.newFileSystem(uri, emptyMap<String, Any>()).use { fileSystem ->
                            classNamesInDirectory(fileSystem.getPath(packagePath), packageName)
                        }

                    else -> emptySequence()
                }
            }.distinct()
            .sorted()
            .toList()
    }

    private fun classLoader(): ClassLoader =
        Thread.currentThread().contextClassLoader
            ?: GraphQlReflectionTargets::class.java.classLoader

    private fun classNamesInDirectory(
        directory: Path,
        packageName: String,
    ): Sequence<String> {
        if (!Files.isDirectory(directory)) return emptySequence()

        // jar の FileSystem を閉じる前に辿る
        return Files.walk(directory).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(CLASS_EXTENSION) }
                .map { path ->
                    val relative =
                        directory
                            .relativize(path)
                            .toString()
                            .removeSuffix(CLASS_EXTENSION)
                            .replace(File.separatorChar, '.')
                            .replace('/', '.')

                    "$packageName.$relative"
                }.toList()
                .asSequence()
        }
    }
}
