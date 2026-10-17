package net.matsudamper.kmp.activitypub.frontend.logic

import kotlinx.coroutines.flow.StateFlow
import com.apollographql.apollo.ApolloClient

/**
 * 今の接続先から作った ApolloClient。未設定の間は null
 */
interface GraphQlClientSource {
    val client: StateFlow<ApolloClient?>

    /**
     * 接続先を保存先から読み終えるまで待ってから返す。読み終える前の [client] の null は未設定と区別できない
     */
    suspend fun awaitLoadedClient(): ApolloClient?
}
