package net.matsudamper.kmp.activitypub.android

import android.content.Context
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.okHttpClient
import net.matsudamper.kmp.activitypub.frontend.logic.GraphQlClient
import net.matsudamper.kmp.activitypub.shared.GRAPHQL_PATH
import okhttp3.OkHttpClient

internal object AndroidGraphQlClient {
    /**
     * @param serverUrl `https://example.com` の形。末尾の `/` は付けない
     */
    fun create(
        context: Context,
        serverUrl: String,
    ): ApolloClient {
        val okHttpClient = OkHttpClient
            .Builder()
            .cookieJar(PersistentCookieJar(context.getSharedPreferences(COOKIE_PREFERENCES_NAME, Context.MODE_PRIVATE)))
            .build()

        return GraphQlClient
            .builder("$serverUrl$GRAPHQL_PATH")
            .okHttpClient(okHttpClient)
            .build()
    }

    private const val COOKIE_PREFERENCES_NAME = "cookies"
}
