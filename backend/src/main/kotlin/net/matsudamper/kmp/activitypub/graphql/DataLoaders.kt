package net.matsudamper.kmp.activitypub.graphql

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import graphql.schema.DataFetchingEnvironment
import io.opentelemetry.context.Context
import net.matsudamper.kmp.activitypub.graphql.dataloader.DataLoaderDefine
import net.matsudamper.kmp.activitypub.graphql.dataloader.FollowingDataLoaderDefine
import net.matsudamper.kmp.activitypub.graphql.dataloader.PinnedNoteDataLoaderDefine
import org.dataloader.DataLoader
import org.dataloader.DataLoaderRegistry

/**
 * 1 回の実行の中で使う DataLoader。実行ごとに作る
 */
internal class DataLoaders(
    private val diContainer: DiContainer,
    private val dataLoaderRegistryBuilder: DataLoaderRegistry.Builder,
    private val otelContext: Context,
) {
    val followingDataLoader by register {
        FollowingDataLoaderDefine(diContainer.followingGroupService)
    }

    val pinnedNoteDataLoader by register {
        PinnedNoteDataLoaderDefine(diContainer.notePinService)
    }

    private fun <K : Any, V : Any> register(initializer: () -> DataLoaderDefine<K, V>): DataLoaderRegister<K, V> {
        val provider = initializer()
        val dataLoader = provider.getDataLoader()
        val scheduler = OtelBatchLoaderScheduler(otelContext, "DataLoader.${provider.key}")
        val otelOptions = dataLoader.options.transform {
            it.setBatchLoaderScheduler(scheduler)
                // subscription は接続のあいだ同じ DataLoader を使い続けるので、覚えておくと後から届いた投稿に古いフォローや固定が載る
                .setCachingEnabled(false)
        }
        dataLoaderRegistryBuilder.register(provider.key, dataLoader.transform { it.options(otelOptions) })
        return DataLoaderRegister(provider.key)
    }

    class DataLoaderProvider<K : Any, V : Any>(
        private val dataLoaderName: String,
    ) {
        fun get(env: DataFetchingEnvironment): DataLoader<K, V> {
            return checkNotNull(env.dataLoaderRegistry.getDataLoader(dataLoaderName)) {
                "$dataLoaderName is null"
            }
        }
    }

    private class DataLoaderRegister<K : Any, V : Any>(
        private val key: String,
    ) : ReadOnlyProperty<Any, DataLoaderProvider<K, V>> {
        override fun getValue(
            thisRef: Any,
            property: KProperty<*>,
        ): DataLoaderProvider<K, V> {
            return DataLoaderProvider(key)
        }
    }
}
