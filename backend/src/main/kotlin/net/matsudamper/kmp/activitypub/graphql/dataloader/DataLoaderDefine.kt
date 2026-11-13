package net.matsudamper.kmp.activitypub.graphql.dataloader

import org.dataloader.DataLoader

interface DataLoaderDefine<Key : Any, R : Any> {
    val key: String

    fun getDataLoader(): DataLoader<Key, R>
}
