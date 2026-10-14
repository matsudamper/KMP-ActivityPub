package net.matsudamper.kmp.activitypub.graalvm

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphQlReflectionTargetsTest {
    @Test
    fun `走査するパッケージにクラスがある`() {
        GraphQlReflectionTargets.PACKAGES.forEach { packageName ->
            assertTrue(
                GraphQlReflectionTargets.classNamesIn(packageName).isNotEmpty(),
                "$packageName にクラスが無い",
            )
        }
    }

    @Test
    fun `依存の生成物も拾う`() {
        val classNames = GraphQlReflectionTargets.classNamesIn(GraphQlReflectionTargets.MODEL_PACKAGE)

        assertContains(classNames, "${GraphQlReflectionTargets.MODEL_PACKAGE}.QlUserLoginResult")
        assertContains(classNames, "${GraphQlReflectionTargets.MODEL_PACKAGE}.UserMutationResolver")
    }

    @Test
    fun `リゾルバの実装は走査するパッケージに置く`() {
        val scanned = GraphQlReflectionTargets.classNamesIn(GraphQlReflectionTargets.RESOLVER_PACKAGE)

        val implementations =
            GraphQlReflectionTargets
                .classNamesIn(ROOT_PACKAGE)
                .filter { it.substringAfterLast('.').endsWith(RESOLVER_IMPL_SUFFIX) }

        assertTrue(implementations.isNotEmpty(), "リゾルバの実装が 1 つも見つからない")

        assertEquals(
            emptyList(),
            implementations.filterNot { it in scanned },
            "リゾルバの実装が ${GraphQlReflectionTargets.RESOLVER_PACKAGE} の外にある。走査の対象から外れる",
        )
    }

    private companion object {
        const val ROOT_PACKAGE = "net.matsudamper.kmp.activitypub"
        const val RESOLVER_IMPL_SUFFIX = "ResolverImpl"
    }
}
