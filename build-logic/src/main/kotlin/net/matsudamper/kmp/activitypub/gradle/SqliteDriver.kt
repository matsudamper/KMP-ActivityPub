package net.matsudamper.kmp.activitypub.gradle

import java.io.File
import java.net.URLClassLoader
import java.sql.Connection
import java.sql.Driver
import java.util.Properties

/**
 * ドライバをビルド自身の classpath に載せないため、タスク専用の classpath から読み込む
 */
internal fun <T> withSqliteConnection(
    driverClasspath: Iterable<File>,
    databaseFile: File,
    block: (Connection) -> T,
): T {
    val loader =
        URLClassLoader(
            driverClasspath.map { it.toURI().toURL() }.toTypedArray(),
            ClassLoader.getPlatformClassLoader(),
        )

    return loader.use {
        val driver =
            loader
                .loadClass("org.sqlite.JDBC")
                .getDeclaredConstructor()
                .newInstance() as Driver

        driver.connect("jdbc:sqlite:$databaseFile", Properties()).use(block)
    }
}
