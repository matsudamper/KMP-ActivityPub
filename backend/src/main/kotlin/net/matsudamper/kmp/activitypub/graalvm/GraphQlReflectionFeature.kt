package net.matsudamper.kmp.activitypub.graalvm

import org.graalvm.nativeimage.hosted.Feature
import org.graalvm.nativeimage.hosted.RuntimeReflection

/**
 * スキーマを触るたびに reflect-config.json を直さずに済むよう、ビルド時にクラスパスを走査する
 */
class GraphQlReflectionFeature : Feature {
    override fun beforeAnalysis(access: Feature.BeforeAnalysisAccess) {
        registerKotlinReflection()
        registerGraphQlReflection()
    }

    private fun registerGraphQlReflection() {
        GraphQlReflectionTargets.PACKAGES.forEach { packageName ->
            val classNames = GraphQlReflectionTargets.classNamesIn(packageName)

            // まとめて数えると片方の登録漏れに気付けない
            check(classNames.isNotEmpty()) {
                "$packageName にクラスが 1 つも無い。パッケージを移したか、走査に失敗している"
            }

            classNames.forEach { register(it) }
        }
    }

    /**
     * kickstart が kotlin-reflect を使い、実装を名前で引く
     */
    private fun registerKotlinReflection() {
        val factory = Class.forName(KOTLIN_REFLECTION_FACTORY, false, javaClass.classLoader)

        RuntimeReflection.register(factory)
        RuntimeReflection.register(*factory.declaredConstructors)
    }

    private fun register(className: String) {
        val clazz = Class.forName(className, false, GraphQlReflectionFeature::class.java.classLoader)

        RuntimeReflection.register(clazz)
        RuntimeReflection.register(*clazz.declaredConstructors)
        RuntimeReflection.register(*clazz.declaredMethods)
        RuntimeReflection.register(*clazz.declaredFields)
    }

    private companion object {
        const val KOTLIN_REFLECTION_FACTORY = "kotlin.reflect.jvm.internal.ReflectionFactoryImpl"
    }
}
