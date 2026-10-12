package net.matsudamper.kmp.activitypub.gradle

import org.graalvm.buildtools.gradle.NativeImagePlugin as GraalVmNativeImagePlugin
import org.graalvm.buildtools.gradle.dsl.GraalVMExtension
import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

class NativeImagePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply(GraalVmNativeImagePlugin::class.java)

        val javaToolchains = target.extensions.getByType<JavaToolchainService>()

        val toolchain = target.extensions.getByType<JavaPluginExtension>().toolchain

        val graalVmLauncher =
            javaToolchains.launcherFor {
                languageVersion.set(toolchain.languageVersion)
                vendor.set(JvmVendorSpec.GRAAL_VM)
            }

        val repairLauncher =
            target.tasks.register<RepairNativeImageLauncherTask>("repairNativeImageLauncher") {
                description = "Gradle が用意した GraalVM の native-image を使える状態に直す"
                javaLauncher.set(graalVmLauncher)
                temporaryNameSuffix.set(target.path.trim(':').replace(':', '-'))
            }

        target.extensions.configure<GraalVMExtension> {
            // 切ってはいけない。切ると native-image を探すときに
            // 下で渡す launcher が読まれなくなり、JAVA_HOME を見に行って
            // 「native-image が無い」と言って落ちる
            toolchainDetection.set(true)

            // configureEach ではなく all にする。nativeTest の実行タスクは
            // container 経由でなく直接この設定オブジェクトを掴むので、
            // 遅延させると launcher を渡す前に読まれる
            binaries.all {
                javaLauncher.set(graalVmLauncher)
            }
        }

        target.tasks.withType<BuildNativeImageTask>().configureEach {
            dependsOn(repairLauncher)
        }
    }
}
