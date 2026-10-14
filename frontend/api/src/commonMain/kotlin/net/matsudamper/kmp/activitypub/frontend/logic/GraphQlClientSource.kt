package net.matsudamper.kmp.activitypub.frontend.logic

import kotlinx.coroutines.flow.StateFlow
import com.apollographql.apollo.ApolloClient

/**
 * 今の接続先から作った ApolloClient。未設定の間は null
 */
interface GraphQlClientSource {
    val client: StateFlow<ApolloClient?>
}
